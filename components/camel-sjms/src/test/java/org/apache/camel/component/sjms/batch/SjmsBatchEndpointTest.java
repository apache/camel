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
package org.apache.camel.component.sjms.batch;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SjmsBatchEndpointTest extends CamelTestSupport {

    // Create one embedded broker instance for the entire test, as we aren't actually
    // going to send any messages to it; we just need it so that the ConnectionFactory
    // has something local to connect to.
    @RegisterExtension
    public static EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker("localhost");

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("aggStrategy", AggregationStrategies.groupedExchange());

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL(broker.getTcpConnectorUri());

        SjmsComponent sjmsComponent = new SjmsComponent();
        sjmsComponent.setConnectionFactory(connectionFactory);

        SjmsBatchComponent sjmsBatchComponent = new SjmsBatchComponent();
        sjmsBatchComponent.setConnectionFactory(connectionFactory);

        CamelContext context = new DefaultCamelContext(registry);
        context.addComponent("sjms-batch", sjmsBatchComponent);
        context.addComponent("sjms", sjmsComponent);

        return context;
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void testProducerFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").to("sjms-batch:testQueue?aggregationStrategy=#unknown");
            }
        });
        assertThrows(FailedToCreateRouteException.class,
                () -> context.start());
    }

    @Test
    public void testConsumerNegativePollDuration() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms-batch:in?aggregationStrategy=#aggStrategy&pollDuration=-1")
                        .to("mock:out");
            }
        });
        assertThrows(FailedToStartRouteException.class,
                () -> context.start());
    }

    @Test
    public void testConsumerNegativeConsumerCount() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms-batch:in?aggregationStrategy=#aggStrategy&consumerCount=-1")
                        .to("mock:out");
            }
        });
        assertThrows(FailedToStartRouteException.class,
                () -> context.start());
    }

    @Test
    public void testConsumerTopic() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms-batch:topic:in?aggregationStrategy=#aggStrategy")
                        .to("mock:out");
            }
        });
        assertThrows(FailedToCreateRouteException.class,
                () -> context.start());
    }
}
