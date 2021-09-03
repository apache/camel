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
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedService;
import org.apache.camel.test.infra.activemq.services.ActiveMQEmbeddedServiceBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class KameletReifyIT extends CamelTestSupport {

    static int tcpPort = AvailablePortFinder.getNextAvailable();

    @RegisterExtension
    public static ActiveMQEmbeddedService service = ActiveMQEmbeddedServiceBuilder
            .bare()
            .withPersistent(false)
            .withTcpTransport(tcpPort)
            .build();

    private static final String QUEUE_NAME = "my-queue";

    @Override
    protected boolean useJmx() {
        return false;
    }

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

    @Override
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
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        final String brokerUrl = String.format("tcp://localhost:%d", tcpPort);

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
