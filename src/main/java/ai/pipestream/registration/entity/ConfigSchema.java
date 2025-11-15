package ai.pipestream.registration.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity representing a configuration schema for a service.
 * This is the system of record, with Apicurio Registry as a secondary store.
 */
@Entity
@Table(name = "config_schemas")
public class ConfigSchema extends PanacheEntityBase {

    /** Unique identifier for the schema */
    @Id
    @Column(name = "schema_id")
    public String schemaId;

    /** Name of the service this schema belongs to */
    @Column(name = "service_name", nullable = false)
    public String serviceName;

    /** Version of the schema */
    @Column(name = "schema_version", nullable = false)
    public String schemaVersion;

    /** The JSON schema definition */
    @Column(name = "json_schema", nullable = false, columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    public String jsonSchema;

    /** Timestamp when the schema was created */
    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    /** User or service that created the schema */
    @Column(name = "created_by")
    public String createdBy;

    /** Artifact ID in Apicurio Registry */
    @Column(name = "apicurio_artifact_id")
    public String apicurioArtifactId;

    /** Global ID in Apicurio Registry */
    @Column(name = "apicurio_global_id")
    public Long apicurioGlobalId;

    /** Synchronization status with Apicurio Registry */
    @Column(name = "sync_status")
    @Enumerated(EnumType.STRING)
    public SyncStatus syncStatus = SyncStatus.PENDING;

    /** Timestamp of the last synchronization attempt */
    @Column(name = "last_sync_attempt")
    public LocalDateTime lastSyncAttempt;

    /** Error message if synchronization failed */
    @Column(name = "sync_error")
    public String syncError;

    /**
     * Factory method to create a new ConfigSchema instance
     * @param serviceName The name of the service
     * @param version The schema version
     * @param jsonSchema The JSON schema definition
     * @return A new ConfigSchema with generated schema ID
     */
    public static ConfigSchema create(String serviceName, String version, String jsonSchema) {
        ConfigSchema schema = new ConfigSchema();
        schema.schemaId = generateSchemaId(serviceName, version);
        schema.serviceName = serviceName;
        schema.schemaVersion = version;
        schema.jsonSchema = jsonSchema;
        return schema;
    }

    /**
     * Generate a unique schema ID from service name and version
     * @param serviceName The name of the service
     * @param version The schema version (dots will be replaced with underscores)
     * @return A unique schema ID in the format "serviceName-vVersion"
     */
    public static String generateSchemaId(String serviceName, String version) {
        return String.format("%s-v%s", serviceName, version.replace(".", "_"));
    }

    /**
     * Mark the schema as successfully synced to Apicurio Registry
     * @param artifactId The Apicurio artifact ID
     * @param globalId The Apicurio global ID
     */
    public void markSynced(String artifactId, Long globalId) {
        this.apicurioArtifactId = artifactId;
        this.apicurioGlobalId = globalId;
        this.syncStatus = SyncStatus.SYNCED;
        this.lastSyncAttempt = LocalDateTime.now();
        this.syncError = null;
    }

    /**
     * Mark the schema synchronization as failed
     * @param error The error message describing the failure
     */
    public void markSyncFailed(String error) {
        this.syncStatus = SyncStatus.FAILED;
        this.lastSyncAttempt = LocalDateTime.now();
        this.syncError = error;
    }

    /**
     * Synchronization status with Apicurio Registry
     */
    public enum SyncStatus {
        /** Not yet synced to Apicurio */
        PENDING,
        /** Successfully synced */
        SYNCED,
        /** Sync failed */
        FAILED,
        /** Schema changed, needs re-sync */
        OUT_OF_SYNC
    }
}