-- Seed Roles
INSERT INTO roles (id, code, name, description)
VALUES
    ('a0000000-0000-0000-0000-000000000001',
     'ADMIN',
     'Administrator',
     'Quản trị viên toàn quyền hệ thống'),

    ('a0000000-0000-0000-0000-000000000002',
     'PROVIDER',
     'Court Provider',
     'Chủ sân / Đối tác cung cấp dịch vụ sân và thể thao'),

    ('a0000000-0000-0000-0000-000000000003',
     'CUSTOMER',
     'Customer',
     'Khách hàng tìm kiếm, đặt sân và sử dụng dịch vụ')

    ON CONFLICT (code) DO NOTHING;

-- Seed Permissions
INSERT INTO permissions (id, code, name, description, module)
VALUES
    -- User management
    ('b0000000-0000-0000-0000-000000000001',
     'USER_READ',
     'Xem người dùng',
     'Xem danh sách và thông tin người dùng',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000002',
     'USER_CREATE',
     'Tạo người dùng',
     'Tạo tài khoản người dùng mới',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000003',
     'USER_UPDATE',
     'Cập nhật người dùng',
     'Chỉnh sửa thông tin người dùng',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000004',
     'USER_DELETE',
     'Xóa người dùng',
     'Xóa tài khoản người dùng',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000005',
     'USER_MANAGE_ROLE',
     'Phân quyền người dùng',
     'Gán hoặc thu hồi vai trò của người dùng',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000006',
     'USER_LOCK',
     'Khóa tài khoản',
     'Khóa tài khoản người dùng',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000007',
     'USER_UNLOCK',
     'Mở khóa tài khoản',
     'Mở khóa tài khoản người dùng',
     'IDENTITY'),

    -- Self profile
    ('b0000000-0000-0000-0000-000000000008',
     'USER_READ_SELF',
     'Xem thông tin cá nhân',
     'Xem thông tin của chính mình',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000009',
     'USER_UPDATE_SELF',
     'Cập nhật thông tin cá nhân',
     'Cập nhật thông tin của chính mình',
     'IDENTITY'),

    -- Role / Permission
    ('b0000000-0000-0000-0000-000000000010',
     'ROLE_READ',
     'Xem vai trò và quyền',
     'Xem danh sách vai trò và quyền hạn',
     'IDENTITY'),

    ('b0000000-0000-0000-0000-000000000011',
     'ROLE_MANAGE',
     'Quản lý vai trò và quyền',
     'Tạo, sửa và cấu hình vai trò cùng quyền hạn',
     'IDENTITY'),

    ON CONFLICT (code) DO NOTHING;


INSERT INTO role_permissions (role_id, permission_id)
SELECT
    r.id,
    p.id
FROM roles r
         JOIN permissions p
              ON p.code IN (
                            'USER_READ',
                            'USER_CREATE',
                            'USER_UPDATE',
                            'USER_DELETE',
                            'USER_MANAGE_ROLE',
                            'USER_LOCK',
                            'USER_UNLOCK',
                            'ROLE_READ',
                            'ROLE_MANAGE'
                  )
WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT
    r.id,
    p.id
FROM roles r
         JOIN permissions p
              ON p.code IN (
                            'USER_READ_SELF',
                            'USER_UPDATE_SELF'
                  )
WHERE r.code = 'PROVIDER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT
    r.id,
    p.id
FROM roles r
         JOIN permissions p
              ON p.code IN (
                            'USER_READ_SELF',
                            'USER_UPDATE_SELF'
                  )
WHERE r.code = 'CUSTOMER';