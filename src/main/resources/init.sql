CREATE DATABASE pipeline_registry;
CREATE USER registration_user WITH PASSWORD 'password';
GRANT ALL PRIVILEGES ON DATABASE pipeline_registry TO registration_user;
