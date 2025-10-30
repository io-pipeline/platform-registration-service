package io.pipeline.registration.grpc;

import io.pipeline.platform.registration.*;
import io.pipeline.registration.handlers.ServiceRegistrationHandler;
import io.pipeline.registration.handlers.ModuleRegistrationHandler;
import io.pipeline.registration.handlers.ServiceDiscoveryHandler;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import com.google.protobuf.Empty;

/**
 * Main platform registration service implementation
 */
@GrpcService
public class PlatformRegistrationService extends MutinyPlatformRegistrationGrpc.PlatformRegistrationImplBase {
    
    private static final Logger LOG = Logger.getLogger(PlatformRegistrationService.class);
    
    @Inject
    ServiceRegistrationHandler serviceRegistrationHandler;
    
    @Inject
    ModuleRegistrationHandler moduleRegistrationHandler;
    
    @Inject
    ServiceDiscoveryHandler discoveryHandler;

    @Override
    public Multi<RegistrationEvent> registerService(ServiceRegistrationRequest request) {
        LOG.infof("Received service registration request for: %s", request.getServiceName());
        return serviceRegistrationHandler.registerService(request);
    }
    
    @Blocking
    @Override
    public Multi<RegistrationEvent> registerModule(ModuleRegistrationRequest request) {
        LOG.infof("Received module registration request for: %s", request.getModuleName());
        return moduleRegistrationHandler.registerModule(request);
    }
    
    @Override
    public Uni<UnregisterResponse> unregisterService(UnregisterRequest request) {
        LOG.infof("Received service unregistration request for: %s at %s:%d", 
                 request.getServiceName(), request.getHost(), request.getPort());
        
        return serviceRegistrationHandler.unregisterService(request);
    }
    
    @Override
    public Uni<UnregisterResponse> unregisterModule(UnregisterRequest request) {
        LOG.infof("Received module unregistration request for: %s at %s:%d", 
                 request.getServiceName(), request.getHost(), request.getPort());
        
        // Use module-specific unregistration to emit module events
        return moduleRegistrationHandler.unregisterModule(request);
    }
    
    @Override
    public Uni<ServiceListResponse> listServices(Empty request) {
        LOG.debug("Received request to list all services");
        return discoveryHandler.listServices();
    }
    
    @Override
    public Uni<ModuleListResponse> listModules(Empty request) {
        LOG.debug("Received request to list all modules");
        return discoveryHandler.listModules();
    }
    
    @Override
    public Uni<ServiceDetails> getService(ServiceLookupRequest request) {
        if (request.hasServiceName()) {
            LOG.debugf("Looking up service by name: %s", request.getServiceName());
            return discoveryHandler.getServiceByName(request.getServiceName());
        } else if (request.hasServiceId()) {
            LOG.debugf("Looking up service by ID: %s", request.getServiceId());
            return discoveryHandler.getServiceById(request.getServiceId());
        } else {
            return Uni.createFrom().failure(new IllegalArgumentException("Must provide service name or ID"));
        }
    }
    
    @Override
    public Uni<ModuleDetails> getModule(ServiceLookupRequest request) {
        if (request.hasServiceName()) {
            LOG.debugf("Looking up module by name: %s", request.getServiceName());
            return discoveryHandler.getModuleByName(request.getServiceName());
        } else if (request.hasServiceId()) {
            LOG.debugf("Looking up module by ID: %s", request.getServiceId());
            return discoveryHandler.getModuleById(request.getServiceId());
        } else {
            return Uni.createFrom().failure(new IllegalArgumentException("Must provide module name or ID"));
        }
    }

    @Override
    public Uni<ServiceResolveResponse> resolveService(ServiceResolveRequest request) {
        LOG.infof("Resolving service: %s (prefer_local=%s, tags=%s, capabilities=%s)",
                 request.getServiceName(),
                 request.getPreferLocal(),
                 request.getRequiredTagsList(),
                 request.getRequiredCapabilitiesList());

        return discoveryHandler.resolveService(request);
    }

    @Override
    public Multi<ServiceListResponse> watchServices(Empty request) {
        LOG.info("Received request to watch services for real-time updates");
        return discoveryHandler.watchServices();
    }
}
