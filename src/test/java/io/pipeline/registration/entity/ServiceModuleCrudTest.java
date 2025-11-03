package io.pipeline.registration.entity;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ServiceModuleCrudTest {

    @Test
    @RunOnVertxContext
    void serviceModule_fullCrud_flow(UniAsserter asserter) {
        // Create
        ServiceModule module = ServiceModule.create("orders", "127.0.0.1", 9090);
        module.version = "1.0.0";
        module.metadata = new HashMap<>();
        module.metadata.put("env", "test");
        module.metadata.put("replicas", 1);
        final String id = module.serviceId;

        // Persist in TX
        asserter.execute(() -> Panache.withTransaction(module::persist));

        // Read back (by id)
        asserter.assertThat(
                () -> Panache.withSession(() -> ServiceModule.<ServiceModule>findById(id)),
                found -> {
                    assertNotNull(found, "Expected module to be found after persist");
                    assertEquals(id, found.serviceId);
                    assertEquals("orders", found.serviceName);
                    assertEquals("127.0.0.1", found.host);
                    assertEquals(9090, found.port);
                    assertEquals("1.0.0", found.version);
                    assertNotNull(found.registeredAt, "registeredAt should be set by DB/CreationTimestamp");
                    assertNotNull(found.lastHeartbeat, "lastHeartbeat seeded in factory");
                    assertEquals(ServiceStatus.ACTIVE, found.status);
                    assertEquals("test", found.metadata.get("env"));
                    assertEquals(1, ((Number)found.metadata.get("replicas")).intValue());
                }
        );

        // Update within a new TX
        asserter.execute(() -> Panache.withTransaction(() -> ServiceModule.<ServiceModule>findById(id)
                .invoke(m -> {
                    m.version = "1.1.0";
                    m.status = ServiceStatus.UNHEALTHY;
                    m.metadata.put("tier", "dev");
                    m.updateHeartbeat();
                })
        ));

        // Verify the update
        asserter.assertThat(
                () -> Panache.withSession(() -> ServiceModule.<ServiceModule>findById(id)),
                updated -> {
                    assertNotNull(updated);
                    assertEquals("1.1.0", updated.version);
                    assertEquals(ServiceStatus.UNHEALTHY, updated.status);
                    assertEquals("dev", updated.metadata.get("tier"));
                    assertTrue(updated.isHealthy(), "Heartbeat should be fresh after update");
                }
        );

        // Delete
        asserter.assertThat(
                () -> Panache.withTransaction(() -> ServiceModule.deleteById(id)),
                deleted -> assertTrue(deleted, "Expected deleteById to return true")
        );

        asserter.assertThat(
                () -> Panache.withSession(() -> ServiceModule.<ServiceModule>findById(id)),
                afterDelete -> assertNull(afterDelete, "Entity should be gone after delete")
        );
    }
}
