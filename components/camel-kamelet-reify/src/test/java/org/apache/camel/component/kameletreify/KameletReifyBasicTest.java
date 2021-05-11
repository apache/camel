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
package org.apache.camel.component.kameletreify;

import java.util.Properties;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.BindToRegistry;
import org.apache.camel.PropertyInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.apache.http.annotation.Obsolete;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.assertj.core.api.Assertions.assertThat;

public class KameletReifyBasicTest extends ContainerAwareTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(KameletReifyBasicTest.class);
    private static final String CONTAINER_NAME = "activemq";
    private static final String CONTAINER_IMAGE = "rmohr/activemq:5.15.9-alpine";
    private static final int TCP_PORT = 61616;
    private static final String QUEUE_NAME = "my-queue";

    @Test
    public void componentsAreWrapped() throws Exception {
        assertThat(context().getComponentNames())
                .filteredOn(n -> n.startsWith("activemq-"))
                .hasSize(2);

        assertThat(context().getEndpoints())
                .filteredOn(e -> e.getEndpointUri().startsWith("activemq-"))
                .hasSize(2);
        assertThat(context().getEndpoints())
                .filteredOn(e -> e.getEndpointUri().startsWith("activemq:"))
                .isEmpty();

        getMockEndpoint("mock:result")
                .expectedBodiesReceived("test");

        fluentTemplate()
                .to("direct:start")
                .withBody("test")
                .send();

        getMockEndpoint("mock:result")
                .assertIsSatisfied();
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Obsolete
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("kamelet-reify:activemq:{{amqQueueName}}?brokerURL={{amqBrokerUrl}}");
                from("kamelet-reify:activemq:{{amqQueueName}}?connectionFactory=#amqcf")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected GenericContainer<?> createContainer() {
        return new GenericContainer<>(CONTAINER_IMAGE)
                .withNetworkAliases(CONTAINER_NAME)
                .withExposedPorts(TCP_PORT)
                .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                .waitingFor(Wait.forListeningPort());
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        final String host = getContainerHost(CONTAINER_NAME);
        final int port = getContainerPort(CONTAINER_NAME, TCP_PORT);
        final String brokerUrl = String.format("tcp://%s:%d", host, port);

        Properties properties = new Properties();
        properties.setProperty("amqBrokerUrl", brokerUrl);
        properties.setProperty("amqQueueName", QUEUE_NAME);

        return properties;
    }

    @BindToRegistry("amqcf")
    public ConnectionFactory activeMQConnectionFactory(@PropertyInject("amqBrokerUrl") String brokerUrl) {
        return new ActiveMQConnectionFactory(brokerUrl);
    }
}
