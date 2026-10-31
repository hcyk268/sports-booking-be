# Seeds realistic demo courts (R2 sports photos), bookings, and optional SePay checkout.
# Requires: API running, R2_ENABLED=true, provider + customer seed users.
param(
    [string]$ApiBase = $(if ($env:UAT_SEED_API_BASE) { $env:UAT_SEED_API_BASE } else { 'http://localhost:8080/api/v1' }),
    [string]$ProviderEmail = $(if ($env:UAT_SEED_PROVIDER_EMAIL) { $env:UAT_SEED_PROVIDER_EMAIL } else { 'paysafe.ikegdn1@gmail.com' }),
    [string]$CustomerEmail = $(if ($env:UAT_SEED_CUSTOMER_EMAIL) { $env:UAT_SEED_CUSTOMER_EMAIL } else { 'paysafe.jiwhva2@gmail.com' }),
    [string]$Password = $(if ($env:UAT_SEED_PASSWORD) { $env:UAT_SEED_PASSWORD } else { 'matkhauMANH123' }),
    [string]$BookingDate = '',
    [switch]$SkipPayment,
    [switch]$RefreshGallery,
    [string]$ManifestPath = (Join-Path $PSScriptRoot 'uat-seed-manifest.json')
)

$ErrorActionPreference = 'Stop'
$FootballSportId = 'd1000000-0000-0000-0000-000000000001'
$BadmintonSportId = 'd1000000-0000-0000-0000-000000000002'
$Weekdays = @('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY')
$Weekend = @('SATURDAY', 'SUNDAY')

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
            if ($response.Content) {
                return $response.Content | ConvertFrom-Json
            }
            return $null
        }
        catch {
            $response = $_.Exception.Response
            $status = if ($response) { [int]$response.StatusCode } else { 0 }
            if ($status -eq 429 -and $attempt -lt 6) {
                $waitSec = [Math]::Min(60, 5 * $attempt)
                Write-Warning "Rate limited on $Method $Path; retry in ${waitSec}s (attempt $attempt)"
                Start-Sleep -Seconds $waitSec
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
            throw "API $Method $Path failed ($status): $detail"
        }
    }
}

function Connect-Account {
    param([string]$Email, [string[]]$ExpectedRoles)
    $login = Invoke-Api -Method Post -Path '/auth/login' -Body @{ email = $Email; password = $Password }
    $token = $login.data.accessToken
    $roles = @($login.data.user.roles)
    foreach ($role in $ExpectedRoles) {
        if ($roles -notcontains $role) {
            throw "Account $Email missing role $role (has: $($roles -join ', '))"
        }
    }
    return @{
        Token  = $token
        UserId = $login.data.user.id
        Auth   = @{ Authorization = "Bearer $token" }
        Email  = $Email
    }
}

function Update-SeedUserProfile {
    param(
        [hashtable]$Account,
        [string]$FullName,
        [string]$Phone
    )
    $me = Invoke-Api -Method Get -Path '/users/me' -Headers $Account.Auth
    $currentName = $me.data.fullName
    $currentPhone = $me.data.phone
    if ($currentName -eq $FullName -and $currentPhone -eq $Phone) {
        Write-Host "Profile OK: $($Account.Email) ($FullName)"
        return
    }
    Invoke-Api -Method Patch -Path '/users/me' -Headers $Account.Auth -Body @{
        version  = $me.data.version
        fullName = $FullName
        phone    = $Phone
    } | Out-Null
    Write-Host "Updated profile: $($Account.Email) -> $FullName, $Phone"
}

