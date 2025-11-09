package ai.pipestream.registration.consul;

import ai.pipestream.platform.registration.ServiceRegistrationRequest;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.consul.CheckOptions;
import io.vertx.ext.consul.ServiceOptions;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Handles service registration and unregistration with Consul
 */
@ApplicationScoped
public class ConsulRegistrar {
    
    private static final Logger LOG = Logger.getLogger(ConsulRegistrar.class);
    
    @Inject
    ConsulClient consulClient;
    
    /**
     * Register a service with Consul including health check configuration
     */
    public Uni<Boolean> registerService(ServiceRegistrationRequest request, String serviceId) {
        ServiceOptions serviceOptions = new ServiceOptions()
            .setId(serviceId)
            .setName(request.getServiceName())
            .setAddress(request.getHost())
            .setPort(request.getPort())
            .setTags(new ArrayList<>(request.getTagsList()))
            .setMeta(new HashMap<>(request.getMetadataMap()));
        
        // Add version to metadata
        serviceOptions.getMeta().put("version", request.getVersion());
        
        // Add capabilities as tags with prefix
        request.getCapabilitiesList().forEach(cap -> 
            serviceOptions.getTags().add("capability:" + cap)
        );
        
        // Configure gRPC health check (same port)
        CheckOptions checkOptions = new CheckOptions()
            .setName(request.getServiceName() + " gRPC Health Check")
            .setGrpc(request.getHost() + ":" + request.getPort())
            .setInterval("10s")
            .setDeregisterAfter("1m");
        
        serviceOptions.setCheckOptions(checkOptions);
        
        LOG.infof("Registering service with Consul: %s", serviceId);
        
        return consulClient.registerService(serviceOptions)
            .map(v -> {
                LOG.infof("Successfully registered service: %s", serviceId);
                return true;
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to register service: %s", serviceId);
                return false;
            });
    }
    
    /**
     * Unregister a service from Consul
     */
    public Uni<Boolean> unregisterService(String serviceId) {
        LOG.infof("Unregistering service from Consul: %s", serviceId);
        
        return consulClient.deregisterService(serviceId)
            .map(v -> {
                LOG.infof("Successfully unregistered service: %s", serviceId);
                return true;
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to unregister service: %s", serviceId);
                return false;
            });
    }
    
    /**
     * Generate a consistent service ID from service details
     */
    public static String generateServiceId(String serviceName, String host, int port) {
        return String.format("%s-%s-%d", serviceName, host, port);
    }
}