# Court API — FC-02 và FC-03

Tài liệu bàn giao backend trên nhánh `feature/court-search-management`.
Code ứng dụng được đối chiếu tại commit `909d034`.
Base URL local: `http://localhost:8080`. Swagger: `/swagger-ui.html`.

## 1. Phạm vi và mức hoàn thành

| Yêu cầu | Phần hiện có | Điểm cần tích hợp |
| --- | --- | --- |
| FC-02-01: Tìm kiếm sân | Từ khóa theo tên, địa chỉ, thành phố, tên môn thể thao | Không tìm kiếm không dấu hoặc theo khoảng cách địa lý |
| FC-02-02: Lọc kết hợp | Thành phố, môn, ngày, khoảng giờ, tổng giá; phân trang sau khi lọc | Chưa loại các giờ đã được đặt qua booking |
| FC-02-03: Chi tiết sân | Địa chỉ, mô tả, ảnh, tiện ích, nội quy, trạng thái, ownerId; giá qua API lịch/tìm kiếm | Chưa tích hợp đánh giá và thông tin công khai mở rộng của chủ sân |
| FC-02-04: Lịch theo ngày | Khung giờ, giá, trạng thái đóng tạm thời, thông tin có đủ giá hay chưa | Chưa có trạng thái đã đặt |
| FC-03-01: Tạo/sửa sân | PROVIDER tạo/sửa sân thuộc mình; quản lý ảnh, tiện ích, nội quy; công bố sân | Việc phê duyệt/cấp role PROVIDER thuộc luồng identity/provider của nhóm |
| FC-03-02: Lịch hoạt động | Một khoảng mở–đóng mỗi ngày trong tuần; độ dài khung; ngày không có lịch là ngày nghỉ | Giờ mở–đóng hiện phải nằm trong cùng ngày |
| FC-03-03: Đóng tạm thời | Tạo, xem, hủy khoảng đóng theo ngày; chống chồng lấn và đánh dấu khung bị đóng | Chưa cảnh báo booking đã xác nhận trùng lịch đóng |
| FC-03-04: Giá thuê | Giá theo thứ/khoảng giờ; tính theo thời lượng và hiển thị trước khi đặt | Booking cần sử dụng và chốt giá khi tạo lượt đặt |

**Kết quả tìm kiếm hoặc `closed: false` chưa phải xác nhận sân còn trống.**
Module booking cần kiểm tra trạng thái đặt hiện tại trước khi xác nhận lượt đặt.

## 2. Xác thực và trạng thái sân

Các API chủ sân cần JWT của tài khoản có role `PROVIDER`:

```http
Authorization: Bearer <accessToken>
```

- Tạo sân mặc định `DRAFT`.
- Công bố sân chuyển `DRAFT` sang `ACTIVE`.
- Công bố lại sân `ACTIVE` của chính chủ trả thành công.
- API công khai chỉ trả sân `ACTIVE`.
- API chủ sân trả cả sân nháp; lấy ownerId từ principal, không từ request body.
- Các endpoint quản lý vẫn kiểm tra quyền sở hữu.
- Tạo/sửa/công bố qua API court hiện trả 403 khi tài khoản khác sở hữu sân.
- API riêng của provider thường trả 404 nếu sân không thuộc tài khoản.
- Quyền PROVIDER là đầu vào của module court; module này không duyệt hồ sơ nhà cung cấp.

## 3. API công khai

| Method | Endpoint | Nội dung |
| --- | --- | --- |
| GET | `/api/v1/sports` | Danh sách môn thể thao |
| GET | `/api/v1/courts` | Danh sách/tìm kiếm cơ bản |
| GET | `/api/v1/courts/search` | Tìm kiếm nâng cao |
| GET | `/api/v1/courts/{id}` | Chi tiết sân ACTIVE |
| GET | `/api/v1/courts/{id}/schedule?date=YYYY-MM-DD` | Lịch và giá theo ngày |
| GET | `/api/v1/courts/{id}/images` | Danh sách ảnh |
| GET | `/api/v1/courts/{id}/images/{imageId}/content` | Nội dung ảnh |

