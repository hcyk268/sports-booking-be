# UAT seed data (Booking & Payment)

Script dựng **dữ liệu demo gần production**: tên sân/địa chỉ/mô tả tiếng Việt, bảng giá theo ngày thường–cuối tuần, **ảnh sân thể thao (Pexels) → R2**, hồ sơ user có tên + SĐT, booking theo khung giờ cao điểm (multi-slot cho sân bóng 7).

## Điều kiện

1. Stack: Postgres / Redis / RabbitMQ (`docker compose up -d`).
2. Backend `http://localhost:8080`.
3. `R2_ENABLED=true` + credential R2.
4. SePay (payment): `SEPAY_ENABLED=true` + sandbox merchant.
5. User ACTIVE + role:
   - Provider: `paysafe.ikegdn1@gmail.com`
   - Customer: `paysafe.jiwhva2@gmail.com`
   - Mật khẩu: `matkhauMANH123` (hoặc `UAT_SEED_PASSWORD`).

## Chạy

```powershell
cd d:\a2024START\BE\sports-booking-be
.\scripts\seed-uat-data.ps1
```

### Luồng test API (booking + payment)

Sau khi seed (hoặc gộp một lệnh):

```powershell
# Seed + smoke test toàn endpoint (IPN giả lập local → CONFIRMED)
.\scripts\test-booking-payment-flow.ps1 -SeedFirst -SimulateIpn

# Chỉ test theo manifest đã có (không seed lại)
.\scripts\test-booking-payment-flow.ps1 -SimulateIpn
```

| Tuỳ chọn test script | Ý nghĩa |
|---------------------|---------|
| `-SeedFirst` | Gọi `seed-uat-data.ps1` trước khi test |
| `-SimulateIpn` | Gửi `POST /payments/sepay/ipn` với `SEPAY_SECRET_KEY` (đọc từ `.env`) |
| `-ManifestPath` | Manifest mặc định `scripts/uat-seed-manifest.json` |

**Lưu ý:** Nếu `GET /bookings/me` trả 500, restart backend sau khi pull fix sort JPQL trên `BookingRepository`.

| Tuỳ chọn | Ý nghĩa |
|----------|---------|
| `-BookingDate yyyy-MM-dd` | Ngày đặt sân (mặc định **ngày mai**, TZ VN) |
| `-SkipPayment` | Không tạo checkout SePay |
| `-RefreshGallery` | Thêm ảnh Pexels nếu gallery còn slot (≤ max/sân) |
| `-ManifestPath` | File JSON kết quả |

**Rate limit login:** 10 lần / 15 phút — script retry 429; nếu vẫn fail, đợi hoặc flush Redis rate limit.

## Dữ liệu sau seed

| Key | Tên hiển thị | Giá (VND/giờ) | Booking mẫu |
|-----|----------------|---------------|-------------|
| `football-q9` | Saigon Seven Park - Vinhomes Grand Park | T2–T6: 420k / 680k; CN: 580k–890k | **2 slot liên tiếp** 18:00–20:00 + **payment** |
| `badminton-q7` | Phú Mỹ Badminton Club | T2–T6: 130k / 210k; CN: 190k–260k | 17:00–18:00 (HELD) |
| `football-hn` | Hanoi Arena Mini - Cầu Giấy | T2–T6: 320k–520k; CN: 480k–620k | 12:00–13:00 (HELD) |

- Marker idempotent ẩn cuối mô tả: `(uat-seed:football-q9)` — không hiện trên UI nếu FE cắt đoạn cuối (tuỳ FE).
- Provider profile: **Trần Minh Khoa** · Customer: **Lê Thị Mai** (+ SĐT VN).
- Giờ mở: sân bóng Q9 cuối tuần sớm hơn; cầu lông mở 5:30 sáng.

## Manifest

`scripts/uat-seed-manifest.json`: `courtId`, `bookingId`, slot, tổng tiền, payment/checkout (sân bóng Q9).

## Làm dữ liệu “thật” hơn trên môi trường cũ

1. Chạy lại script (cùng key): **cập nhật** tên/mô tả/giá/giờ qua API; gallery giữ ảnh cũ trừ khi `-RefreshGallery` và chưa đủ ảnh tối đa.
2. Xóa sân UAT cũ (tên `UAT | ...` không có marker) trên provider portal hoặc DB nếu lộn xộn từ lần seed trước.
3. Đổi `-BookingDate` sang **Thứ 7** để test giá cuối tuần trên manifest.

## SePay / IPN

Return URL → frontend; IPN backend cần tunnel. Booking HELD hết hạn ~10 phút nếu chưa thanh toán.

### DRAFT vs ACTIVE (hay gặp khi test SePay)

| Nơi thấy sân | Trạng thái |
|--------------|------------|
| **Provider portal** (`GET /provider/courts`) | Cả **DRAFT** lẫn **ACTIVE** (kể cả bản UAT cũ trùng tên) |
| **Customer search / đặt sân** | Chỉ **ACTIVE** + có giá/giờ |

Bộ sân demo đủ dữ liệu (marker `uat-seed:football-q9` …) nằm trong `scripts/uat-seed-manifest.json` — thường đã **ACTIVE** sau seed.

```powershell
# Publish mọi sân DRAFT của provider (sân cũ thiếu giá vẫn không search được)
.\scripts\publish-draft-courts.ps1

# Booking + payment mới + file HTML mở SePay sandbox
.\scripts\sepay-sandbox-checkout.ps1 -SeedFirst -OpenBrowser -BookingDate 2026-11-03
```

Thanh toán sandbox **thật**: mở `scripts/sepay-checkout.html` → chuyển khoản test theo SePay. Xác nhận booking **CONFIRMED** cần IPN tới `/api/v1/payments/sepay/ipn` (tunnel) hoặc test local dùng `-SimulateIpn` trên `test-booking-payment-flow.ps1`.
