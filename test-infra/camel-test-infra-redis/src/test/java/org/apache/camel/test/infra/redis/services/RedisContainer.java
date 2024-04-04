package org.apache.camel.test.infra.redis.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.redis.common.RedisProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class RedisContainer extends GenericContainer<RedisContainer> {
    public static final String CONTAINER_NAME = "redis";

    public RedisContainer() {
        super(LocalPropertyResolver.getProperty(RedisLocalContainerService.class, RedisProperties.REDIS_CONTAINER));

        this.withNetworkAliases(CONTAINER_NAME)
                .withExposedPorts(RedisProperties.DEFAULT_PORT)
                .waitingFor(Wait.forListeningPort());
    }

    public RedisContainer(String imageName) {
        super(DockerImageName.parse(imageName));
    }

    public static RedisContainer initContainer(String imageName, String networkAlias) {
        return new RedisContainer(imageName)
                .withNetworkAliases(networkAlias)
                .withExposedPorts(RedisProperties.DEFAULT_PORT)
                .waitingFor(Wait.forListeningPort());
    }
}
