package io.pipeline.registration.events;

import io.pipeline.platform.registration.ModuleRegistered;
import io.pipeline.platform.registration.ServiceRegistered;
import io.pipeline.platform.registration.ModuleUnregistered;
import io.pipeline.platform.registration.ServiceUnregistered;
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

    private Timestamp createTimestamp() {
        long millis = System.currentTimeMillis();
        return Timestamp.newBuilder()
            .setSeconds(millis / 1000)
            .setNanos((int) ((millis % 1000) * 1_000_000))
            .build();
    }
}