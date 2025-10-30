package io.pipeline.registration.consul;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Handles health check operations for services registered with Consul
 */
@ApplicationScoped
public class ConsulHealthChecker {
    
    private static final Logger LOG = Logger.getLogger(ConsulHealthChecker.class);
    private static final int MAX_HEALTH_CHECK_ATTEMPTS = 10;
    private static final int BASE_DELAY_SECONDS = 3;
    private static final int MAX_DELAY_SECONDS = 10;
    
    @Inject
    ConsulClient consulClient;
    
    /**
     * Wait for a service to become healthy in Consul's view
     * @param serviceId The full service ID (serviceName-host-port)
     * @return Uni<Boolean> true if service becomes healthy, false if timeout
     */
    public Uni<Boolean> waitForHealthy(String serviceId) {
        String serviceName = extractServiceName(serviceId);
        if (serviceName == null) {
            LOG.errorf("Invalid serviceId format: %s", serviceId);
            return Uni.createFrom().item(false);
        }
        
        LOG.debugf("Waiting for health check - serviceId: %s, serviceName: %s", serviceId, serviceName);
        return checkHealthWithRetry(serviceName, serviceId, 0);
    }
    
    private Uni<Boolean> checkHealthWithRetry(String serviceName, String serviceId, int attempt) {
        if (attempt >= MAX_HEALTH_CHECK_ATTEMPTS) {
            LOG.warnf("Service %s did not become healthy after %d attempts", serviceId, MAX_HEALTH_CHECK_ATTEMPTS);
            return Uni.createFrom().item(false);
        }
        
        return consulClient.healthServiceNodes(serviceName, true)
            .onItem().transform(serviceEntries -> {
                if (serviceEntries != null && serviceEntries.getList() != null) {
                    boolean isHealthy = serviceEntries.getList().stream()
                        .anyMatch(entry -> serviceId.equals(entry.getService().getId()));
                    if (isHealthy) {
                        LOG.infof("Service %s is now healthy in Consul", serviceId);
                        return true;
                    }
                }
                return false;
            })
            .onItem().transformToUni(healthy -> {
                if (healthy) {
                    return Uni.createFrom().item(true);
                }
                
                long delaySec = Math.min(BASE_DELAY_SECONDS + attempt, MAX_DELAY_SECONDS);
                LOG.debugf("Service %s not healthy yet; scheduling next check in %ds (attempt %d/%d)", 
                    serviceId, delaySec, attempt + 1, MAX_HEALTH_CHECK_ATTEMPTS);
                
                return Uni.createFrom().item(false)
                    .onItem().delayIt().by(Duration.ofSeconds(delaySec))
                    .onItem().transformToUni(ignored -> checkHealthWithRetry(serviceName, serviceId, attempt + 1));
            })
            .onFailure().recoverWithUni(throwable -> {
                long delaySec = Math.min(BASE_DELAY_SECONDS + attempt, MAX_DELAY_SECONDS);
                LOG.warnf("Error checking health for %s: %s; retrying in %ds", 
                    serviceId, throwable.getMessage(), delaySec);
                
                return Uni.createFrom().item(false)
                    .onItem().delayIt().by(Duration.ofSeconds(delaySec))
                    .onItem().transformToUni(ignored -> checkHealthWithRetry(serviceName, serviceId, attempt + 1));
            });
    }
    
    /**
     * Extract service name from serviceId (format: serviceName-host-port)
     */
    private String extractServiceName(String serviceId) {
        int lastDash = serviceId.lastIndexOf('-');
        if (lastDash == -1) {
            return null;
        }
        
        String withoutPort = serviceId.substring(0, lastDash);
        int secondLastDash = withoutPort.lastIndexOf('-');
        if (secondLastDash == -1) {
            return null;
        }
        
        return withoutPort.substring(0, secondLastDash);
    }
}