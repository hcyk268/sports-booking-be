# End-to-end API smoke test: booking + payment (+ optional SePay IPN simulation).
# Prerequisite: backend on :8080 with SEPAY_ENABLED; run -SeedFirst or seed-uat-data.ps1 first.
param(
    [string]$ApiBase = $(if ($env:UAT_SEED_API_BASE) { $env:UAT_SEED_API_BASE } else { 'http://localhost:8080/api/v1' }),
    [string]$CustomerEmail = $(if ($env:UAT_SEED_CUSTOMER_EMAIL) { $env:UAT_SEED_CUSTOMER_EMAIL } else { 'paysafe.jiwhva2@gmail.com' }),
    [string]$ProviderEmail = $(if ($env:UAT_SEED_PROVIDER_EMAIL) { $env:UAT_SEED_PROVIDER_EMAIL } else { 'paysafe.ikegdn1@gmail.com' }),
    [string]$Password = $(if ($env:UAT_SEED_PASSWORD) { $env:UAT_SEED_PASSWORD } else { 'matkhauMANH123' }),
    [string]$ManifestPath = (Join-Path $PSScriptRoot 'uat-seed-manifest.json'),
    [switch]$SeedFirst,
    [string]$BookingDate = '',
    [switch]$SimulateIpn,
    [string]$SePaySecret = $(if ($env:SEPAY_SECRET_KEY) { $env:SEPAY_SECRET_KEY } else { '' }),
    [int]$IpnWaitSeconds = 4
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [ValidateSet('Get', 'Post', 'Put', 'Delete', 'Patch')][string]$Method,
        [string]$Path,
        $Body = $null,
        [hashtable]$Headers = @{},
        [string]$IdempotencyKey = $null,
        [switch]$AllowError
    )
    $uri = if ($Path.StartsWith('http')) { $Path } else { ($ApiBase.TrimEnd('/')) + $Path }
    $headers = $Headers.Clone()
    if ($IdempotencyKey) { $headers['Idempotency-Key'] = $IdempotencyKey }
    $jsonBody = if ($null -ne $Body) { $Body | ConvertTo-Json -Depth 16 -Compress } else { $null }
    $attempt = 0
    while ($true) {
        $attempt++
        try {
            $webParams = @{
                Method          = $Method
                Uri             = $uri
                Headers         = $headers
                UseBasicParsing = $true
            }
            if ($jsonBody) {
                $webParams.ContentType = 'application/json; charset=utf-8'
                $webParams.Body = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
            }
            $response = Invoke-WebRequest @webParams
            $parsed = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
            return $parsed
        }
        catch {
            $response = $_.Exception.Response
            $status = if ($response) { [int]$response.StatusCode } else { 0 }
            if ($status -eq 429 -and $attempt -lt 6) {
                Start-Sleep -Seconds ([Math]::Min(60, 5 * $attempt))
                continue
            }
            $detail = $_.ErrorDetails.Message
            if (-not $detail -and $response) {
                try {
                    $reader = [System.IO.StreamReader]::new($response.GetResponseStream())
                    $detail = $reader.ReadToEnd()
                }
                catch { $detail = $_.Exception.Message }
            }
            if (-not $detail) { $detail = $_.Exception.Message }
            if ($AllowError) {
                return @{ _error = $true; status = $status; detail = $detail }
            }
            throw "API $Method $Path failed ($status): $detail"
        }
    }
}

function Connect-Account {
    param([string]$Email, [string[]]$ExpectedRoles)
    $login = Invoke-Api -Method Post -Path '/auth/login' -Body @{ email = $Email; password = $Password }
    $roles = @($login.data.user.roles)
    foreach ($role in $ExpectedRoles) {
        if ($roles -notcontains $role) {
            throw "Account $Email missing role $role"
        }
    }
    return @{
        Auth  = @{ Authorization = "Bearer $($login.data.accessToken)" }
        Email = $Email
    }
}

function Test-Step {
    param([string]$Name, [scriptblock]$Action)
    Write-Host ">> $Name" -ForegroundColor Cyan
    try {
        $result = & $Action
        Write-Host "   OK" -ForegroundColor Green
        return @{ name = $Name; ok = $true; result = $result }
    }
    catch {
        Write-Host "   FAIL: $($_.Exception.Message)" -ForegroundColor Red
        return @{ name = $Name; ok = $false; error = $_.Exception.Message }
    }
}

function Get-EnvSePaySecret {
    if ($SePaySecret) { return $SePaySecret }
    $envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
    if (-not (Test-Path $envFile)) { return '' }
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*SEPAY_SECRET_KEY=(.+)$') {
            return $Matches[1].Trim().Trim('"')
        }
    }
    return ''
}

