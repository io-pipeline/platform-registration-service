-- Module registrations table (final schema)
CREATE TABLE modules (
    service_id VARCHAR(255) PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    version VARCHAR(100),
    config_schema_id VARCHAR(255),
    metadata JSON,
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_heartbeat DATETIME(6),
    status ENUM('ACTIVE','INACTIVE','MAINTENANCE','UNHEALTHY') NOT NULL DEFAULT 'ACTIVE'
);

-- Schema versions table with Apicurio sync columns (final schema)
CREATE TABLE config_schemas (
    schema_id VARCHAR(255) PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    schema_version VARCHAR(100) NOT NULL,
    json_schema JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(255),
    -- Apicurio integration
    apicurio_artifact_id VARCHAR(255),
    apicurio_global_id BIGINT,
    sync_status ENUM('FAILED','OUT_OF_SYNC','PENDING','SYNCED') NOT NULL DEFAULT 'PENDING',
    last_sync_attempt DATETIME(6),
    sync_error VARCHAR(255),
    CONSTRAINT unique_service_schema_version UNIQUE(service_name, schema_version)
);

-- Indexes for efficient lookups
CREATE INDEX idx_modules_service_name ON modules(service_name);
CREATE INDEX idx_modules_status ON modules(status);
CREATE INDEX idx_schemas_service_name ON config_schemas(service_name);
CREATE INDEX idx_schemas_sync_status ON config_schemas(sync_status);
