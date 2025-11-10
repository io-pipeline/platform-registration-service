package ai.pipestream.registration.handlers;

import com.google.protobuf.Timestamp;
import ai.pipestream.platform.registration.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles service discovery and lookup operations
 */
@ApplicationScoped
public class ServiceDiscoveryHandler {
    
    private static final Logger LOG = Logger.getLogger(ServiceDiscoveryHandler.class);
    
    @Inject
    ConsulClient consulClient;
    
    /**
     * List all services (non-modules)
     */
    public Uni<ServiceListResponse> listServices() {
        return consulClient.catalogServices()
            .flatMap(services -> {
                if (services == null || services.getList() == null || services.getList().isEmpty()) {
                    return Uni.createFrom().item(buildEmptyServiceList());
                }
                
                // Get health info for each service
                List<Uni<List<ServiceDetails>>> serviceUnis = services.getList().stream()
                    .map(service -> consulClient.healthServiceNodes(service.getName(), true)
                        .map(healthNodes -> {
                            if (healthNodes == null || healthNodes.getList() == null) {
                                return Collections.<ServiceDetails>emptyList();
                            }
                            return healthNodes.getList().stream()
                                .filter(entry -> !isModule(entry.getService().getTags()))
                                .map(this::convertToServiceDetails)
                                .collect(Collectors.toList());
                        })
                        .onFailure().recoverWithItem(Collections.<ServiceDetails>emptyList())
                    )
                    .collect(Collectors.toList());
                
                return Uni.join().all(serviceUnis).andCollectFailures()
                    .map(lists -> {
                        List<ServiceDetails> allServices = lists.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                        
                        return ServiceListResponse.newBuilder()
                            .addAllServices(allServices)
                            .setAsOf(createTimestamp())
                            .setTotalCount(allServices.size())
                            .build();
                    });
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Failed to list services from Consul", throwable);
                return buildEmptyServiceList();
            });
    }
    
    /**
     * List all modules
     */
    public Uni<ModuleListResponse> listModules() {
        return consulClient.catalogServices()
            .flatMap(services -> {
                if (services == null || services.getList() == null || services.getList().isEmpty()) {
                    return Uni.createFrom().item(buildEmptyModuleList());
                }
                
                // Get health info for each service that is a module
                List<Uni<List<ModuleDetails>>> moduleUnis = services.getList().stream()
                    .map(service -> consulClient.healthServiceNodes(service.getName(), true)
                        .map(healthNodes -> {
                            if (healthNodes == null || healthNodes.getList() == null) {
                                return Collections.<ModuleDetails>emptyList();
                            }
                            return healthNodes.getList().stream()
                                .filter(entry -> isModule(entry.getService().getTags()))
                                .map(this::convertToModuleDetails)
                                .collect(Collectors.toList());
                        })
                        .onFailure().recoverWithItem(Collections.<ModuleDetails>emptyList())
                    )
                    .collect(Collectors.toList());
                
                return Uni.join().all(moduleUnis).andCollectFailures()
                    .map(lists -> {
                        List<ModuleDetails> allModules = lists.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                        
                        return ModuleListResponse.newBuilder()
                            .addAllModules(allModules)
                            .setAsOf(createTimestamp())
                            .setTotalCount(allModules.size())
                            .build();
                    });
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Failed to list modules from Consul", throwable);
                return buildEmptyModuleList();
            });
    }
    
    /**
     * Get service by name (returns first healthy instance)
     */
    public Uni<ServiceDetails> getServiceByName(String serviceName) {
        return consulClient.healthServiceNodes(serviceName, true)
            .map(serviceEntries -> {
                if (serviceEntries == null || serviceEntries.getList() == null || serviceEntries.getList().isEmpty()) {
                    throw new io.grpc.StatusRuntimeException(
                        io.grpc.Status.NOT_FOUND.withDescription("Service not found: " + serviceName)
                    );
                }
                // Return first healthy instance
                return convertToServiceDetails(serviceEntries.getList().get(0));
            });
    }
    
