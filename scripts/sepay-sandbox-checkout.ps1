# Tạo booking + payment mới và mở form POST SePay sandbox trong trình duyệt.
param(
    [string]$ApiBase = $(if ($env:UAT_SEED_API_BASE) { $env:UAT_SEED_API_BASE } else { 'http://localhost:8080/api/v1' }),
    [string]$CustomerEmail = $(if ($env:UAT_SEED_CUSTOMER_EMAIL) { $env:UAT_SEED_CUSTOMER_EMAIL } else { 'paysafe.jiwhva2@gmail.com' }),
    [string]$ProviderEmail = $(if ($env:UAT_SEED_PROVIDER_EMAIL) { $env:UAT_SEED_PROVIDER_EMAIL } else { 'paysafe.ikegdn1@gmail.com' }),
    [string]$Password = $(if ($env:UAT_SEED_PASSWORD) { $env:UAT_SEED_PASSWORD } else { 'matkhauMANH123' }),
    [string]$BookingDate = '',
    [string]$CourtKey = 'football-q9',
    [string]$HtmlPath = (Join-Path $PSScriptRoot 'sepay-checkout.html'),
    [switch]$PublishDraftCourts,
    [switch]$SeedFirst,
    [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'

function Get-BookingDate {
    if ($BookingDate) {
        return [datetime]::ParseExact($BookingDate, 'yyyy-MM-dd', $null)
    }
    $tz = [TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')
    $localNow = [TimeZoneInfo]::ConvertTimeFromUtc((Get-Date).ToUniversalTime(), $tz)
    return $localNow.Date.AddDays(1)
}

function Invoke-Api {
    param(
        [ValidateSet('Get', 'Post', 'Put', 'Delete', 'Patch')][string]$Method,
        [string]$Path,
        $Body = $null,
        [hashtable]$Headers = @{},
        [string]$IdempotencyKey = $null
    )
    $uri = ($ApiBase.TrimEnd('/')) + $Path
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
            if ($response.Content) { return $response.Content | ConvertFrom-Json }
            return $null
        }
        catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            if ($status -eq 429 -and $attempt -lt 6) {
                Start-Sleep -Seconds ([Math]::Min(60, 5 * $attempt))
                continue
            }
            $detail = $_.ErrorDetails.Message
            if (-not $detail -and $_.Exception.Response) {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $detail = $reader.ReadToEnd()
            }
            throw "API $Method $Path failed ($status): $detail"
        }
    }
}

function Format-LocalTime([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $Value }
    $normalized = ($Value -replace '\.\d+$', '').Trim()
    if ($normalized -match '^(\d{1,2}):(\d{2})$') {
        return '{0:D2}:{1:D2}:00' -f [int]$Matches[1], [int]$Matches[2]
    }
    if ($normalized -match '^(\d{1,2}):(\d{2}):(\d{2})$') {
        return '{0:D2}:{1:D2}:{2:D2}' -f [int]$Matches[1], [int]$Matches[2], [int]$Matches[3]
    }
    return $normalized
}

function New-CheckoutHtml {
    param($Checkout, [string]$Title)
    $fieldsHtml = ($Checkout.fields | ForEach-Object {
            $n = [System.Net.WebUtility]::HtmlEncode($_.name)
            $v = [System.Net.WebUtility]::HtmlEncode($_.value)
            "    <input type=`"hidden`" name=`"$n`" value=`"$v`" />"
        }) -join "`n"
    $action = [System.Net.WebUtility]::HtmlEncode($Checkout.action)
    @"
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="utf-8" />
  <title>$Title</title>
</head>
<body onload="document.forms[0].submit()">
  <p>Đang chuyển tới SePay sandbox…</p>
  <form method="$([System.Net.WebUtility]::HtmlEncode($Checkout.method))" action="$action">
$fieldsHtml
    <noscript><button type="submit">Thanh toán SePay</button></noscript>
  </form>
</body>
</html>
"@
}

if ($PublishDraftCourts) {
    & (Join-Path $PSScriptRoot 'publish-draft-courts.ps1') -ApiBase $ApiBase -ProviderEmail $ProviderEmail -Password $Password
}

