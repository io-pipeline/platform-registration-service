package io.pipeline.registration.entity;

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
    
    @Id
    @Column(name = "service_id")
    public String serviceId;
    
    @Column(name = "service_name", nullable = false)
    public String serviceName;
    
    @Column(nullable = false)
    public String host;
    
    @Column(nullable = false)
    public Integer port;
    
    @Column
    public String version;
    
    @Column(name = "config_schema_id")
    public String configSchemaId;
    
    @Column(columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "registered_at")
    public LocalDateTime registeredAt;
    
    @Column(name = "last_heartbeat")
    public LocalDateTime lastHeartbeat;
    
    @Column
    @Enumerated(EnumType.STRING)
    public ServiceStatus status = ServiceStatus.ACTIVE;
    
    // Convenience methods
    public static ServiceModule create(String serviceName, String host, int port) {
        ServiceModule module = new ServiceModule();
        module.serviceId = generateServiceId(serviceName, host, port);
        module.serviceName = serviceName;
        module.host = host;
        module.port = port;
        module.lastHeartbeat = LocalDateTime.now();
        return module;
    }
    
    public static String generateServiceId(String serviceName, String host, int port) {
        return String.format("%s-%s-%d", serviceName, host.replace(".", "-"), port);
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }
    
    public boolean isHealthy() {
        if (lastHeartbeat == null) return false;
        // Consider unhealthy if no heartbeat for 30 seconds
        return lastHeartbeat.isAfter(LocalDateTime.now().minusSeconds(30));
    }
}