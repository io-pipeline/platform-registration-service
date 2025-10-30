package io.pipeline.registration.handlers;

import com.google.protobuf.Timestamp;
import io.pipeline.platform.registration.*;
import io.pipeline.registration.consul.ConsulHealthChecker;
import io.pipeline.registration.consul.ConsulRegistrar;
import io.pipeline.registration.events.OpenSearchEventsProducer;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Handles service registration operations
 */
@ApplicationScoped
public class ServiceRegistrationHandler {
    
    private static final Logger LOG = Logger.getLogger(ServiceRegistrationHandler.class);
    
    @Inject
    ConsulRegistrar consulRegistrar;
    
    @Inject
    ConsulHealthChecker healthChecker;
    
    @Inject
    OpenSearchEventsProducer openSearchProducer;
    
    /**
     * Register a service with streaming status updates
     */
    public Multi<RegistrationEvent> registerService(ServiceRegistrationRequest request) {
        String serviceId = ConsulRegistrar.generateServiceId(request.getServiceName(), request.getHost(), request.getPort());
        
        return Multi.createFrom().emitter(emitter -> {
            // Emit STARTED event
            emitter.emit(createEvent(EventType.STARTED, "Starting service registration", serviceId));
            
            // Validate request
            if (!validateServiceRequest(request)) {
                RegistrationEvent failed = createEventWithError(serviceId, "Invalid service registration request", "Missing required fields");
                emitter.emit(failed);
                emitter.complete();
                return;
            }
            
            emitter.emit(createEvent(EventType.VALIDATED, "Service registration request validated", null));
            
            // Register with Consul
            consulRegistrar.registerService(request, serviceId)
                .onItem().transformToUni(success -> {
                    if (!success) {
                        RegistrationEvent failed = createEventWithError(serviceId, "Failed to register with Consul", "Consul registration returned false");
                        emitter.emit(failed);
                        emitter.complete();
                        return Uni.createFrom().voidItem();
                    }
                    
                    emitter.emit(createEvent(EventType.CONSUL_REGISTERED, "Service registered with Consul", serviceId));
                    emitter.emit(createEvent(EventType.HEALTH_CHECK_CONFIGURED, "Health check configured", null));
                    
                    // Wait for health check
                    return healthChecker.waitForHealthy(serviceId)
                        .onItem().invoke(healthy -> {
                            if (healthy) {
                                emitter.emit(createEvent(EventType.CONSUL_HEALTHY, "Service reported healthy by Consul", null));
                                RegistrationEvent completed = createEvent(EventType.COMPLETED, "Service registration completed successfully", serviceId);
                                emitter.emit(completed);
                                
                                // Emit to OpenSearch on success
                                openSearchProducer.emitServiceRegistered(serviceId, request.getServiceName(), 
                                    request.getHost(), request.getPort(), request.getVersion());
                            } else {
                                // Service registered but never became healthy
                                RegistrationEvent failed = createEventWithError(serviceId, "Service registered but failed health checks",
                                    "Service did not become healthy within timeout period. Check service logs and connectivity.");
                                emitter.emit(failed);
                                
                                // Cleanup - unregister from Consul
                                consulRegistrar.unregisterService(serviceId)
                                    .subscribe().with(
                                        cleanup -> LOG.debugf("Cleaned up unhealthy service registration: %s", serviceId),
                                        error -> LOG.errorf(error, "Failed to cleanup unhealthy service: %s", serviceId)
                                    );
                            }
                            emitter.complete();
                        });
                })
                .subscribe().with(
                    result -> {}, // Success handled above
                    error -> {
                        LOG.error("Failed to register service", error);
                        RegistrationEvent failed = createEventWithError(serviceId, "Registration failed", error.getMessage());
                        emitter.emit(failed);
                        emitter.complete();
                    }
                );
        });
    }
    
    /**
     * Unregister a service
     */
    public Uni<UnregisterResponse> unregisterService(UnregisterRequest request) {
        String serviceId = ConsulRegistrar.generateServiceId(request.getServiceName(), request.getHost(), request.getPort());
        
        return consulRegistrar.unregisterService(serviceId)
            .map(success -> {
                UnregisterResponse.Builder response = UnregisterResponse.newBuilder()
                    .setSuccess(success)
                    .setTimestamp(createTimestamp());
                
                if (success) {
                    response.setMessage("Service unregistered successfully");
                    // Emit to OpenSearch
                    openSearchProducer.emitServiceUnregistered(serviceId, request.getServiceName());
                } else {
                    response.setMessage("Failed to unregister service");
                }
                
                return response.build();
            });
    }
    
    private boolean validateServiceRequest(ServiceRegistrationRequest request) {
        return !request.getServiceName().isEmpty() && 
               !request.getHost().isEmpty() && 
               request.getPort() > 0;
    }
    
    private RegistrationEvent createEvent(EventType type, String message, String serviceId) {
        RegistrationEvent.Builder builder = RegistrationEvent.newBuilder()
            .setEventType(type)
            .setMessage(message)
            .setTimestamp(createTimestamp());
        
        if (serviceId != null) {
            builder.setServiceId(serviceId);
        }
        
        return builder.build();
    }
    
    private RegistrationEvent createEventWithError(String serviceId, String message, String errorDetail) {
        RegistrationEvent.Builder builder = RegistrationEvent.newBuilder()
            .setEventType(EventType.FAILED)
            .setMessage(message)
            .setErrorDetail(errorDetail)
            .setTimestamp(createTimestamp());
        
        if (serviceId != null) {
            builder.setServiceId(serviceId);
        }
        
        return builder.build();
    }
    
    private Timestamp createTimestamp() {
        long millis = System.currentTimeMillis();
        return Timestamp.newBuilder()
            .setSeconds(millis / 1000)
            .setNanos((int) ((millis % 1000) * 1_000_000))
            .build();
    }
}