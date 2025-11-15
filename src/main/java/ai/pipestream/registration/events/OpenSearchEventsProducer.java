package ai.pipestream.registration.events;

import ai.pipestream.platform.registration.ModuleRegistered;
import ai.pipestream.platform.registration.ServiceRegistered;
import ai.pipestream.platform.registration.ModuleUnregistered;
import ai.pipestream.platform.registration.ServiceUnregistered;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import io.smallrye.reactive.messaging.MutinyEmitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import com.google.protobuf.Timestamp;

import java.util.UUID;

/**
 * Produces events to Kafka for OpenSearch indexing.
 * Only emits on successful registration/unregistration.
 * Key: UUID
 * Value: Protobuf events
 */
@ApplicationScoped
public class OpenSearchEventsProducer {

    private static final Logger LOG = Logger.getLogger(OpenSearchEventsProducer.class);

    @Channel("opensearch-service-registered-events")
    MutinyEmitter<ServiceRegistered> serviceRegisteredEmitter;

    @Channel("opensearch-service-unregistered-events")
    MutinyEmitter<ServiceUnregistered> serviceUnregisteredEmitter;

    @Channel("opensearch-module-registered-events")
    MutinyEmitter<ModuleRegistered> moduleRegisteredEmitter;

    @Channel("opensearch-module-unregistered-events")
    MutinyEmitter<ModuleUnregistered> moduleUnregisteredEmitter;

    /**
     * Emit a service registered event to Kafka for OpenSearch indexing
     * @param serviceId The unique service ID
     * @param serviceName The name of the service
     * @param host The host address
     * @param port The port number
     * @param version The service version
     */
    public void emitServiceRegistered(String serviceId, String serviceName, String host, int port, String version) {
        try {
            ServiceRegistered event = ServiceRegistered.newBuilder()
                .setServiceId(serviceId)
                .setServiceName(serviceName)
                .setHost(host)
                .setPort(port)
                .setVersion(version)
                .setTimestamp(createTimestamp())
                .build();
            
            UUID key = UUID.randomUUID();
            OutgoingKafkaRecordMetadata<UUID> metadata = OutgoingKafkaRecordMetadata.<UUID>builder()
                .withKey(key)
                .build();
            
            serviceRegisteredEmitter.sendMessageAndForget(Message.of(event).addMetadata(metadata));
            LOG.debugf("Emitted ServiceRegistered event for OpenSearch: serviceId=%s, key=%s", serviceId, key);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit ServiceRegistered event for OpenSearch: %s", serviceId);
        }
    }

    /**
     * Emit a service unregistered event to Kafka for OpenSearch indexing
     * @param serviceId The unique service ID
     * @param serviceName The name of the service
     */
    public void emitServiceUnregistered(String serviceId, String serviceName) {
        try {
            ServiceUnregistered event = ServiceUnregistered.newBuilder()
                .setServiceId(serviceId)
                .setServiceName(serviceName)
                .setTimestamp(createTimestamp())
                .build();
            
            UUID key = UUID.randomUUID();
            OutgoingKafkaRecordMetadata<UUID> metadata = OutgoingKafkaRecordMetadata.<UUID>builder()
                .withKey(key)
                .build();
            
            serviceUnregisteredEmitter.sendMessageAndForget(Message.of(event).addMetadata(metadata));
            LOG.debugf("Emitted ServiceUnregistered event for OpenSearch: serviceId=%s, key=%s", serviceId, key);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit ServiceUnregistered event for OpenSearch: %s", serviceId);
        }
    }

    /**
     * Emit a module registered event to Kafka for OpenSearch indexing
     * @param serviceId The unique service ID
     * @param moduleName The name of the module
     * @param host The host address
     * @param port The port number
     * @param version The module version
     * @param schemaId The schema ID
     * @param apicurioArtifactId The Apicurio artifact ID
     */
    public void emitModuleRegistered(String serviceId, String moduleName, String host, int port,
                                    String version, String schemaId, String apicurioArtifactId) {
        try {
            ModuleRegistered event = ModuleRegistered.newBuilder()
                .setServiceId(serviceId)
                .setModuleName(moduleName)
                .setHost(host)
                .setPort(port)
                .setVersion(version)
                .setSchemaId(schemaId)
                .setApicurioArtifactId(apicurioArtifactId)
                .setTimestamp(createTimestamp())
                .build();
            
            UUID key = UUID.randomUUID();
            OutgoingKafkaRecordMetadata<UUID> metadata = OutgoingKafkaRecordMetadata.<UUID>builder()
                .withKey(key)
                .build();
            
            moduleRegisteredEmitter.sendMessageAndForget(Message.of(event).addMetadata(metadata));
            LOG.debugf("Emitted ModuleRegistered event for OpenSearch: serviceId=%s, key=%s", serviceId, key);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit ModuleRegistered event for OpenSearch: %s", serviceId);
        }
    }

    /**
     * Emit a module unregistered event to Kafka for OpenSearch indexing
     * @param serviceId The unique service ID
     * @param moduleName The name of the module
     */
    public void emitModuleUnregistered(String serviceId, String moduleName) {
        try {
            ModuleUnregistered event = ModuleUnregistered.newBuilder()
                .setServiceId(serviceId)
                .setModuleName(moduleName)
                .setTimestamp(createTimestamp())
                .build();
            
            UUID key = UUID.randomUUID();
            OutgoingKafkaRecordMetadata<UUID> metadata = OutgoingKafkaRecordMetadata.<UUID>builder()
                .withKey(key)
                .build();
            
            moduleUnregisteredEmitter.sendMessageAndForget(Message.of(event).addMetadata(metadata));
            LOG.debugf("Emitted ModuleUnregistered event for OpenSearch: serviceId=%s, key=%s", serviceId, key);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit ModuleUnregistered event for OpenSearch: %s", serviceId);
        }
    }

    /**
     * Create a Protobuf timestamp from current system time
     * @return Protobuf Timestamp representing current time
     */
    private Timestamp createTimestamp() {
        long millis = System.currentTimeMillis();
        return Timestamp.newBuilder()
            .setSeconds(millis / 1000)
            .setNanos((int) ((millis % 1000) * 1_000_000))
            .build();
    }
}