function Send-SimulatedIpn {
    param(
        [string]$InvoiceNumber,
        [string]$AmountPlain,
        [string]$CustomerId,
        [string]$Secret
    )
    if (-not $Secret) { throw 'SEPAY_SECRET_KEY required for -SimulateIpn (env or -SePaySecret)' }
    $txnId = [guid]::NewGuid().ToString()
    $nowLocal = [TimeZoneInfo]::ConvertTimeFromUtc((Get-Date).ToUniversalTime(),
        [TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time'))
    $txnDate = $nowLocal.ToString('yyyy-MM-dd HH:mm:ss')
    $payload = @{
        timestamp         = [int][double]::Parse((Get-Date -UFormat %s))
        notification_type = 'ORDER_PAID'
        order             = @{
            id                   = "order-$txnId"
            order_id             = "gw-$txnId"
            order_status         = 'CAPTURED'
            order_currency       = 'VND'
            order_amount         = "$AmountPlain.00"
            order_invoice_number = $InvoiceNumber
        }
        transaction       = @{
            id                   = "txn-$txnId"
            transaction_id       = "bank-$txnId"
            payment_method       = 'BANK_TRANSFER'
            transaction_type     = 'PAYMENT'
            transaction_status   = 'APPROVED'
            transaction_amount   = $AmountPlain
            transaction_currency = 'VND'
            transaction_date     = $txnDate
        }
        customer          = @{
            id          = $CustomerId
            customer_id = $CustomerId
        }
    } | ConvertTo-Json -Depth 6 -Compress
    $uri = ($ApiBase.TrimEnd('/')) + '/payments/sepay/ipn'
    Invoke-RestMethod -Method Post -Uri $uri -Headers @{ 'X-Secret-Key' = $Secret } `
        -ContentType 'application/json' -Body $payload | Out-Null
}

if ($SeedFirst) {
    Write-Host "Running seed-uat-data.ps1 ..." -ForegroundColor Yellow
    $seedArgs = @{
        ApiBase        = $ApiBase
        ProviderEmail  = $ProviderEmail
        CustomerEmail  = $CustomerEmail
        Password       = $Password
        ManifestPath   = $ManifestPath
    }
    if ($BookingDate) { $seedArgs.BookingDate = $BookingDate }
    & (Join-Path $PSScriptRoot 'seed-uat-data.ps1') @seedArgs
}

if (-not (Test-Path -LiteralPath $ManifestPath)) {
    throw "Manifest not found: $ManifestPath. Run .\scripts\seed-uat-data.ps1 or use -SeedFirst."
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
$primary = @($manifest.bookings | Where-Object { $_.key -eq 'football-q9' } | Select-Object -First 1)
if (-not $primary) {
    $primary = @($manifest.bookings | Select-Object -First 1)
}
if (-not $primary) { throw 'Manifest has no bookings.' }

$bookingId = [guid]$primary.booking.id
$courtId = [guid]$primary.courtId
$bookingDate = $manifest.bookingDate
$paymentId = $null
$invoiceNumber = $null
if ($primary.payment) {
    $paymentId = [guid]$primary.payment.paymentId
}

Write-Host ""
Write-Host "Booking/payment flow test -> $ApiBase"
Write-Host "Manifest: $ManifestPath | football-q9 booking: $bookingId"
Write-Host ""

$customer = Connect-Account -Email $CustomerEmail -ExpectedRoles @('CUSTOMER')
$provider = Connect-Account -Email $ProviderEmail -ExpectedRoles @('PROVIDER')

$results = @()

$results += Test-Step "GET /courts/search?date=$bookingDate" {
    $r = Invoke-Api -Method Get -Path "/courts/search?date=$bookingDate&page=0&size=5"
    if ($r.data.pagination.totalElements -lt 1) { throw 'No ACTIVE courts in search' }
    $r.data.pagination.totalElements
}

$results += Test-Step "GET /courts/{id}/schedule" {
    Invoke-Api -Method Get -Path "/courts/$courtId/schedule?date=$bookingDate" | Out-Null
}

$results += Test-Step 'GET /bookings/me' {
    $r = Invoke-Api -Method Get -Path '/bookings/me?page=0&size=20' -Headers $customer.Auth -AllowError
    if ($r._error) {
        if ($r.status -eq 500) {
            throw 'HTTP 500 — restart backend after BookingRepository sort fix (see git diff on booking module)'
        }
        throw $r.detail
    }
    $r.data.items.Count
}

$results += Test-Step 'GET /bookings/{id} (customer)' {
    $b = (Invoke-Api -Method Get -Path "/bookings/$bookingId" -Headers $customer.Auth).data
    if ($b.id -ne $bookingId.ToString()) { throw 'Wrong booking id' }
    $b.status
}

$results += Test-Step 'GET /bookings (provider)' {
    $r = Invoke-Api -Method Get -Path '/bookings?page=0&size=10' -Headers $provider.Auth -AllowError
    if ($r._error) {
        if ($r.status -eq 500) { throw 'HTTP 500 — restart backend after booking list fix' }
        throw $r.detail
    }
    $r.data.pagination.totalElements
}

if (-not $paymentId) {
    $results += Test-Step 'POST /bookings/{id}/payments' {
        $key = "uat-flow-pay-$bookingId"
        $checkout = (Invoke-Api -Method Post -Path "/bookings/$bookingId/payments" `
                -Body @{ paymentMethod = 'BANK_TRANSFER' } -Headers $customer.Auth -IdempotencyKey $key).data
        $script:paymentId = [guid]$checkout.paymentId
        $script:invoiceNumber = ($checkout.checkout.fields | Where-Object { $_.name -eq 'order_invoice_number' }).value
        $checkout.paymentId
    }
}
else {
    Write-Host ">> POST /bookings/{id}/payments (skip — manifest has payment $paymentId)" -ForegroundColor DarkGray
}

