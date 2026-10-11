CREATE TABLE roles (
   id UUID PRIMARY KEY,
   code VARCHAR(50) NOT NULL, --CUSTOMER/PROVIDER/ADMIN
   name VARCHAR(100) NOT NULL, --Customer/Court Provider/Administrator
   description VARCHAR(255),
   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
   CONSTRAINT uq_roles_code UNIQUE (code)
);