    /**
     * Get service by ID
     */
    public Uni<ServiceDetails> getServiceById(String serviceId) {
        // Consul doesn't have a direct "get by ID" so we need to extract service name and search
        String serviceName = extractServiceNameFromId(serviceId);
        if (serviceName == null) {
            return Uni.createFrom().failure(new io.grpc.StatusRuntimeException(
                io.grpc.Status.INVALID_ARGUMENT.withDescription("Invalid service ID format: " + serviceId)
            ));
        }
        
        return consulClient.healthServiceNodes(serviceName, true)
            .map(serviceEntries -> {
                if (serviceEntries == null || serviceEntries.getList() == null) {
                    throw new io.grpc.StatusRuntimeException(
                        io.grpc.Status.NOT_FOUND.withDescription("Service not found: " + serviceId)
                    );
                }
                
                var entry = serviceEntries.getList().stream()
                    .filter(e -> serviceId.equals(e.getService().getId()))
                    .findFirst()
                    .orElseThrow(() -> new io.grpc.StatusRuntimeException(
                        io.grpc.Status.NOT_FOUND.withDescription("Service instance not found: " + serviceId)
                    ));
                
                return convertToServiceDetails(entry);
            });
    }
    
    /**
     * Get module by name
     */
    public Uni<ModuleDetails> getModuleByName(String moduleName) {
        return consulClient.healthServiceNodes(moduleName, true)
            .map(serviceEntries -> {
                if (serviceEntries == null || serviceEntries.getList() == null || serviceEntries.getList().isEmpty()) {
                    throw new io.grpc.StatusRuntimeException(
                        io.grpc.Status.NOT_FOUND.withDescription("Module not found: " + moduleName)
                    );
                }
                // Return first healthy instance that is tagged as module
                var moduleEntry = serviceEntries.getList().stream()
                    .filter(entry -> isModule(entry.getService().getTags()))
                    .findFirst()
                    .orElseThrow(() -> new io.grpc.StatusRuntimeException(
                        io.grpc.Status.NOT_FOUND.withDescription("Module not found: " + moduleName)
                    ));
                
                return convertToModuleDetails(moduleEntry);
            });
    }
    
    /**
     * Get module by ID
     */
    public Uni<ModuleDetails> getModuleById(String moduleId) {
        String moduleName = extractServiceNameFromId(moduleId);
        if (moduleName == null) {
            return Uni.createFrom().failure(new io.grpc.StatusRuntimeException(
                io.grpc.Status.INVALID_ARGUMENT.withDescription("Invalid module ID format: " + moduleId)
            ));
        }
        
        return consulClient.healthServiceNodes(moduleName, true)
            .map(serviceEntries -> {
                if (serviceEntries == null || serviceEntries.getList() == null) {
                    throw new io.grpc.StatusRuntimeException(
                        io.grpc.Status.NOT_FOUND.withDescription("Module not found: " + moduleId)
                    );
                }
                
                var entry = serviceEntries.getList().stream()
                    .filter(e -> moduleId.equals(e.getService().getId()) && isModule(e.getService().getTags()))
                    .findFirst()
                    .orElseThrow(() -> new io.grpc.StatusRuntimeException(
                        io.grpc.Status.NOT_FOUND.withDescription("Module instance not found: " + moduleId)
                    ));
                
                return convertToModuleDetails(entry);
            });
    }
    
