# Publish all DRAFT courts owned by the UAT provider (POST /courts/{id}/publish).
# DRAFT sân vẫn thấy trên portal provider; customer search/booking chỉ dùng ACTIVE.
param(
    [string]$ApiBase = $(if ($env:UAT_SEED_API_BASE) { $env:UAT_SEED_API_BASE } else { 'http://localhost:8080/api/v1' }),
    [string]$ProviderEmail = $(if ($env:UAT_SEED_PROVIDER_EMAIL) { $env:UAT_SEED_PROVIDER_EMAIL } else { 'paysafe.ikegdn1@gmail.com' }),
    [string]$Password = $(if ($env:UAT_SEED_PASSWORD) { $env:UAT_SEED_PASSWORD } else { 'matkhauMANH123' })
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [ValidateSet('Get', 'Post', 'Put', 'Delete', 'Patch')][string]$Method,
        [string]$Path,
        $Body = $null,
        [hashtable]$Headers = @{}
    )
    $uri = ($ApiBase.TrimEnd('/')) + $Path
    $headers = $Headers.Clone()
    $jsonBody = if ($null -ne $Body) { $Body | ConvertTo-Json -Depth 8 -Compress } else { $null }
    $webParams = @{ Method = $Method; Uri = $uri; Headers = $headers; UseBasicParsing = $true }
    if ($jsonBody) {
        $webParams.ContentType = 'application/json; charset=utf-8'
        $webParams.Body = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
    }
    try {
        $response = Invoke-WebRequest @webParams
        if ($response.Content) { return $response.Content | ConvertFrom-Json }
        return $null
    }
    catch {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        throw "API $Method $Path failed: $($reader.ReadToEnd())"
    }
}

$login = Invoke-Api -Method Post -Path '/auth/login' -Body @{ email = $ProviderEmail; password = $Password }
$auth = @{ Authorization = "Bearer $($login.data.accessToken)" }

$page = 0
$published = 0
$skipped = 0
do {
    $resp = Invoke-Api -Method Get -Path "/provider/courts?page=$page&size=50" -Headers $auth
    foreach ($court in @($resp.data.items)) {
        if ($court.status -ne 'DRAFT') {
            $skipped++
            continue
        }
        try {
            $out = Invoke-Api -Method Post -Path "/courts/$($court.id)/publish" -Headers $auth
            Write-Host "ACTIVE: $($court.name) ($($court.id))"
            $published++
        }
        catch {
            Write-Warning "Skip $($court.id): $($_.Exception.Message)"
        }
    }
    $page++
} while ($resp.data.pagination.hasNext)

Write-Host ""
Write-Host "Published: $published | Already non-DRAFT: $skipped"
Write-Host "Luu y: sân ACTIVE nhưng thiếu giá/giờ vẫn không lên search — chạy seed-uat-data.ps1 cho bộ sân đủ dữ liệu."
