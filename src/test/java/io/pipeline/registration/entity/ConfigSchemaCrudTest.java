package io.pipeline.registration.entity;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ConfigSchemaCrudTest {

    @Test
    @RunOnVertxContext
    void configSchema_fullCrud_flow(UniAsserter asserter) {
        // Create
        String json = """
        {
          "type":"object",
          "properties":{
            "port":{"type":"integer"},
            "host":{"type":"string"}
          },
          "required":["port","host"]
        }
        """;

        ConfigSchema schema = ConfigSchema.create("orders", "1.0.0", json);
        schema.createdBy = "test";
        final String id = schema.schemaId;

        // Persist
        asserter.execute(() -> Panache.withTransaction(schema::persist));

        // Read
        asserter.assertThat(
                () -> Panache.withSession(() -> ConfigSchema.<ConfigSchema>findById(id)),
                found -> {
                    assertNotNull(found, "Expected to find schema after persist");
                    assertEquals(id, found.schemaId);
                    assertEquals("orders", found.serviceName);
                    assertEquals("1.0.0", found.schemaVersion);
                    assertNotNull(found.jsonSchema);
                    assertTrue(found.jsonSchema.contains("\"port\""));
                    assertTrue(found.jsonSchema.contains("\"host\""));
                    assertEquals(ConfigSchema.SyncStatus.PENDING, found.syncStatus);
                    assertNotNull(found.createdAt);
                }
        );

        // Update: mark synced
        asserter.execute(() -> Panache.withTransaction(() ->
                ConfigSchema.<ConfigSchema>findById(id)
                        .invoke(s -> s.markSynced("artifact-1", 100L))
        ));

        asserter.assertThat(
                () -> Panache.withSession(() -> ConfigSchema.<ConfigSchema>findById(id)),
                synced -> {
                    assertEquals(ConfigSchema.SyncStatus.SYNCED, synced.syncStatus);
                    assertEquals("artifact-1", synced.apicurioArtifactId);
                    assertEquals(100L, synced.apicurioGlobalId);
                    assertNotNull(synced.lastSyncAttempt);
                    assertNull(synced.syncError);
                }
        );

        // Update: OUT_OF_SYNC then FAILED
        asserter.execute(() -> Panache.withTransaction(() ->
                ConfigSchema.<ConfigSchema>findById(id)
                        .invoke(s -> s.syncStatus = ConfigSchema.SyncStatus.OUT_OF_SYNC)
        ));

        asserter.assertThat(
                () -> Panache.withSession(() -> ConfigSchema.<ConfigSchema>findById(id)),
                outOfSync -> assertEquals(ConfigSchema.SyncStatus.OUT_OF_SYNC, outOfSync.syncStatus)
        );

        asserter.execute(() -> Panache.withTransaction(() ->
                ConfigSchema.<ConfigSchema>findById(id)
                        .invoke(s -> s.markSyncFailed("network error"))
        ));

        asserter.assertThat(
                () -> Panache.withSession(() -> ConfigSchema.<ConfigSchema>findById(id)),
                failed -> {
                    assertEquals(ConfigSchema.SyncStatus.FAILED, failed.syncStatus);
                    assertEquals("network error", failed.syncError);
                    assertNotNull(failed.lastSyncAttempt);
                }
        );

        // Delete
        asserter.assertThat(
                () -> Panache.withTransaction(() -> ConfigSchema.deleteById(id)),
                deleted -> assertTrue(deleted)
        );

        asserter.assertThat(
                () -> Panache.withSession(() -> ConfigSchema.<ConfigSchema>findById(id)),
                afterDelete -> assertNull(afterDelete)
        );
    }
}