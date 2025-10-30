-- Development initialization script
-- This runs when the dev services container starts

-- Create the database if it doesn't exist
SELECT 'CREATE DATABASE pipeline_registry'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pipeline_registry');