package io.pipeline.registration.consul;

import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.consul.ConsulClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ConsulClientProducer {
    
    private static final Logger LOG = Logger.getLogger(ConsulClientProducer.class);
    
    @Inject
    Vertx vertx;
    
    @ConfigProperty(name = "pipeline.consul.host", defaultValue = "localhost")
    String consulHost;
    
    @ConfigProperty(name = "pipeline.consul.port", defaultValue = "8500")
    int consulPort;
    
    @Produces
    @ApplicationScoped
    public ConsulClient produceConsulClient() {
        LOG.infof("Creating Consul client for %s:%d", consulHost, consulPort);
        
        ConsulClientOptions options = new ConsulClientOptions()
            .setHost(consulHost)
            .setPort(consulPort);
            
        return ConsulClient.create(vertx, options);
    }
}