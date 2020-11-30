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

import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.processor.idempotent.InfinispanIdempotentRepository;
import org.apache.camel.component.mock.MockEndpoint;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.junit.Before;
import org.junit.jupiter.api.Test;

public class InfinispanTestContainersIdempotentRepositoryTest extends InfinispanTestContainerSupport {

    private RemoteCacheManager remoteCacheManager;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Before
    public void doPreSetup() {
        remoteCacheManager = createAndGetDefaultCache();
    }

    @Test
    public void producerQueryOperationWithoutQueryBuilder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        final String messageId = UUID.randomUUID().toString();
        IntStream.range(0, 10).forEach(
                i -> template().sendBodyAndHeader("direct:start", "message-" + i, "MessageID", messageId));

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .idempotentConsumer(
                                header("MessageID"),
                                new InfinispanIdempotentRepository(
                                        new RemoteCacheManager(
                                                new ConfigurationBuilder()
                                                        .addServers(getInfispanUrl())
                                                        .security().authentication().username(service.username())
                                                        .password(service.password()).realm("default")
                                                        .serverName("infinispan").saslMechanism("DIGEST-MD5")
                                                        .build(),
                                                true),
                                        "mycache"))
                        .skipDuplicate(true)
                        .to("mock:result");
            }
        };
    }
}
