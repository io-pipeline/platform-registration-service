package ai.pipestream.registration.handlers;

import ai.pipestream.data.module.RegistrationRequest;
import ai.pipestream.data.module.ServiceRegistrationMetadata;
import ai.pipestream.dynamic.grpc.client.DynamicGrpcClientFactory;
import ai.pipestream.platform.registration.GetModuleSchemaRequest;
import ai.pipestream.platform.registration.ModuleSchemaResponse;
import ai.pipestream.registration.entity.ConfigSchema;
import ai.pipestream.registration.repository.ApicurioRegistryClient;
import ai.pipestream.registration.repository.ModuleRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class SchemaRetrievalHandlerTest {

    @Inject
    SchemaRetrievalHandler schemaRetrievalHandler;

    @InjectMock
    ApicurioRegistryClient apicurioClient;

    @InjectMock
    ModuleRepository moduleRepository;

    @InjectMock
    DynamicGrpcClientFactory grpcClientFactory;

    @BeforeEach
    void setUp() {
        Mockito.reset(apicurioClient, moduleRepository, grpcClientFactory);
    }

    @Test
    void getModuleSchema_fromDatabase_success() {
        // Arrange
        String serviceName = "test-module";
        String version = "1.0.0";
        String jsonSchema = "{\"type\": \"object\", \"properties\": {\"key\": {\"type\": \"string\"}}}";

        ConfigSchema schema = ConfigSchema.create(serviceName, version, jsonSchema);
        schema.createdBy = "test-user";
        schema.createdAt = LocalDateTime.now();
        schema.apicurioArtifactId = "test-artifact-id";
        schema.syncStatus = ConfigSchema.SyncStatus.SYNCED;

        when(moduleRepository.findSchemaById(anyString()))
            .thenReturn(Uni.createFrom().item(schema));

        GetModuleSchemaRequest request = GetModuleSchemaRequest.newBuilder()
            .setModuleName(serviceName)
            .setVersion(version)
            .build();

        // Act
        ModuleSchemaResponse response = schemaRetrievalHandler.getModuleSchema(request)
            .await().indefinitely();

        // Assert
        assertNotNull(response);
        assertEquals(serviceName, response.getModuleName());
        assertEquals(jsonSchema, response.getSchemaJson());
        assertEquals(version, response.getSchemaVersion());
        assertEquals("test-artifact-id", response.getArtifactId());
        assertTrue(response.containsMetadata("created_by"));
        assertEquals("test-user", response.getMetadataOrThrow("created_by"));
        assertTrue(response.containsMetadata("sync_status"));
        assertEquals("SYNCED", response.getMetadataOrThrow("sync_status"));
        assertTrue(response.hasUpdatedAt());

        verify(moduleRepository).findSchemaById(anyString());
        verifyNoInteractions(apicurioClient, grpcClientFactory);
    }

    @Test
    void getModuleSchema_latestVersion_fromDatabase() {
        // Arrange
        String serviceName = "test-module";
        String jsonSchema = "{\"type\": \"object\"}";

        ConfigSchema schema = ConfigSchema.create(serviceName, "1.0.0", jsonSchema);
        schema.createdAt = LocalDateTime.now();

        when(moduleRepository.findLatestSchemaByServiceName(eq(serviceName)))
            .thenReturn(Uni.createFrom().item(schema));

        GetModuleSchemaRequest request = GetModuleSchemaRequest.newBuilder()
            .setModuleName(serviceName)
            .build(); // No version = latest

        // Act
        ModuleSchemaResponse response = schemaRetrievalHandler.getModuleSchema(request)
            .await().indefinitely();

        // Assert
        assertNotNull(response);
        assertEquals(serviceName, response.getModuleName());
        assertEquals(jsonSchema, response.getSchemaJson());
        
        verify(moduleRepository).findLatestSchemaByServiceName(eq(serviceName));
        verifyNoInteractions(apicurioClient, grpcClientFactory);
    }

    @Test
    void getModuleSchema_fallbackToApicurio_success() {
        // Arrange
        String serviceName = "test-module";
        String version = "1.0.0";
        String jsonSchema = "{\"type\": \"object\", \"properties\": {\"key\": {\"type\": \"string\"}}}";

        when(moduleRepository.findSchemaById(anyString()))
            .thenReturn(Uni.createFrom().nullItem());

        when(apicurioClient.getSchema(eq(serviceName), eq(version)))
            .thenReturn(Uni.createFrom().item(jsonSchema));

        when(apicurioClient.getArtifactMetadata(eq(serviceName)))
            .thenReturn(Uni.createFrom().nullItem());

        GetModuleSchemaRequest request = GetModuleSchemaRequest.newBuilder()
            .setModuleName(serviceName)
            .setVersion(version)
            .build();

        // Act
        ModuleSchemaResponse response = schemaRetrievalHandler.getModuleSchema(request)
            .await().indefinitely();

        // Assert
        assertNotNull(response);
        assertEquals(serviceName, response.getModuleName());
        assertEquals(jsonSchema, response.getSchemaJson());
        assertEquals(version, response.getSchemaVersion());

        verify(moduleRepository).findSchemaById(anyString());
        verify(apicurioClient).getSchema(eq(serviceName), eq(version));
        verify(apicurioClient).getArtifactMetadata(eq(serviceName));
    }

    @Test
    void getModuleSchema_fallbackToModule_success() {
        // Arrange
        String serviceName = "test-module";
        String jsonSchema = "{\"type\": \"object\"}";

        ServiceRegistrationMetadata metadata = ServiceRegistrationMetadata.newBuilder()
            .setModuleName(serviceName)
            .setVersion("1.0.0")
            .setJsonConfigSchema(jsonSchema)
            .setDisplayName("Test Module")
            .setDescription("Test Description")
            .build();

        when(moduleRepository.findLatestSchemaByServiceName(eq(serviceName)))
            .thenReturn(Uni.createFrom().nullItem());

        when(apicurioClient.getSchema(anyString(), anyString()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Not found in Apicurio")));

        // Mock the gRPC client chain
        var mockStub = mock(ai.pipestream.data.module.MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub.class);
        when(grpcClientFactory.getMutinyClientForService(eq(serviceName)))
            .thenReturn(Uni.createFrom().item(mockStub));
        when(mockStub.getServiceRegistration(any(RegistrationRequest.class)))
            .thenReturn(Uni.createFrom().item(metadata));

        GetModuleSchemaRequest request = GetModuleSchemaRequest.newBuilder()
            .setModuleName(serviceName)
            .build();

        // Act
        ModuleSchemaResponse response = schemaRetrievalHandler.getModuleSchema(request)
            .await().indefinitely();

        // Assert
        assertNotNull(response);
        assertEquals(serviceName, response.getModuleName());
        assertEquals(jsonSchema, response.getSchemaJson());
        assertEquals("1.0.0", response.getSchemaVersion());
        assertTrue(response.containsMetadata("source"));
        assertEquals("module-direct", response.getMetadataOrThrow("source"));
        assertTrue(response.containsMetadata("display_name"));
        assertEquals("Test Module", response.getMetadataOrThrow("display_name"));
        assertTrue(response.containsMetadata("description"));
        assertEquals("Test Description", response.getMetadataOrThrow("description"));
    }

    @Test
    void getModuleSchema_synthesizeSchema_whenModuleHasNoSchema() {
        // Arrange
        String serviceName = "test-module";

        ServiceRegistrationMetadata metadata = ServiceRegistrationMetadata.newBuilder()
            .setModuleName(serviceName)
            .setVersion("1.0.0")
            .build(); // No schema provided

        when(moduleRepository.findLatestSchemaByServiceName(eq(serviceName)))
            .thenReturn(Uni.createFrom().nullItem());

        when(apicurioClient.getSchema(anyString(), anyString()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Not found")));

        var mockStub = mock(ai.pipestream.data.module.MutinyPipeStepProcessorGrpc.MutinyPipeStepProcessorStub.class);
        when(grpcClientFactory.getMutinyClientForService(eq(serviceName)))
            .thenReturn(Uni.createFrom().item(mockStub));
        when(mockStub.getServiceRegistration(any(RegistrationRequest.class)))
            .thenReturn(Uni.createFrom().item(metadata));

        GetModuleSchemaRequest request = GetModuleSchemaRequest.newBuilder()
            .setModuleName(serviceName)
            .build();

        // Act
        ModuleSchemaResponse response = schemaRetrievalHandler.getModuleSchema(request)
            .await().indefinitely();

        // Assert
        assertNotNull(response);
        assertEquals(serviceName, response.getModuleName());
        assertTrue(response.getSchemaJson().contains("openapi"));
        assertTrue(response.getSchemaJson().contains(serviceName + " Configuration"));
    }

    @Test
    void getModuleSchema_notFound_throwsException() {
        // Arrange
        String serviceName = "non-existent-module";

        when(moduleRepository.findLatestSchemaByServiceName(eq(serviceName)))
            .thenReturn(Uni.createFrom().nullItem());

        when(apicurioClient.getSchema(anyString(), anyString()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Not found")));

        when(grpcClientFactory.getMutinyClientForService(eq(serviceName)))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Service not found")));

        GetModuleSchemaRequest request = GetModuleSchemaRequest.newBuilder()
            .setModuleName(serviceName)
            .build();

        // Act & Assert
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> {
            schemaRetrievalHandler.getModuleSchema(request).await().indefinitely();
        });

        assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode());
        assertTrue(exception.getStatus().getDescription().contains("Module schema not found"));
    }
}