if (-not $paymentId) { throw 'No payment id for remaining steps' }

$results += Test-Step 'GET /bookings/{id}/payments' {
    (Invoke-Api -Method Get -Path "/bookings/$bookingId/payments" -Headers $customer.Auth).data.Count
}

$results += Test-Step 'GET /payments + GET /payments/{id}' {
    (Invoke-Api -Method Get -Path '/payments?page=0&size=5' -Headers $customer.Auth).data.items.Count | Out-Null
    $p = (Invoke-Api -Method Get -Path "/payments/$paymentId" -Headers $customer.Auth).data
    if (-not $invoiceNumber) {
        $script:invoiceNumber = ($p.attempts | Select-Object -First 1).invoiceNumber
    }
    $p.status
}

$results += Test-Step 'POST /payments/{id}/checkout' {
    (Invoke-Api -Method Post -Path "/payments/$paymentId/checkout" -Headers $customer.Auth).data.paymentId
}

if ($SimulateIpn) {
    $results += Test-Step 'POST /payments/sepay/ipn (simulated)' {
        $pay = (Invoke-Api -Method Get -Path "/payments/$paymentId" -Headers $customer.Auth).data
        if ($pay.status -eq 'SUCCEEDED') {
            return 'already SUCCEEDED'
        }
        if ($pay.status -notin @('PENDING', 'PROCESSING')) {
            throw "Payment status $($pay.status) — cannot simulate IPN"
        }
        $amount = [int][decimal]$pay.amount
        $custId = (Invoke-Api -Method Get -Path '/users/me' -Headers $customer.Auth).data.id
        Send-SimulatedIpn -InvoiceNumber $invoiceNumber -AmountPlain "$amount" -CustomerId $custId `
            -Secret (Get-EnvSePaySecret)
        Start-Sleep -Seconds $IpnWaitSeconds
        $after = (Invoke-Api -Method Get -Path "/payments/$paymentId" -Headers $customer.Auth).data
        if ($after.status -ne 'SUCCEEDED') { throw "Expected SUCCEEDED, got $($after.status)" }
        $booking = (Invoke-Api -Method Get -Path "/bookings/$bookingId" -Headers $customer.Auth).data
        if ($booking.status -ne 'CONFIRMED') { throw "Expected CONFIRMED booking, got $($booking.status)" }
        'SUCCEEDED + CONFIRMED'
    }
}
else {
    Write-Host ">> IPN: skipped (use -SimulateIpn or pay via SePay sandbox checkout in manifest)" -ForegroundColor DarkYellow
}

Write-Host ""
$failed = @($results | Where-Object { -not $_.ok })
$passed = @($results | Where-Object { $_.ok })
Write-Host "Passed: $($passed.Count) / $($results.Count)"
if ($failed.Count -gt 0) {
    Write-Host "Failed steps:" -ForegroundColor Red
    $failed | ForEach-Object { Write-Host "  - $($_.name): $($_.error)" }
    exit 1
}

Write-Host ""
Write-Host "Manual (optional): open SePay sandbox with checkout fields from seed manifest payment entry."
Write-Host "PaymentId: $paymentId | BookingId: $bookingId"
exit 0
