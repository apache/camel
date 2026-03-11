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
package org.apache.camel.component.jms.integration;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test that all thread pools are removed when adding and removing a route dynamically. This test manipulates the thread
 * pools, so it's not a good candidate for running in parallel.
 */
@Tags({ @Tag("not-parallel") })
@Timeout(60)
public class JmsAddAndRemoveRouteManagementIT extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @ContextFixture
    public void enableJmx(CamelContext context) {
        DefaultCamelContext.setDisableJmx(false);
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testAddAndRemoveRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName query = new ObjectName("*:type=threadpools,*");

        Set<ObjectName> before = mbeanServer.queryNames(query, null);

        getMockEndpoint("mock:result").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsAddAndRemoveRouteManagementTest.in").routeId("myNewRoute")
                        .to("activemq:queue:JmsAddAndRemoveRouteManagementTest.foo");
            }
        });

        // Wait for the new route's thread pool to be registered and identify it
        AtomicReference<Set<ObjectName>> duringRef = new AtomicReference<>();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<ObjectName> during = mbeanServer.queryNames(query, null);
            Set<ObjectName> added = new HashSet<>(during);
            added.removeAll(before);
            Assertions.assertFalse(added.isEmpty(),
                    "There should be at least one new thread pool in JMX");
            duringRef.set(during);
        });

        // Identify the thread pools added by the new route
        Set<ObjectName> addedPools = new HashSet<>(duringRef.get());
        addedPools.removeAll(before);

        template.sendBody("activemq:queue:JmsAddAndRemoveRouteManagementTest.in", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // now stop and remove that route
        context.getRouteController().stopRoute("myNewRoute");
        context.removeRoute("myNewRoute");

        // Verify the thread pools from the removed route are cleaned up
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<ObjectName> after = mbeanServer.queryNames(query, null);
            for (ObjectName added : addedPools) {
                Assertions.assertFalse(after.contains(added),
                        "Thread pool from removed route should be cleaned up: " + added);
            }
        });
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsAddAndRemoveRouteManagementTest.foo").to("mock:result");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