### Tìm kiếm cơ bản

Query: `keyword`, `city`, `sportId`, `page` (mặc định 0), `size` (mặc định 20, tối đa 100).

`data.items[]` là CourtResponse trực tiếp. Từ khóa và city không phân biệt hoa/thường.
City khớp đầy đủ sau khi trim; từ khóa khớp một phần.
Ký tự `%`, `_` trong từ khóa được tìm như ký tự thường, không phải wildcard.

### Tìm kiếm nâng cao

Ngoài các query cơ bản, hỗ trợ:

| Query | Quy tắc |
| --- | --- |
| date | ISO date, ví dụ 2026-10-19 |
| startTime / endTime | Gửi cả hai; giờ nguyên phút; start trước end |
| minPrice / maxPrice | VND; không âm; min không lớn hơn max; biên được tính vào |

- Có lọc giờ hoặc giá thì phải có date.
- Khoảng giờ phải ghép được từ các khung đầy đủ, liên tiếp của sân.
- Giá lọc là **tổng tiền của khoảng được chọn**, không phải giá mỗi giờ.
- Nếu chỉ chọn ngày/giá, lấy khung đơn đầu tiên phù hợp.
- Nếu không gửi date, `match` là null.
- Bỏ sân không hoạt động ngày đó, khoảng bị đóng hoặc chưa có đủ giá.
- Chỉ phân trang sau khi lọc, `totalElements` là tổng số sân thực sự phù hợp.
- Quét các sân phù hợp bộ lọc cơ bản theo lô 50; giờ/giá/lịch đóng được tải theo lô.
  Chưa có kết quả benchmark tải lớn cho thuật toán này.

Ví dụ:

```http
GET /api/v1/courts/search?date=2026-10-19&startTime=16:00&endTime=18:00&minPrice=200000&maxPrice=300000&page=0&size=20
```

Cấu trúc phần data:

