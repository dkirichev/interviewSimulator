-- V3__create_admin_and_cleanup.sql
-- Admin user table and default admin account

CREATE TABLE IF NOT EXISTS admin_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Default admin account (password: BCrypt hashed)
INSERT INTO admin_users (id, username, password_hash)
VALUES (
    gen_random_uuid(),
    'admin',
    '$2a$12$uY43Bd496SLgyiPoUiRwLe.N0udxJYEVKJBZhhW/KYvyLbMvAl81e'
);
