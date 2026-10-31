# Sports Booking API

## API Documentation
- Swagger UI: /swagger-ui/index.html
- OpenAPI JSON: /v3/api-docs

## Modules
- Identity: đăng ký, đăng nhập, phân quyền
- Court: tìm kiếm sân, chi tiết sân, quản lý lịch hoạt động, bảng giá, đóng sân và ảnh sân
- Booking: truy vấn lịch sử booking của customer hiện tại
- Payment: tạo checkout SePay cho booking; các endpoint còn lại đang là khung
- Upload: luồng upload ảnh trực tiếp qua Cloudflare R2, xác thực ảnh và cấp quyền truy cập

## Related Documentation
- [API Conventions](./api-conventions.md)
- [Error Codes](./error-codes.md)
- [Identity](./modules/identity-api.md)
- [Court](./modules/court-api.md)
- [Booking](./modules/booking-api.md)
- [Payment](./modules/payment-api.md)
- [Upload](./modules/upload-api.md)