if ($SeedFirst) {
    $target = Get-BookingDate
    & (Join-Path $PSScriptRoot 'seed-uat-data.ps1') -ApiBase $ApiBase -ProviderEmail $ProviderEmail `
        -CustomerEmail $CustomerEmail -Password $Password -BookingDate $target.ToString('yyyy-MM-dd')
}

$targetDate = Get-BookingDate
$dateStr = $targetDate.ToString('yyyy-MM-dd')

$customerLogin = Invoke-Api -Method Post -Path '/auth/login' -Body @{ email = $CustomerEmail; password = $Password }
$cAuth = @{ Authorization = "Bearer $($customerLogin.data.accessToken)" }

$courtId = $null
$manifestPath = Join-Path $PSScriptRoot 'uat-seed-manifest.json'
if (Test-Path -LiteralPath $manifestPath) {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $entry = @($manifest.courts | Where-Object { $_.key -eq $CourtKey } | Select-Object -First 1)
    if ($entry -and $entry.status -eq 'ACTIVE') {
        $courtId = [guid]$entry.id
        Write-Host "Court (manifest): $($entry.name) — ACTIVE"
    }
}

if (-not $courtId) {
    $search = Invoke-Api -Method Get -Path "/courts/search?date=$dateStr&page=0&size=20"
    if ($search.data.pagination.totalElements -lt 1) {
        throw @"
Không có sân ACTIVE trên search. Customer không book được sân DRAFT.
Chạy: .\scripts\seed-uat-data.ps1
  hoặc: .\scripts\publish-draft-courts.ps1 (sân cũ cần thêm giá/giờ)
"@
    }
    $courtId = [guid]$search.data.items[0].id
    Write-Host "Court (search): $($search.data.items[0].name)"
}

$schedule = Invoke-Api -Method Get -Path "/courts/$courtId/schedule?date=$dateStr"
$candidates = @($schedule.data.slots | Where-Object { -not $_.closed -and $_.priceConfigured -and $null -ne $_.totalPrice })
if (-not $candidates.Count) { throw "Không có slot có giá ngày $dateStr" }

$slot = $null
for ($i = 0; $i -lt $candidates.Count; $i++) {
    $trySlot = @{
        date      = $dateStr
        startTime = Format-LocalTime ([string]$candidates[$i].startTime)
        endTime   = Format-LocalTime ([string]$candidates[$i].endTime)
    }
    $key = "sepay-live-$courtId-$dateStr-$($trySlot.startTime)"
    try {
        $booking = (Invoke-Api -Method Post -Path '/bookings' -Body @{
                courtId = $courtId.ToString()
                slots   = @($trySlot)
            } -Headers $cAuth -IdempotencyKey $key).data
        $slot = $trySlot
        break
    }
    catch {
        if ($_.Exception.Message -match '\(409\)|SLOT_UNAVAILABLE|NOT_BOOKABLE') { continue }
        throw
    }
}
if (-not $slot) { throw 'Không giữ được slot trống — đổi -BookingDate' }

Write-Host "Booking HELD: $($booking.id) | $($slot.startTime)-$($slot.endTime) | $($booking.totalAmount) VND"

$payKey = "sepay-live-pay-$($booking.id)"
$checkout = (Invoke-Api -Method Post -Path "/bookings/$($booking.id)/payments" `
        -Body @{ paymentMethod = 'BANK_TRANSFER' } -Headers $cAuth -IdempotencyKey $payKey).data

Write-Host "Payment PENDING: $($checkout.paymentId)"
Write-Host "SePay: $($checkout.checkout.action)"

$html = New-CheckoutHtml -Checkout $checkout.checkout -Title "SePay sandbox — $($booking.bookingCode)"
Set-Content -LiteralPath $HtmlPath -Value $html -Encoding utf8
Write-Host "Saved: $HtmlPath"

Write-Host ""
Write-Host "=== Thanh toán thật SePay sandbox ==="
Write-Host "1. Mở file HTML (form POST) — thanh toán trên pay-sandbox.sepay.vn"
Write-Host "2. success/error URL chỉ là FE — trạng thái cuối cần IPN (tunnel ngrok) hoặc đối soát thủ công"
Write-Host "3. Booking hold ~10 phút — thanh toán trước khi hết hạn"
Write-Host "   paymentId: $($checkout.paymentId) | bookingId: $($booking.id)"

if ($OpenBrowser) {
    Start-Process -FilePath $HtmlPath
}