function Get-ProviderCourtBySeedKey {
    param([hashtable]$Auth, [string]$SeedKey)
    $marker = "uat-seed:$SeedKey"
    $page = 0
    do {
        $resp = Invoke-Api -Method Get -Path "/provider/courts?page=$page&size=50" -Headers $Auth.Auth
        foreach ($court in @($resp.data.items)) {
            if ($court.description -like "*$marker*") { return $court }
        }
        $page++
    } while ($resp.data.pagination.hasNext)
    return $null
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

function New-OperatingHoursBody {
    param([hashtable]$Definition)
    $days = @()
    foreach ($day in $Weekdays) {
        $days += @{
            dayOfWeek           = $day
            openingTime         = $Definition.weekdayOpen
            closingTime         = $Definition.weekdayClose
            slotDurationMinutes = $Definition.slotMinutes
        }
    }
    foreach ($day in $Weekend) {
        $days += @{
            dayOfWeek           = $day
            openingTime         = $Definition.weekendOpen
            closingTime         = $Definition.weekendClose
            slotDurationMinutes = $Definition.slotMinutes
        }
    }
    return @{ days = $days }
}

function New-PriceRulesBody {
    param([array]$Segments)
    $rules = @()
    foreach ($seg in $Segments) {
        foreach ($day in @($seg.days)) {
            $rules += @{
                dayOfWeek    = $day
                startTime    = $seg.start
                endTime      = $seg.end
                pricePerHour = $seg.price
            }
        }
    }
    return @{ rules = $rules }
}

function Save-TempImage {
    param([string]$Url, [string]$FileName)
    $tempDir = Join-Path ([IO.Path]::GetTempPath()) ('uat-seed-' + [Guid]::NewGuid().ToString('n'))
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    $path = Join-Path $tempDir $FileName
    $downloadHeaders = @{ 'User-Agent' = 'SportsBooking-Seed/1.0' }
    Invoke-WebRequest -Uri $Url -OutFile $path -UseBasicParsing -Headers $downloadHeaders | Out-Null
    $bytes = [IO.File]::ReadAllBytes($path)
    if ($bytes.Length -lt 1 -or $bytes.Length -gt 5242880) {
        throw "Downloaded image size invalid: $($bytes.Length) bytes from $Url"
    }
    $contentType = 'image/jpeg'
    if ($FileName.ToLowerInvariant().EndsWith('.png')) { $contentType = 'image/png' }
    return @{ Path = $path; TempDir = $tempDir; Size = $bytes.Length; ContentType = $contentType }
}

function Add-CourtImageFromUrl {
    param(
        [hashtable]$Auth,
        [guid]$CourtId,
        [string]$ImageUrl,
        [string]$FileName
    )
    $download = Save-TempImage -Url $ImageUrl -FileName $FileName
    try {
        $initBody = @{
            purpose     = 'COURT_IMAGE'
            resourceId  = $CourtId.ToString()
            fileName    = $FileName
            contentType = $download.ContentType
            sizeBytes   = $download.Size
        }
        $ticket = (Invoke-Api -Method Post -Path '/uploads' -Body $initBody -Headers $Auth.Auth).data
        $putHeaders = @{}
        foreach ($property in $ticket.headers.PSObject.Properties) {
            $name = $property.Name.ToLowerInvariant()
            if ($name -notin @('content-type', 'content-length', 'authorization', 'cookie', 'host')) {
                $putHeaders[$property.Name] = $property.Value
            }
        }
        Invoke-WebRequest -UseBasicParsing -Uri $ticket.uploadUrl -Method Put `
            -ContentType $download.ContentType -InFile $download.Path -Headers $putHeaders | Out-Null
        $verified = Invoke-Api -Method Post -Path "/uploads/$($ticket.uploadId)/complete" -Headers $Auth.Auth
        if ($verified.data.status -ne 'READY') {
            throw "Upload $($ticket.uploadId) not READY: $($verified.data.status)"
        }
        Invoke-Api -Method Post -Path "/provider/courts/$CourtId/images" `
            -Body @{ uploadId = $ticket.uploadId } -Headers $Auth.Auth | Out-Null
    }
    finally {
        if (Test-Path $download.TempDir) {
            Remove-Item -LiteralPath $download.TempDir -Recurse -Force
        }
    }
}

function Sync-CourtGallery {
    param(
        [hashtable]$Provider,
        [guid]$CourtId,
        [hashtable]$Definition
    )
    $gallery = Invoke-Api -Method Get -Path "/provider/courts/$CourtId/images" -Headers $Provider.Auth
    $imageCount = @($gallery.data).Count
    if ($imageCount -gt 0 -and -not $RefreshGallery) {
        Write-Host "  Gallery: $imageCount image(s) (use -RefreshGallery to add more on empty-only policy skipped)."
        return
    }
    if ($imageCount -ge $Definition.maxImages) {
        Write-Host "  Gallery full ($imageCount); skip upload."
        return
    }
    $idx = 0
    foreach ($img in $Definition.imageUrls) {
        if ($imageCount -ge $Definition.maxImages) { break }
        $idx++
        $fileName = $Definition.key + "-photo-$idx.jpg"
        Write-Host "  Uploading: $fileName"
        try {
            Add-CourtImageFromUrl -Auth $Provider -CourtId $CourtId -ImageUrl $img -FileName $fileName
            $imageCount++
        }
        catch {
            Write-Warning "  Gallery skip $fileName : $($_.Exception.Message)"
        }
    }
}

function Sync-CourtProfile {
    param(
        [hashtable]$Provider,
        [guid]$CourtId,
        [hashtable]$Definition,
        [long]$Version
    )
    Invoke-Api -Method Put -Path "/courts/$CourtId" -Headers $Provider.Auth -Body @{
        version     = $Version
        sportId     = $Definition.sportId
        name        = $Definition.name
        address     = $Definition.address
        city        = $Definition.city
        ward        = $Definition.ward
        description = $Definition.description
        amenities   = $Definition.amenities
        rules       = $Definition.rules
    } | Out-Null
}

function Ensure-UatCourt {
    param(
        [hashtable]$Provider,
        [hashtable]$Definition
    )
    $existing = Get-ProviderCourtBySeedKey -Auth $Provider -SeedKey $Definition.key
    if ($existing) {
        $courtId = [guid]$existing.id
        Write-Host "Court exists: $($Definition.name) ($courtId)"
        Sync-CourtProfile -Provider $Provider -CourtId $courtId -Definition $Definition -Version $existing.version
    }
    else {
        $created = Invoke-Api -Method Post -Path '/courts' -Body @{
            sportId     = $Definition.sportId
            name        = $Definition.name
            address     = $Definition.address
            city        = $Definition.city
            ward        = $Definition.ward
            description = $Definition.description
            amenities   = $Definition.amenities
            rules       = $Definition.rules
        } -Headers $Provider.Auth
        $courtId = [guid]$created.data.id
        Write-Host "Created court: $($Definition.name) ($courtId)"
    }

    Invoke-Api -Method Put -Path "/provider/courts/$courtId/operating-hours" `
        -Body (New-OperatingHoursBody -Definition $Definition) -Headers $Provider.Auth | Out-Null

    Invoke-Api -Method Put -Path "/provider/courts/$courtId/price-rules" `
        -Body (New-PriceRulesBody -Segments $Definition.priceSegments) -Headers $Provider.Auth | Out-Null

    Sync-CourtGallery -Provider $Provider -CourtId $courtId -Definition $Definition

    $detail = Invoke-Api -Method Get -Path "/provider/courts/$courtId" -Headers $Provider.Auth
    if ($detail.data.status -ne 'ACTIVE') {
        $published = Invoke-Api -Method Post -Path "/courts/$courtId/publish" -Headers $Provider.Auth
        Write-Host "Published: $($published.data.status)"
        return $published.data
    }
    Write-Host "Active: $($Definition.name)"
    return $detail.data
}

function Find-ScheduleSlot {
    param(
        [guid]$CourtId,
        [datetime]$Date,
        [string]$PreferredStart,
        [string]$PreferredEnd = $null,
        [string[]]$ExcludeStartTimes = @()
    )
    $dateStr = $Date.ToString('yyyy-MM-dd')
    $schedule = Invoke-Api -Method Get -Path "/courts/$CourtId/schedule?date=$dateStr"
    $candidates = @($schedule.data.slots | Where-Object {
            -not $_.closed -and $_.priceConfigured -and $null -ne $_.totalPrice
        } | Where-Object {
            (Format-LocalTime ([string]$_.startTime)) -notin $ExcludeStartTimes
        })
    if (-not $candidates.Count) {
        throw "No bookable slot on $dateStr for court $CourtId"
    }
    if ($PreferredStart) {
        $preferredAt = Format-LocalTime $PreferredStart
        $match = $candidates | Where-Object { (Format-LocalTime ([string]$_.startTime)) -eq $preferredAt } | Select-Object -First 1
        if ($match) {
            return @{
                date      = $dateStr
                startTime = Format-LocalTime ([string]$match.startTime)
                endTime   = Format-LocalTime ([string]$match.endTime)
                price     = $match.totalPrice
            }
        }
    }
    $fallback = $candidates | Select-Object -First 1
    return @{
        date      = $dateStr
        startTime = Format-LocalTime ([string]$fallback.startTime)
        endTime   = Format-LocalTime ([string]$fallback.endTime)
        price     = $fallback.totalPrice
    }
}

function Resolve-ConsecutiveBookingSlots {
    param(
        [guid]$CourtId,
        [datetime]$Date,
        [int]$Count,
        [int]$StartIndex = 0
    )
    $dateStr = $Date.ToString('yyyy-MM-dd')
    $schedule = Invoke-Api -Method Get -Path "/courts/$CourtId/schedule?date=$dateStr"
    $candidates = @($schedule.data.slots | Where-Object {
            -not $_.closed -and $_.priceConfigured -and $null -ne $_.totalPrice
        })
    for ($i = $StartIndex; $i -le ($candidates.Count - $Count); $i++) {
        $block = @($candidates[$i..($i + $Count - 1)])
        $consecutive = $true
        for ($j = 0; $j -lt ($Count - 1); $j++) {
            if ((Format-LocalTime ([string]$block[$j].endTime)) -ne (Format-LocalTime ([string]$block[$j + 1].startTime))) {
                $consecutive = $false
                break
            }
        }
        if ($consecutive) {
            return @($block | ForEach-Object {
                    @{
                        date      = $dateStr
                        startTime = Format-LocalTime ([string]$_.startTime)
                        endTime   = Format-LocalTime ([string]$_.endTime)
                        price     = $_.totalPrice
                    }
                })
        }
    }
    throw "No $Count consecutive bookable slots on $dateStr for court $CourtId (from index $StartIndex)"
}

function New-BookingHold-Resilient {
    param(
        [hashtable]$Customer,
        [guid]$CourtId,
        [datetime]$Date,
        [string]$DefinitionKey,
        [int]$SlotCount
    )
    for ($start = 0; $start -lt 24; $start++) {
        $resolvedSlots = Resolve-ConsecutiveBookingSlots -CourtId $CourtId -Date $Date -Count $SlotCount -StartIndex $start
        $slotKeyPart = ($resolvedSlots | ForEach-Object { "$($_.startTime)-$($_.endTime)" }) -join '_'
        $holdKey = "uat-seed-hold-$DefinitionKey-$($Date.ToString('yyyy-MM-dd'))-$slotKeyPart"
        if ($holdKey.Length -gt 100) {
            $holdKey = "uat-seed-hold-$DefinitionKey-$($Date.ToString('yyyy-MM-dd'))-$start"
        }
        try {
            return @{
                booking = (New-BookingHold -Customer $Customer -CourtId $CourtId -Slots $resolvedSlots -IdempotencyKey $holdKey)
                slots   = $resolvedSlots
                holdKey = $holdKey
            }
        }
        catch {
            if ($_.Exception.Message -match '\(409\)|SLOT_UNAVAILABLE|NOT_BOOKABLE') { continue }
            throw
        }
    }
    throw "Could not create booking for $DefinitionKey on $($Date.ToString('yyyy-MM-dd')) after trying consecutive slot blocks"
}

function New-BookingHold {
    param(
        [hashtable]$Customer,
        [guid]$CourtId,
        [array]$Slots,
        [string]$IdempotencyKey
    )
    $slotPayload = @($Slots | ForEach-Object {
            @{
                date      = $_.date
                startTime = $_.startTime
                endTime   = $_.endTime
            }
        })
    $body = @{
        courtId = $CourtId.ToString()
        slots   = $slotPayload
    }
    $resp = Invoke-Api -Method Post -Path '/bookings' -Body $body -Headers $Customer.Auth -IdempotencyKey $IdempotencyKey
    return $resp.data
}

function New-PaymentCheckout {
    param(
        [hashtable]$Customer,
        [guid]$BookingId,
        [string]$IdempotencyKey,
        [string]$PaymentMethod = 'BANK_TRANSFER'
    )
    $resp = Invoke-Api -Method Post -Path "/bookings/$BookingId/payments" `
        -Body @{ paymentMethod = $PaymentMethod } -Headers $Customer.Auth -IdempotencyKey $IdempotencyKey
    return $resp.data
}

function Build-Description {
    param([string]$SeedKey, [string]$PublicText)
    return "$PublicText`n`n(uat-seed:$SeedKey)"
}

# Realistic venue copy + market-style pricing (VND / hour / sân).
$courtDefinitions = @(
    @{
        key           = 'football-q9'
        sportId       = $FootballSportId
        name          = 'Saigon Seven Park - Sân bóng 7 Vinhomes Grand Park'
        address       = '118 Nguyễn Xiển, Long Bình, Thủ Đức'
        city          = 'TP. Hồ Chí Minh'
        ward          = 'Long Bình'
        description   = Build-Description 'football-q9' @'
Complex sân bóng 7 người mặt cỏ nhân tạo chuẩn thi đấu phong trào, hệ thống đèn LED 800 lux, khung thành chắc chắn. Phù hợp đặt sân theo giờ cho công ty, nhóm bạn hoặc giải nội bộ cuối tuần. Bãi xe rộng, có quầy nước giải khát và khu vực thay giày.
'@
        amenities     = @('Cỏ nhân tạo', 'Đèn LED', 'Bãi xe ô tô', 'Phòng thay đồ', 'Quầy nước', 'Camera an ninh', 'Wifi khu vực chờ')
        rules         = @'
- Giày đinh TF/IC; không đinh kim loại.
- Không hút thuốc, không mang chai thủy tinh vào sân.
- Đặt sân trước tối thiểu 2 giờ; hủy miễn phí trước giờ chơi 4 tiếng (theo chính sách app).
- Trẻ em dưới 12 tuổi cần người lớn giám sát.
'@
        weekdayOpen   = '06:00:00'
        weekdayClose  = '23:00:00'
        weekendOpen   = '05:30:00'
        weekendClose  = '23:30:00'
        slotMinutes   = 60
        maxImages     = 4
        imageUrls     = @(
            'https://images.pexels.com/photos/114296/pexels-photo-114296.jpeg?auto=compress&cs=tinysrgb&w=1600'
            'https://images.pexels.com/photos/47730/the-ball-stadion-football-the-pitch-47730.jpeg?auto=compress&cs=tinysrgb&w=1600'
            'https://images.pexels.com/photos/274422/pexels-photo-274422.jpeg?auto=compress&cs=tinysrgb&w=1600'
        )
        priceSegments = @(
            @{ days = $Weekdays; start = '06:00:00'; end = '16:00:00'; price = 420000 }
            @{ days = $Weekdays; start = '16:00:00'; end = '23:00:00'; price = 680000 }
            @{ days = $Weekend; start = '05:30:00'; end = '12:00:00'; price = 580000 }
            @{ days = $Weekend; start = '12:00:00'; end = '17:00:00'; price = 750000 }
            @{ days = $Weekend; start = '17:00:00'; end = '23:30:00'; price = 890000 }
        )
        bookingSlots  = @(
            @{ preferredStart = '18:30'; preferredEnd = '19:30' }
            @{ preferredStart = '19:30'; preferredEnd = '20:30' }
        )
        createPayment = $true
        paymentMethod = 'BANK_TRANSFER'
    },
    @{
        key           = 'badminton-q7'
        sportId       = $BadmintonSportId
        name          = 'Phú Mỹ Badminton Club - Hà Đô Centrosa'
        address       = '819 Tôn Đản, phường Tân Phú, Quận 7'
        city          = 'TP. Hồ Chí Minh'
        ward          = 'Tân Phú'
        description   = Build-Description 'badminton-q7' @'
Hệ thống 6 sân cầu lông tiêu chuẩn BWF, sàn đế cao su chống trượt, ánh sáng đều không chói. Có dịch vụ căng cước, cho thuê vợt và bán cầu chính hãng tại quầy. Không gian máy lạnh khu vực nghỉ, phù hợp tập luyện cố định hoặc đặt lẻ buổi tối.
'@
        amenities     = @('Sàn chống trượt', 'Máy lạnh khu nghỉ', 'Căng cước', 'Cho thuê vợt', 'Quầy đồ uống', 'Tủ đựng đồ', 'Wifi')
        rules         = @'
- Mang giày cầu lông sạch; không mang giày đinh ra sân.
- Giữ im lặng khi đối thủ giao cầu; không quay video flash gần sân.
- Mỗi sân tối đa 4 người khi đánh đôi; trẻ em cần huấn luyện viên hoặc phụ huynh.
'@
        weekdayOpen   = '05:30:00'
        weekdayClose  = '22:30:00'
        weekendOpen   = '06:00:00'
        weekendClose  = '23:00:00'
        slotMinutes   = 60
        maxImages     = 4
        imageUrls     = @(
            'https://images.pexels.com/photos/209977/pexels-photo-209977.jpeg?auto=compress&cs=tinysrgb&w=1600'
            'https://images.pexels.com/photos/366054/pexels-photo-366054.jpeg?auto=compress&cs=tinysrgb&w=1600'
        )
        priceSegments = @(
            @{ days = $Weekdays; start = '05:30:00'; end = '16:00:00'; price = 130000 }
            @{ days = $Weekdays; start = '16:00:00'; end = '22:30:00'; price = 210000 }
            @{ days = $Weekend; start = '06:00:00'; end = '14:00:00'; price = 190000 }
            @{ days = $Weekend; start = '14:00:00'; end = '18:00:00'; price = 230000 }
            @{ days = $Weekend; start = '18:00:00'; end = '23:00:00'; price = 260000 }
        )
        bookingSlots  = @(
            @{ preferredStart = '17:00'; preferredEnd = '18:00' }
        )
        createPayment = $false
    },
    @{
        key           = 'football-hn'
        sportId       = $FootballSportId
        name          = 'Hanoi Arena Mini - Sân 5 người Cầu Giấy'
        address       = '42 Dịch Vọng Hậu, Dịch Vọng, Cầu Giấy'
        city          = 'Hà Nội'
        ward          = 'Dịch Vọng'
        description   = Build-Description 'football-hn' @'
Sân mini 5v5 lối xuống hầm có mái che một phần, mặt sân cao su d granules, lưới chắn bóng cao. Hay được các team văn phòng khu Dịch Vọng – Xuân Thủy đặt khung giờ trưa và sau 18h. Có camera và nhân viên trực quầy đến 23h.
'@
        amenities     = @('Mái che một phần', 'Lưới chắn bóng', 'Bãi xe máy', 'Quầy nước', 'Camera', 'Đèn chiếu sáng')
        rules         = @'
- Sân 5 người; tối đa 12 người trên sân (bao gồm dự bị).
- Không tổ chức tiệc BBQ trong khu vực sân.
- Hoàn tiền theo chính sách hủy trên app; không nhận chuyển khoản trực tiếp ngoài hệ thống.
'@
        weekdayOpen   = '06:00:00'
        weekdayClose  = '23:00:00'
        weekendOpen   = '06:00:00'
        weekendClose  = '23:00:00'
        slotMinutes   = 60
        maxImages     = 3
        imageUrls     = @(
            'https://images.pexels.com/photos/1884574/pexels-photo-1884574.jpeg?auto=compress&cs=tinysrgb&w=1600'
            'https://images.pexels.com/photos/399187/pexels-photo-399187.jpeg?auto=compress&cs=tinysrgb&w=1600'
        )
        priceSegments = @(
            @{ days = $Weekdays; start = '06:00:00'; end = '11:30:00'; price = 320000 }
            @{ days = $Weekdays; start = '11:30:00'; end = '17:00:00'; price = 380000 }
            @{ days = $Weekdays; start = '17:00:00'; end = '23:00:00'; price = 520000 }
            @{ days = $Weekend; start = '06:00:00'; end = '17:00:00'; price = 480000 }
            @{ days = $Weekend; start = '17:00:00'; end = '23:00:00'; price = 620000 }
        )
        bookingSlots  = @(
            @{ preferredStart = '12:00'; preferredEnd = '13:00' }
        )
        createPayment = $false
    }
)

Write-Host "Realistic UAT seed -> $ApiBase"
$targetDate = Get-BookingDate
Write-Host "Booking date: $($targetDate.ToString('yyyy-MM-dd')) ($($targetDate.DayOfWeek))"

$provider = Connect-Account -Email $ProviderEmail -ExpectedRoles @('PROVIDER')
$customer = Connect-Account -Email $CustomerEmail -ExpectedRoles @('CUSTOMER')

Update-SeedUserProfile -Account $provider -FullName 'Trần Minh Khoa' -Phone '0908123456'
Update-SeedUserProfile -Account $customer -FullName 'Lê Thị Mai' -Phone '0918765432'

$manifest = @{
    seededAt      = (Get-Date).ToUniversalTime().ToString('o')
    apiBase       = $ApiBase
    providerEmail = $ProviderEmail
    customerEmail = $CustomerEmail
    bookingDate   = $targetDate.ToString('yyyy-MM-dd')
    courts        = @()
    bookings      = @()
}

foreach ($def in $courtDefinitions) {
    $court = Ensure-UatCourt -Provider $provider -Definition $def
    $courtId = [guid]$court.id

    $slotCount = @($def.bookingSlots).Count
    if ($slotCount -lt 1) { $slotCount = 1 }
    $holdResult = New-BookingHold-Resilient -Customer $customer -CourtId $courtId -Date $targetDate `
        -DefinitionKey $def.key -SlotCount $slotCount
    $booking = $holdResult.booking
    $resolvedSlots = $holdResult.slots
    $holdKey = $holdResult.holdKey

    $entry = @{
        key       = $def.key
        courtId   = $courtId.ToString()
        courtName = $def.name
        city      = $def.city
        slots     = $resolvedSlots
        booking   = @{
            id             = $booking.id
            status         = $booking.status
            totalAmount    = $booking.totalAmount
            holdExpiresAt  = $booking.holdExpiresAt
            idempotencyKey = $holdKey
        }
        payment = $null
    }

    if (-not $SkipPayment -and $def.createPayment) {
        $payKey = "uat-seed-pay-$($booking.id)"
        try {
            $checkout = New-PaymentCheckout -Customer $customer -BookingId ([guid]$booking.id) `
                -IdempotencyKey $payKey -PaymentMethod $def.paymentMethod
            $entry.payment = @{
                paymentId      = $checkout.paymentId
                attemptId      = $checkout.attemptId
                status         = $checkout.status
                amount         = $checkout.amount
                idempotencyKey = $payKey
                checkoutAction = $checkout.checkout.action
                checkoutMethod = $checkout.checkout.method
                fieldCount     = @($checkout.checkout.fields).Count
            }
            Write-Host "Payment checkout: $($checkout.paymentId) ($($def.paymentMethod))"
        }
        catch {
            Write-Warning "Payment failed for $($booking.id): $($_.Exception.Message)"
        }
    }

    $manifest.courts += @{
        key    = $def.key
        id     = $courtId.ToString()
        name   = $def.name
        city   = $def.city
        status = $court.status
    }
    $manifest.bookings += $entry
    $slotLabel = ($resolvedSlots | ForEach-Object { "$($_.startTime)-$($_.endTime)" }) -join ', '
    Write-Host "Booking $($booking.status): $($booking.id) | $($def.name) | $slotLabel | $($booking.totalAmount) VND"
}

$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ManifestPath -Encoding utf8
Write-Host ""
Write-Host "Done. Manifest: $ManifestPath"
Write-Host "Provider: $ProviderEmail (Trần Minh Khoa) | Customer: $CustomerEmail (Lê Thị Mai)"