    /**
     * Resolve service to find the best available instance
     */
    public Uni<ServiceResolveResponse> resolveService(ServiceResolveRequest request) {
        String serviceName = request.getServiceName();
        
        return consulClient.healthServiceNodes(serviceName, true)
            .map(serviceEntries -> {
                ServiceResolveResponse.Builder responseBuilder = ServiceResolveResponse.newBuilder()
                    .setServiceName(serviceName)
                    .setResolvedAt(createTimestamp());
                
                if (serviceEntries == null || serviceEntries.getList() == null || serviceEntries.getList().isEmpty()) {
                    // No healthy instances found
                    return responseBuilder
                        .setFound(false)
                        .setTotalInstances(0)
                        .setHealthyInstances(0)
                        .setSelectionReason("No healthy instances found")
                        .build();
                }
                
                List<io.vertx.ext.consul.ServiceEntry> healthyInstances = serviceEntries.getList();
                
                // Filter by required tags if specified
                if (!request.getRequiredTagsList().isEmpty()) {
                    healthyInstances = healthyInstances.stream()
                        .filter(entry -> {
                            List<String> serviceTags = entry.getService().getTags();
                            if (serviceTags == null) return false;
                            return serviceTags.containsAll(request.getRequiredTagsList());
                        })
                        .collect(Collectors.toList());
                }
                
                // Filter by required capabilities if specified
                if (!request.getRequiredCapabilitiesList().isEmpty()) {
                    healthyInstances = healthyInstances.stream()
                        .filter(entry -> {
                            List<String> serviceTags = entry.getService().getTags();
                            if (serviceTags == null) return false;
                            // Capabilities are stored as "capability:xxx" tags
                            Set<String> capabilities = serviceTags.stream()
                                .filter(tag -> tag.startsWith("capability:"))
                                .map(tag -> tag.substring("capability:".length()))
                                .collect(Collectors.toSet());
                            return capabilities.containsAll(request.getRequiredCapabilitiesList());
                        })
                        .collect(Collectors.toList());
                }
                
                if (healthyInstances.isEmpty()) {
                    // No instances match the criteria
                    return responseBuilder
                        .setFound(false)
                        .setTotalInstances(serviceEntries.getList().size())
                        .setHealthyInstances(serviceEntries.getList().size())
                        .setSelectionReason("No instances match the required criteria")
                        .build();
                }
                
                // Select the best instance
                io.vertx.ext.consul.ServiceEntry selectedInstance = null;
                String selectionReason = "";
                
                if (request.getPreferLocal()) {
                    // Try to find an instance on localhost first
                    Optional<io.vertx.ext.consul.ServiceEntry> localInstance = healthyInstances.stream()
                        .filter(entry -> "localhost".equals(entry.getService().getAddress()) || 
                                        "127.0.0.1".equals(entry.getService().getAddress()))
                        .findFirst();
                    
                    if (localInstance.isPresent()) {
                        selectedInstance = localInstance.get();
                        selectionReason = "Selected local instance as requested";
                    }
                }
                
                if (selectedInstance == null) {
                    // Use round-robin or random selection
                    // For now, just pick the first one (could be enhanced with load balancing)
                    selectedInstance = healthyInstances.get(0);
                    selectionReason = "Selected first available healthy instance";
                }
                
                var service = selectedInstance.getService();
                responseBuilder
                    .setFound(true)
                    .setHost(service.getAddress())
                    .setPort(service.getPort())
                    .setServiceId(service.getId())
                    .setTotalInstances(serviceEntries.getList().size())
                    .setHealthyInstances(healthyInstances.size())
                    .setSelectionReason(selectionReason);
                
                // Add metadata
                if (service.getMeta() != null) {
                    responseBuilder.putAllMetadata(service.getMeta());
                    if (service.getMeta().containsKey("version")) {
                        responseBuilder.setVersion(service.getMeta().get("version"));
                    }
                }
                
                // Add tags and capabilities
                if (service.getTags() != null) {
                    for (String tag : service.getTags()) {
                        if (tag.startsWith("capability:")) {
                            responseBuilder.addCapabilities(tag.substring("capability:".length()));
                        } else {
                            responseBuilder.addTags(tag);
                        }
                    }
                }
                
                return responseBuilder.build();
            })
            .onFailure().recoverWithItem(throwable -> {
                LOG.errorf(throwable, "Failed to resolve service: %s", serviceName);
                return ServiceResolveResponse.newBuilder()
                    .setFound(false)
                    .setServiceName(serviceName)
                    .setSelectionReason("Error resolving service: " + throwable.getMessage())
                    .setResolvedAt(createTimestamp())
                    .build();
            });
    }
    
    private ServiceDetails convertToServiceDetails(io.vertx.ext.consul.ServiceEntry entry) {
        var service = entry.getService();
        ServiceDetails.Builder builder = ServiceDetails.newBuilder()
            .setServiceId(service.getId())
            .setServiceName(service.getName())
            .setHost(service.getAddress())
            .setPort(service.getPort())
            .setIsHealthy(true); // Only healthy services are returned by default
        
        // Extract metadata
        if (service.getMeta() != null) {
            builder.putAllMetadata(service.getMeta());
            if (service.getMeta().containsKey("version")) {
                builder.setVersion(service.getMeta().get("version"));
            }
        }
        
        // Extract tags and capabilities
        if (service.getTags() != null) {
            for (String tag : service.getTags()) {
                if (tag.startsWith("capability:")) {
                    builder.addCapabilities(tag.substring("capability:".length()));
                } else {
                    builder.addTags(tag);
                }
            }
        }
        
        builder.setRegisteredAt(createTimestamp());
        builder.setLastHealthCheck(createTimestamp());
        
        return builder.build();
    }
    