```json
{
  "items": [
    {
      "court": {
        "id": "<court UUID>",
        "name": "San thu nghiem",
        "ownerId": "<owner UUID>",
        "status": "ACTIVE",
        "amenities": ["Parking"],
        "rules": "Wear sports shoes.",
        "images": [],
        "coverImageUrl": null
      },
      "match": {
        "date": "2026-10-19",
        "startTime": "16:00:00",
        "endTime": "18:00:00",
        "totalPrice": 250000,
        "currency": "VND"
      }
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

CourtResponse còn có sportId, sportName, address, city, ward, description.
Ví dụ trên rút gọn phần thông tin sân.

### Lịch theo ngày

`data` có courtId, date, currency và slots:

```json
{
  "startTime": "10:00:00",
  "endTime": "11:00:00",
  "totalPrice": 100000,
  "priceConfigured": true,
  "closed": false
}
```

- Giá theo VND, làm tròn từng khung đến một đồng; khoảng nhiều khung cộng các giá khung.
- Không đủ giá: totalPrice=null, priceConfigured=false.
- Chỉ cần giao một phần với lịch đóng là cả khung có closed=true.
- Các khoảng liền kề không giao nhau, ví dụ đóng đến 12:00 không chặn khung bắt đầu 12:00.
- Ngày nghỉ trả slots=[]; không tạo khung cuối nếu thời lượng còn lại không đủ.

## 4. API chủ sân

| Method | Endpoint | HTTP thành công |
| --- | --- | --- |
| POST | `/api/v1/courts` | 201 |
| PUT | `/api/v1/courts/{id}` | 200 |
| POST | `/api/v1/courts/{id}/publish` | 200 |
| GET | `/api/v1/provider/courts` | 200 |
| GET | `/api/v1/provider/courts/{id}` | 200 |
| GET / PUT | `/api/v1/provider/courts/{id}/operating-hours` | 200 |
| GET / PUT | `/api/v1/provider/courts/{id}/price-rules` | 200 |
| POST | `/api/v1/provider/courts/{id}/closures` | 201 |
| GET | `/api/v1/provider/courts/{id}/closures?date=YYYY-MM-DD` | 200 |
| DELETE | `/api/v1/provider/courts/{id}/closures/{closureId}` | 204 |
| GET | `/api/v1/provider/courts/{id}/images` | 200 |
| POST | `/api/v1/provider/courts/{id}/images` | 201 |
| GET | `/api/v1/provider/courts/{id}/images/{imageId}/content` | 200 |
| PUT | `/api/v1/provider/courts/{id}/images/{imageId}/cover` | 200 |
| DELETE | `/api/v1/provider/courts/{id}/images/{imageId}` | 204 |

### Tạo/sửa thông tin

```json
{
  "sportId": "<sport UUID>",
  "name": "San thu nghiem",
  "address": "Dia chi thu nghiem",
  "city": "TP Ho Chi Minh",
  "ward": "Phuong thu nghiem",
  "description": "Mo ta san",
  "amenities": ["Parking", "Changing room"],
  "rules": "Wear sports shoes."
}
```

- sportId phải tồn tại. name/address/city bắt buộc.
- Tối đa 30 tiện ích, mỗi tên không rỗng và tối đa 100 ký tự.
- Trim và loại tiện ích trùng chính xác; khác hoa/thường vẫn là tên khác.
- Nội quy tối đa 5000 ký tự.
- PUT thay toàn bộ thông tin: bỏ amenities/rules sẽ xóa nội dung tương ứng.
- PUT không nhận ownerId/status, không thay ảnh/lịch/giá.

### Lịch tuần

PUT thay toàn bộ lịch tuần; days=[] xóa lịch. Ngày bị bỏ khỏi danh sách là ngày nghỉ.

```json
{
  "days": [
    {
      "dayOfWeek": "MONDAY",
      "openingTime": "06:00",
      "closingTime": "22:00",
      "slotDurationMinutes": 60
    }
  ]
}
```

Mỗi thứ chỉ xuất hiện một lần; độ dài khung 15–240 phút.
Giờ nguyên phút, mở trước đóng trong cùng ngày, đủ ít nhất một khung.

### Bảng giá

PUT thay toàn bộ bảng giá; rules=[] xóa bảng giá.

```json
{
  "rules": [
    {
      "dayOfWeek": "MONDAY",
      "startTime": "06:00",
      "endTime": "17:00",
      "pricePerHour": 100000
    },
    {
      "dayOfWeek": "MONDAY",
      "startTime": "17:00",
      "endTime": "22:00",
      "pricePerHour": 150000
    }
  ]
}
```

Tối đa 100 khoảng giá; giá phải dương, tối đa 10 chữ số phần nguyên/2 số thập phân.
Các khoảng cùng thứ không chồng lấn; tính tiền theo phần thời lượng trong từng mức giá.

### Lịch đóng

```json
{
  "closureDate": "2026-10-19",
  "startTime": "10:00",
  "endTime": "12:00",
  "reason": "Bao tri san"
}
```

Lý do bắt buộc, tối đa 255 ký tự. Giờ nguyên phút, start trước end.
Trùng lịch đóng hiện có trả 409.
Hủy lịch đóng sẽ mở lại các khung tương ứng nếu không còn khoảng đóng khác.

### Ảnh sân

POST dùng multipart/form-data, field `file`.

- PNG/JPEG thực, tối đa 5 MiB; tối đa 10 ảnh/sân.
- Mỗi cạnh tối đa 6000 pixel, tổng tối đa 20 triệu pixel.
- Kiểm tra dữ liệu thật và mã hóa lại; không tin phần mở rộng/MIME do client gửi.
- Ảnh đầu tiên làm ảnh đại diện. Đổi ảnh đại diện bảo đảm mỗi sân tối đa một cover.
- Xóa cover tự chọn ảnh còn lại đầu tiên; hết ảnh thì coverImageUrl=null.
- Response ảnh có id, url, cover, displayOrder.
- URL tương đối với backend; frontend ghép với base URL backend.
- Preview sân nháp dùng URL provider và Bearer token.
- Ảnh công khai chỉ đọc được khi sân ACTIVE; xóa ảnh thì URL trả 404.
- Xóa file sau khi giao dịch database commit; rollback upload có dọn file.

## 5. Lưu trữ và migration

Flyway V10–V15 tạo sport/court, giờ hoạt động, giá, lịch đóng, tiện ích/nội quy và ảnh.
Không sửa migration đã áp dụng. Cần phối hợp số migration với các nhánh khác.

Ảnh lưu trên filesystem local; implementation hiện không gọi R2.
Mặc định:

```text
<user.home>/.sports-booking/uploads/courts
```

Có thể cấu hình bằng biến môi trường `APP_STORAGE_LOCAL_DIRECTORY`.
Khi triển khai, dùng volume bền vững và sao lưu **cả database lẫn thư mục ảnh**.
Ảnh/file bí mật không nằm trong Git.
Nếu thao tác filesystem dọn file thất bại hoặc tiến trình dừng đột ngột, cần kiểm tra file mồ côi.

Các giới hạn multipart được cấu hình trong application.yml: file 5MB, request 6MB.

## 6. Kết quả kiểm tra đã thực hiện

- Manual API: tạo/sửa/công bố; CUSTOMER bị từ chối; quyền sở hữu; sân DRAFT không công khai.
- Lịch/giá: lưu và đọc lại; lịch sai bị từ chối; giá chồng lấn bị từ chối; dữ liệu cũ được giữ.
- Đóng sân: tạo/đọc/hủy; chống chồng lấn; tài khoản khác bị từ chối; lịch công khai cập nhật.
- Tiện ích/nội quy: create/update/clear/read; trim/deduplicate; dữ liệu sai; quyền sở hữu.
- Ảnh: PNG/JPEG, quyền riêng tư sân nháp, ảnh đại diện, dữ liệu giả, file lớn, giới hạn 10,
  đọc công khai, imageId thuộc sân khác và xóa. Script tổng hợp trả ALL CHECKS PASSED.
- Search: kết hợp toàn bộ bộ lọc, tổng giá nhiều khung, ngày nghỉ, biên lịch đóng,
  thiếu giá, phân trang và request sai. Script tổng hợp trả ALL CHECKS PASSED.
- Unit: LocalCourtImageStorageTest — 6/6 đạt.
- Unit: CourtScheduleCalculatorTest — 7/7 đạt.
- Hai nhóm unit được chạy riêng; chưa coi đây là kết quả toàn bộ clean verify/integration CI.

CI được cấu hình cho push main và pull_request đến main.
Sau khi mở PR, kiểm tra Build & Test và Integration (Testcontainers).

## 7. Điểm tích hợp cần bàn giao cho nhóm

1. Booking cần cung cấp khoảng đã được đặt/giữ chỗ cho courtId và ngày.
   Áp dụng cùng quy tắc giao khoảng vào lịch, tìm kiếm và kiểm tra trước khi đặt.
2. Khi đóng sân, cần kiểm tra booking đã xác nhận và trả cảnh báo theo chính sách nhóm.
3. Booking chốt tổng giá lúc tạo lượt đặt; không tính lại đơn cũ theo bảng giá mới.
4. Dữ liệu review và hồ sơ công khai của chủ sân cần tích hợp để hoàn thiện FC-02-03.
5. Luồng phê duyệt provider cần kiểm soát việc cấp/thu hồi role PROVIDER.
