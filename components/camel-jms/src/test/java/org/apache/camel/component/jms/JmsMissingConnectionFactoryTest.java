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
package org.apache.camel.component.jms;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The error when no connection factory is set names the endpoint, and says why the component did not autowire one.
 */
public class JmsMissingConnectionFactoryTest {

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    @Test
    public void testConsumerWithTwoConnectionFactoryBeans() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            // connection factories that are never connected
            context.getRegistry().bind("cf2", new ActiveMQConnectionFactory("vm://JmsMissingConnectionFactoryTest-2"));
            context.getRegistry().bind("cf1", new ActiveMQConnectionFactory("vm://JmsMissingConnectionFactoryTest-1"));
            context.addComponent("jms", new JmsComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsMissingConnectionFactoryTest.a").to("mock:a");
                }
            });

            Exception e = assertThrows(Exception.class, context::start);
            assertEquals("connectionFactory must be specified on the jms component or on the endpoint"
                         + " jms://queue:JmsMissingConnectionFactoryTest.a. The component did not autowire one, because the"
                         + " registry has 2 jakarta.jms.ConnectionFactory beans: cf1, cf2. Set the connectionFactory option"
                         + " to one of them, such as connectionFactory=#cf1",
                    illegalArgument(e).getMessage());
        }
    }

    @Test
    public void testConsumerWithNoConnectionFactoryBean() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addComponent("jms", new JmsComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsMissingConnectionFactoryTest.b").to("mock:b");
                }
            });

            Exception e = assertThrows(Exception.class, context::start);
            assertEquals("connectionFactory must be specified on the jms component or on the endpoint"
                         + " jms://queue:JmsMissingConnectionFactoryTest.b. The registry has no jakarta.jms.ConnectionFactory"
                         + " bean to autowire. Create the ConnectionFactory of the client library of the broker as a bean,"
                         + " or set the connectionFactory option",
                    illegalArgument(e).getMessage());
        }
    }

    @Test
    public void testProducerWithTwoConnectionFactoryBeans() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("cf1", new ActiveMQConnectionFactory("vm://JmsMissingConnectionFactoryTest-1"));
            context.getRegistry().bind("cf2", new ActiveMQConnectionFactory("vm://JmsMissingConnectionFactoryTest-2"));
            context.addComponent("broker", new JmsComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("broker:queue:JmsMissingConnectionFactoryTest.c?timeToLive=1000");
                }
            });
            context.start();

            ProducerTemplate template = context.createProducerTemplate();
            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> template.sendBody("direct:start", "Hello"));
            IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("connectionFactory must be specified on the broker component or on the endpoint"
                         + " broker://queue:JmsMissingConnectionFactoryTest.c. The component did not autowire one, because"
                         + " the registry has 2 jakarta.jms.ConnectionFactory beans: cf1, cf2. Set the connectionFactory"
                         + " option to one of them, such as connectionFactory=#cf1",
                    cause.getMessage());
        }
    }

    @Test
    public void testAutowireDisabled() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("cf1", new ActiveMQConnectionFactory("vm://JmsMissingConnectionFactoryTest-1"));
            JmsComponent component = new JmsComponent();
            component.setAllowAutoWiredConnectionFactory(false);
            context.addComponent("jms", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("jms:queue:JmsMissingConnectionFactoryTest.d").to("mock:d");
                }
            });

            Exception e = assertThrows(Exception.class, context::start);
            assertEquals("connectionFactory must be specified on the jms component or on the endpoint"
                         + " jms://queue:JmsMissingConnectionFactoryTest.d. The component does not autowire one, because"
                         + " allowAutoWiredConnectionFactory is false. Set the connectionFactory option",
                    illegalArgument(e).getMessage());
        }
    }

    @Test
    public void testSingleConnectionFactoryBeanIsAutowired() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            ConnectionFactory cf = ConnectionFactoryHelper.createConnectionFactory(service);
            context.getRegistry().bind("cf1", cf);
            context.addComponent("jms", new JmsComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("jms:queue:JmsMissingConnectionFactoryTest.e");
                    from("jms:queue:JmsMissingConnectionFactoryTest.e").to("mock:e");
                }
            });
            context.start();

            assertSame(cf, context.getComponent("jms", JmsComponent.class).getConfiguration().getConnectionFactory());
            MockEndpoint mock = context.getEndpoint("mock:e", MockEndpoint.class);
            mock.expectedBodiesReceived("Hello");
            context.createProducerTemplate().sendBody("direct:start", "Hello");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    public void testConnectionFactoryOnTheEndpoint() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("cf1", ConnectionFactoryHelper.createConnectionFactory(service));
            context.getRegistry().bind("cf2", new ActiveMQConnectionFactory("vm://JmsMissingConnectionFactoryTest-2"));
            context.addComponent("jms", new JmsComponent());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("jms:queue:JmsMissingConnectionFactoryTest.f?connectionFactory=#cf1");
                    from("jms:queue:JmsMissingConnectionFactoryTest.f?connectionFactory=#cf1").to("mock:f");
                }
            });
            context.start();

            MockEndpoint mock = context.getEndpoint("mock:f", MockEndpoint.class);
            mock.expectedBodiesReceived("Hello");
            context.createProducerTemplate().sendBody("direct:start", "Hello");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    private static IllegalArgumentException illegalArgument(Throwable t) {
        IllegalArgumentException found = null;
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IllegalArgumentException iae) {
                found = iae;
            }
        }
        assertNotNull(found, "no IllegalArgumentException in " + t);
        return found;
    }
}