    private ModuleDetails convertToModuleDetails(io.vertx.ext.consul.ServiceEntry entry) {
        var service = entry.getService();
        ModuleDetails.Builder builder = ModuleDetails.newBuilder()
            .setServiceId(service.getId())
            .setModuleName(service.getName())
            .setHost(service.getAddress())
            .setPort(service.getPort())
            .setIsHealthy(true);
        
        // Extract metadata
        if (service.getMeta() != null) {
            builder.putAllMetadata(service.getMeta());
            if (service.getMeta().containsKey("version")) {
                builder.setVersion(service.getMeta().get("version"));
            }
            // Extract module-specific fields from metadata
            if (service.getMeta().containsKey("input-format")) {
                builder.setInputFormat(service.getMeta().get("input-format"));
            }
            if (service.getMeta().containsKey("output-format")) {
                builder.setOutputFormat(service.getMeta().get("output-format"));
            }
        }
        
        builder.setRegisteredAt(createTimestamp());
        builder.setLastHealthCheck(createTimestamp());
        
        return builder.build();
    }
    
    private boolean isModule(List<String> tags) {
        return tags != null && tags.contains("module");
    }
    
    private String extractServiceNameFromId(String serviceId) {
        // Format: serviceName-host-port
        int lastDash = serviceId.lastIndexOf('-');
        if (lastDash == -1) return null;
        
        String withoutPort = serviceId.substring(0, lastDash);
        int secondLastDash = withoutPort.lastIndexOf('-');
        if (secondLastDash == -1) return null;
        
        return withoutPort.substring(0, secondLastDash);
    }
    
    private ServiceListResponse buildEmptyServiceList() {
        return ServiceListResponse.newBuilder()
            .setAsOf(createTimestamp())
            .setTotalCount(0)
            .build();
    }
    
    private ModuleListResponse buildEmptyModuleList() {
        return ModuleListResponse.newBuilder()
            .setAsOf(createTimestamp())
            .setTotalCount(0)
            .build();
    }
    
    private Timestamp createTimestamp() {
        long millis = System.currentTimeMillis();
        return Timestamp.newBuilder()
            .setSeconds(millis / 1000)
            .setNanos((int) ((millis % 1000) * 1_000_000))
            .build();
    }
    
    /**
     * Watch for real-time updates to the list of all healthy services.
     * Sends an initial list immediately, then sends updates whenever services change.
     */
    public Multi<ServiceListResponse> watchServices() {
        LOG.info("Starting service watch stream");

        // Send initial list immediately
        Multi<ServiceListResponse> initialList = Multi.createFrom().uni(listServices())
            .onItem().invoke(response ->
                LOG.infof("Sending initial service list with %d services", response.getTotalCount())
            );

        // Then poll for changes every 2 seconds
        Multi<ServiceListResponse> updates = Multi.createFrom().ticks().every(Duration.ofSeconds(2))
            .onItem().transformToUniAndConcatenate(tick -> listServices())
            .onItem().invoke(response ->
                LOG.debugf("Service watch update: %d services", response.getTotalCount())
            )
            .onFailure().invoke(throwable ->
                LOG.error("Error during service watch", throwable)
            )
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Recovering from error in service watch", throwable);
                return buildEmptyServiceList();
            });

        // Combine initial list with ongoing updates
        return Multi.createBy().concatenating()
            .streams(initialList, updates)
            .onCompletion().invoke(() -> LOG.info("Service watch stream completed"))
            .onCancellation().invoke(() -> LOG.info("Service watch stream cancelled by client"));
    }

    /**
     * Watch for real-time updates to the list of all registered modules.
     * Sends an initial list immediately, then sends updates whenever modules change.
     */
    public Multi<ModuleListResponse> watchModules() {
        LOG.info("Starting module watch stream");

        // Send initial list immediately
        Multi<ModuleListResponse> initialList = Multi.createFrom().uni(listModules())
            .onItem().invoke(response ->
                LOG.infof("Sending initial module list with %d modules", response.getTotalCount())
            );

        // Then poll for changes every 2 seconds
        Multi<ModuleListResponse> updates = Multi.createFrom().ticks().every(Duration.ofSeconds(2))
            .onItem().transformToUniAndConcatenate(tick -> listModules())
            .onItem().invoke(response ->
                LOG.debugf("Module watch update: %d modules", response.getTotalCount())
            )
            .onFailure().invoke(throwable ->
                LOG.error("Error during module watch", throwable)
            )
            .onFailure().recoverWithItem(throwable -> {
                LOG.error("Recovering from error in module watch", throwable);
                return buildEmptyModuleList();
            });

        // Combine initial list with ongoing updates
        return Multi.createBy().concatenating()
            .streams(initialList, updates)
            .onCompletion().invoke(() -> LOG.info("Module watch stream completed"))
            .onCancellation().invoke(() -> LOG.info("Module watch stream cancelled by client"));
    }
}