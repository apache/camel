package org.apache.camel.component.nats;

import org.apache.camel.test.testcontainers.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.Wait;
import org.testcontainers.containers.GenericContainer;

public class NatsTestSupport extends ContainerAwareTestSupport{

    public static final String CONTAINER_IMAGE = "nats:1.2.0";
    public static final String CONTAINER_NAME = "nats";
    
    @Override
    protected GenericContainer<?> createContainer() {
        return natsContainer();
    }

    public static GenericContainer natsContainer() {
        return new GenericContainer(CONTAINER_IMAGE)
            .withNetworkAliases(CONTAINER_NAME)
            .waitingFor(Wait.forLogMessageContaining("Listening for route connections", 1));
    }
    
    public String getNatsUrl() {
        return String.format(
            "%s:%d",
            getContainerHost(CONTAINER_NAME),
            getContainerPort(CONTAINER_NAME, 4222)
        );
    }
}
