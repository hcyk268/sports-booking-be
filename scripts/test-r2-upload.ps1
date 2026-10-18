param([Parameter(Mandatory = $true)][Guid]$CourtId, [string]$ApiBase = 'http://localhost:8080/api/v1')
$ErrorActionPreference = 'Stop'
$email = Read-Host 'Email tai khoan PROVIDER so huu san'
$password = Read-Host 'Mat khau' -AsSecureString
$plain = [System.Net.NetworkCredential]::new('', $password).Password
$loginBody = @{ email = $email; password = $plain } | ConvertTo-Json
$plain = $null
$login = Invoke-RestMethod -Uri ($ApiBase + '/auth/login') -Method Post -ContentType 'application/json' -Body $loginBody
$loginBody = $null
if ($login.data.user.roles -notcontains 'PROVIDER') { throw 'Account is not a PROVIDER.' }
$auth = @{ Authorization = 'Bearer ' + $login.data.accessToken }
$tempImage = Join-Path ([System.IO.Path]::GetTempPath()) ('r2-check-' + [Guid]::NewGuid() + '.png')
$assetId = $null
$imageId = $null
try {
    Add-Type -AssemblyName System.Drawing
    $bitmap = [System.Drawing.Bitmap]::new(2, 2)
    try { $bitmap.Save($tempImage, [System.Drawing.Imaging.ImageFormat]::Png) } finally { $bitmap.Dispose() }
    $body = @{ purpose = 'COURT_IMAGE'; resourceId = $CourtId.ToString(); fileName = 'r2-check.png'; contentType = 'image/png'; sizeBytes = (Get-Item -LiteralPath $tempImage).Length } | ConvertTo-Json
    $ticket = (Invoke-RestMethod -Uri ($ApiBase + '/uploads') -Method Post -ContentType 'application/json' -Body $body -Headers $auth).data
    $assetId = $ticket.uploadId
    $target = [Uri]$ticket.uploadUrl
    if ($target.Scheme -ne 'https' -or -not $target.Host.EndsWith('.r2.cloudflarestorage.com')) { throw 'Unexpected storage host.' }
    $putHeaders = @{}
    foreach ($property in $ticket.headers.PSObject.Properties) {
        if ($property.Name.ToLowerInvariant() -notin @('content-type', 'content-length', 'authorization', 'cookie', 'host')) { $putHeaders[$property.Name] = $property.Value }
    }
    Invoke-WebRequest -UseBasicParsing -Uri $ticket.uploadUrl -Method Put -ContentType 'image/png' -InFile $tempImage -Headers $putHeaders | Out-Null
    Write-Output 'PASS: Direct PUT to R2'
    $verified = Invoke-RestMethod -Uri ($ApiBase + '/uploads/' + $assetId + '/complete') -Method Post -Headers $auth
    if ($verified.data.status -ne 'READY') { throw 'Upload was not verified.' }
    Write-Output 'PASS: Backend verified object'
    $galleryUrl = $ApiBase + '/provider/courts/' + $CourtId + '/images'
    $attached = Invoke-RestMethod -Uri $galleryUrl -Method Post -ContentType 'application/json' -Headers $auth -Body (@{ uploadId = $assetId } | ConvertTo-Json)
    $imageId = $attached.data.id
    Write-Output 'PASS: Verified image attached to court'
    $access = Invoke-RestMethod -Uri ($galleryUrl + '/' + $imageId + '/access') -Headers $auth
    $readTarget = [Uri]$access.data.url
    if ($readTarget.Scheme -ne 'https' -or -not $readTarget.Host.EndsWith('.r2.cloudflarestorage.com')) { throw 'Unexpected read host.' }
    $read = Invoke-WebRequest -UseBasicParsing -Uri $access.data.url
    if ($read.StatusCode -ne 200 -or $read.Headers['Content-Type'] -notlike 'image/png*') { throw 'R2 image read failed.' }
    Write-Output 'PASS: Private R2 read without application JWT'
    Write-Output 'Browser CORS still needs checking in the frontend; this script cannot verify preflight.'
} finally {
    if ($imageId) {
        try { Invoke-RestMethod -Uri ($ApiBase + '/provider/courts/' + $CourtId + '/images/' + $imageId) -Method Delete -Headers $auth | Out-Null; Write-Output 'CLEANUP: Test image removed; R2 deletion is queued' }
        catch { Write-Output ('CLEANUP REQUIRED: remove test image ' + $imageId + ' using the owner API') }
    } elseif ($assetId) {
        try { Invoke-RestMethod -Uri ($ApiBase + '/uploads/' + $assetId) -Method Delete -Headers $auth | Out-Null } catch { Write-Output 'Pending upload will expire and be cleaned up.' }
    }
    if (Test-Path -LiteralPath $tempImage) { Remove-Item -LiteralPath $tempImage }
    $auth = $null
    $login = $null
}
