/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
