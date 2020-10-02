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
package org.apache.camel.component.infinispan.testcontainers;

import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.junit5.Wait;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InfinispanTestContainerSupport extends ContainerAwareTestSupport {

    public static final String CONTAINER_IMAGE = "infinispan/server:11.0.3.Final";
    public static final String CONTAINER_NAME = "infinispan";

    @Override
    protected GenericContainer<?> createContainer() {
        return localstackContainer();
    }

    public static GenericContainer localstackContainer() {
        return new GenericContainer(CONTAINER_IMAGE)
                .withNetworkAliases(CONTAINER_NAME)
                .withEnv("USER", "admin")
                .withEnv("PASSWORD", "password")
                .withExposedPorts(11222)
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessageContaining("Infinispan Server 11.0.3.Final started", 1));
    }

    public String getInfispanUrl() {
        return String.format(
                "%s:%d",
                getContainerHost(CONTAINER_NAME),
                getContainerPort(CONTAINER_NAME, 11222));
    }

    public RemoteCache<Object, Object> getDefaultCache() {
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
        clientBuilder.addServer().host(getContainerHost(CONTAINER_NAME)).port(11222)
                .security().authentication().username("user").password("password");

        RemoteCacheManager remoteCacheManager = new RemoteCacheManager(clientBuilder.build());
        RemoteCache<Object, Object> cache = remoteCacheManager.getCache("default");
        return cache;
    }
}
