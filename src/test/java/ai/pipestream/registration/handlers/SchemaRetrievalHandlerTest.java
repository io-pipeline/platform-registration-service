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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Module name should match the requested service name",
            response.getModuleName(), is(equalTo(serviceName)));
        assertThat("Schema JSON should match the stored schema",
            response.getSchemaJson(), is(equalTo(jsonSchema)));
        assertThat("Schema version should match the requested version",
            response.getSchemaVersion(), is(equalTo(version)));
        assertThat("Artifact ID should match the stored artifact ID",
            response.getArtifactId(), is(equalTo("test-artifact-id")));
        assertThat("Response should contain created_by metadata",
            response.containsMetadata("created_by"), is(true));
        assertThat("Created by metadata should match the stored value",
            response.getMetadataOrThrow("created_by"), is(equalTo("test-user")));
        assertThat("Response should contain sync_status metadata",
            response.containsMetadata("sync_status"), is(true));
        assertThat("Sync status should be SYNCED",
            response.getMetadataOrThrow("sync_status"), is(equalTo("SYNCED")));
        assertThat("Response should have updatedAt timestamp",
            response.hasUpdatedAt(), is(true));

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
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Module name should match the requested service name",
            response.getModuleName(), is(equalTo(serviceName)));
        assertThat("Schema JSON should match the latest schema",
            response.getSchemaJson(), is(equalTo(jsonSchema)));

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
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Module name should match the requested service name",
            response.getModuleName(), is(equalTo(serviceName)));
        assertThat("Schema JSON should match the schema from Apicurio",
            response.getSchemaJson(), is(equalTo(jsonSchema)));
        assertThat("Schema version should match the requested version",
            response.getSchemaVersion(), is(equalTo(version)));

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
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Module name should match the requested service name",
            response.getModuleName(), is(equalTo(serviceName)));
        assertThat("Schema JSON should match the schema from module",
            response.getSchemaJson(), is(equalTo(jsonSchema)));
        assertThat("Schema version should match the module version",
            response.getSchemaVersion(), is(equalTo("1.0.0")));
        assertThat("Response should contain source metadata",
            response.containsMetadata("source"), is(true));
        assertThat("Source metadata should indicate module-direct retrieval",
            response.getMetadataOrThrow("source"), is(equalTo("module-direct")));
        assertThat("Response should contain display_name metadata",
            response.containsMetadata("display_name"), is(true));
        assertThat("Display name should match the module's display name",
            response.getMetadataOrThrow("display_name"), is(equalTo("Test Module")));
        assertThat("Response should contain description metadata",
            response.containsMetadata("description"), is(true));
        assertThat("Description should match the module's description",
            response.getMetadataOrThrow("description"), is(equalTo("Test Description")));
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
        assertThat("Response should not be null", response, is(notNullValue()));
        assertThat("Module name should match the requested service name",
            response.getModuleName(), is(equalTo(serviceName)));
        assertThat("Schema JSON should contain openapi specification",
            response.getSchemaJson(), containsString("openapi"));
        assertThat("Schema JSON should contain module configuration reference",
            response.getSchemaJson(), containsString(serviceName + " Configuration"));
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
        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class, () -> schemaRetrievalHandler.getModuleSchema(request).await().indefinitely());

        assertThat("Exception status code should be NOT_FOUND",
            exception.getStatus().getCode(), is(equalTo(Status.NOT_FOUND.getCode())));
        assertThat("Exception should have a description",
            exception.getStatus().getDescription(), is(notNullValue()));
        assertThat("Exception description should contain 'Module schema not found'",
            exception.getStatus().getDescription(), containsString("Module schema not found"));
    }
}
