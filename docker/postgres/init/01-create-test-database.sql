SELECT 'CREATE DATABASE sports_booking_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'sports_booking_test')\gexec
