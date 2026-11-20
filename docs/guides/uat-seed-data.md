# UAT seed data (Booking & Payment)

Script dựng **dữ liệu demo gần production**: tên sân/địa chỉ/mô tả tiếng Việt, bảng giá theo ngày thường–cuối tuần, **ảnh sân thể thao (Pexels) → R2**, hồ sơ user có tên + SĐT, booking theo khung giờ cao điểm (multi-slot cho sân bóng 7).

## Điều kiện

Các script dùng POSIX `sh`, `curl`, `jq` (Linux/macOS, WSL hoặc Git Bash).
Script `test-r2-upload.sh` cần thêm `openssl`. Chạy `sh scripts/<tên-script>.sh --help`
để xem tham số; các biến `UAT_SEED_API_BASE`, `UAT_SEED_PROVIDER_EMAIL`,
`UAT_SEED_CUSTOMER_EMAIL`, `UAT_SEED_PASSWORD` vẫn được hỗ trợ.
Dữ liệu sân mẫu nằm trong `scripts/uat-courts.json`.

1. Stack: Postgres / Redis / RabbitMQ (`docker compose up -d`).
2. Backend `http://localhost:8080`.
3. `R2_ENABLED=true` + credential R2.
4. SePay (payment): `SEPAY_ENABLED=true` + sandbox merchant.
5. User ACTIVE + role:
   - Provider: `paysafe.ikegdn1@gmail.com`
   - Customer: `paysafe.jiwhva2@gmail.com`
   - Mật khẩu: `matkhauMANH123` (hoặc `UAT_SEED_PASSWORD`).

## Chạy

```sh
cd /path/to/sports-booking-be
sh scripts/seed-uat-data.sh
```

Kiểm tra upload, đọc ảnh riêng tư và dọn ảnh thử trên R2 (nhập tài khoản provider sở hữu sân):

```sh
sh scripts/test-r2-upload.sh --court-id '<court-uuid>'
```

### Luồng test API (booking + payment)

Sau khi seed (hoặc gộp một lệnh):

```sh
# Seed + smoke test toàn endpoint (IPN giả lập local → CONFIRMED)
sh scripts/test-booking-payment-flow.sh --seed-first --simulate-ipn

# Chỉ test theo manifest đã có (không seed lại)
sh scripts/test-booking-payment-flow.sh --simulate-ipn
```

| Tuỳ chọn test script | Ý nghĩa |
|---------------------|---------|
| `--seed-first` | Gọi `seed-uat-data.sh` trước khi test |
| `--simulate-ipn` | Gửi `POST /payments/sepay/ipn` với `SEPAY_SECRET_KEY` (đọc từ `.env`) |
| `--manifest-path` | Manifest mặc định `scripts/uat-seed-manifest.json` |

**Lưu ý:** Nếu `GET /bookings/me` trả 500, restart backend sau khi pull fix sort JPQL trên `BookingRepository`.

| Tuỳ chọn | Ý nghĩa |
|----------|---------|
| `--booking-date yyyy-MM-dd` | Ngày đặt sân (mặc định **ngày mai**, TZ VN) |
| `--skip-payment` | Không tạo checkout SePay |
| `--refresh-gallery` | Thêm ảnh Pexels nếu gallery còn slot (≤ max/sân) |
| `--manifest-path` | File JSON kết quả |

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

1. Chạy lại script (cùng key): **cập nhật** tên/mô tả/giá/giờ qua API; gallery giữ ảnh cũ trừ khi `--refresh-gallery` và chưa đủ ảnh tối đa.
2. Xóa sân UAT cũ (tên `UAT | ...` không có marker) trên provider portal hoặc DB nếu lộn xộn từ lần seed trước.
3. Đổi `--booking-date` sang **Thứ 7** để test giá cuối tuần trên manifest.

## SePay / IPN

Return URL → frontend; IPN backend cần tunnel. Booking HELD hết hạn ~10 phút nếu chưa thanh toán.

### DRAFT vs ACTIVE (hay gặp khi test SePay)

| Nơi thấy sân | Trạng thái |
|--------------|------------|
| **Provider portal** (`GET /provider/courts`) | Cả **DRAFT** lẫn **ACTIVE** (kể cả bản UAT cũ trùng tên) |
| **Customer search / đặt sân** | Chỉ **ACTIVE** + có giá/giờ |

Bộ sân demo đủ dữ liệu (marker `uat-seed:football-q9` …) nằm trong `scripts/uat-seed-manifest.json` — thường đã **ACTIVE** sau seed.

```sh
# Publish mọi sân DRAFT của provider (sân cũ thiếu giá vẫn không search được)
sh scripts/publish-draft-courts.sh

# Booking + payment mới + file HTML mở SePay sandbox
sh scripts/sepay-sandbox-checkout.sh --seed-first --open-browser --booking-date 2026-11-03
```

Thanh toán sandbox **thật**: mở `scripts/sepay-checkout.html` → chuyển khoản test theo SePay. Xác nhận booking **CONFIRMED** cần IPN tới `/api/v1/payments/sepay/ipn` (tunnel) hoặc test local dùng `--simulate-ipn` trên `test-booking-payment-flow.sh`.
