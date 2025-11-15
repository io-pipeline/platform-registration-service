package ai.pipestream.registration.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a registered service module in the system.
 * This is the system of record for all service registrations.
 */
@Entity
@Table(name = "modules")
public class ServiceModule extends PanacheEntityBase {

    /** Unique identifier for the service instance */
    @Id
    @Column(name = "service_id")
    public String serviceId;

    /** Name of the service */
    @Column(name = "service_name", nullable = false)
    public String serviceName;

    /** Host address where the service is running */
    @Column(nullable = false)
    public String host;

    /** Port number where the service is listening */
    @Column(nullable = false)
    public Integer port;

    /** Version of the service */
    @Column
    public String version;

    /** Reference to the configuration schema ID */
    @Column(name = "config_schema_id")
    public String configSchemaId;

    /** Additional metadata about the service stored as JSON */
    @Column(columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> metadata = new HashMap<>();

    /** Timestamp when the service was first registered */
    @CreationTimestamp
    @Column(name = "registered_at")
    public LocalDateTime registeredAt;

    /** Timestamp of the last heartbeat received from the service */
    @Column(name = "last_heartbeat")
    public LocalDateTime lastHeartbeat;

    /** Current status of the service */
    @Column
    @Enumerated(EnumType.STRING)
    public ServiceStatus status = ServiceStatus.ACTIVE;

    /**
     * Factory method to create a new ServiceModule instance
     * @param serviceName The name of the service
     * @param host The host address
     * @param port The port number
     * @return A new ServiceModule with generated service ID and initialized heartbeat
     */
    public static ServiceModule create(String serviceName, String host, int port) {
        ServiceModule module = new ServiceModule();
        module.serviceId = generateServiceId(serviceName, host, port);
        module.serviceName = serviceName;
        module.host = host;
        module.port = port;
        module.lastHeartbeat = LocalDateTime.now();
        return module;
    }

    /**
     * Generate a unique service ID from service details
     * @param serviceName The name of the service
     * @param host The host address (dots will be replaced with dashes)
     * @param port The port number
     * @return A unique service ID in the format "serviceName-host-port"
     */
    public static String generateServiceId(String serviceName, String host, int port) {
        return String.format("%s-%s-%d", serviceName, host.replace(".", "-"), port);
    }

    /**
     * Update the last heartbeat timestamp to current time
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }

    /**
     * Check if the service is considered healthy based on recent heartbeat
     * @return true if service has heartbeat within last 30 seconds, false otherwise
     */
    public boolean isHealthy() {
        if (lastHeartbeat == null) return false;
        // Consider unhealthy if no heartbeat for 30 seconds
        return lastHeartbeat.isAfter(LocalDateTime.now().minusSeconds(30));
    }
}