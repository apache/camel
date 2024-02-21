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
package org.apache.camel.component.micrometer.messagehistory;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.jmx.JmxMeterRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.micrometer.CamelJmxConfig;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedMessageHistoryTest extends CamelTestSupport {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @BindToRegistry(MicrometerConstants.METRICS_REGISTRY_NAME)
    private CompositeMeterRegistry meterRegistry;

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    public void addRegistry() {
        meterRegistry = new CompositeMeterRegistry();
        meterRegistry.add(new SimpleMeterRegistry());
        meterRegistry.add(new JmxMeterRegistry(CamelJmxConfig.DEFAULT, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        addRegistry();
        MicrometerMessageHistoryFactory factory = new MicrometerMessageHistoryFactory();
        factory.setPrettyPrint(true);
        factory.setMeterRegistry(meterRegistry);
        context.setMessageHistoryFactory(factory);

        return context;
    }

    private void cleanMbeanServer() throws Exception {
        //Deleting data from other tests
        for (ObjectName it : timerNames()) {
            getMBeanServer().unregisterMBean(it);
        }
    }

    private Set<ObjectName> timerNames() throws Exception {
        return getMBeanServer().queryNames(new ObjectName("org.apache.camel.micrometer:type=timers,name=*"), null);
    }

    @Test
    public void testMessageHistory() throws Exception {
        cleanMbeanServer();
        int count = 10;

        getMockEndpoint("mock:foo").expectedMessageCount(count / 2);
        getMockEndpoint("mock:bar").expectedMessageCount(count / 2);
        getMockEndpoint("mock:baz").expectedMessageCount(count / 2);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("seda:foo", "Hello " + i);
            } else {
                template.sendBody("seda:bar", "Hello " + i);
            }
        }

        MockEndpoint.assertIsSatisfied(context);

        // there should be 3 names
        assertEquals(3, meterRegistry.getMeters().size());

        // there should be 3 mbeans
        Set<ObjectName> set = timerNames();
        assertEquals(3, set.size());

        ObjectName fooMBean = set.stream().filter(on -> on.getCanonicalName().contains("foo")).findFirst()
                .orElseThrow(() -> new AssertionError("Expected MBean with node Id foo"));

        Long testCount = (Long) getMBeanServer().getAttribute(fooMBean, "Count");
        assertEquals(count / 2, testCount.longValue());

        // get the message history service using JMX
        String name = String.format("org.apache.camel:context=%s,type=services,name=MicrometerMessageHistoryService",
                context.getManagementName());
        ObjectName on = ObjectName.getInstance(name);
        String json = (String) getMBeanServer().invoke(on, "dumpStatisticsAsJson", null, null);
        assertNotNull(json);
        log.info(json);

        assertTrue(json.contains("\"nodeId\" : \"foo\""));
        assertTrue(json.contains("\"nodeId\" : \"bar\""));
        assertTrue(json.contains("\"nodeId\" : \"baz\""));

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("route1").to("mock:foo").id("foo");

                from("seda:bar").routeId("route2").to("mock:bar").id("bar").to("mock:baz").id("baz");
            }
        };
    }
}
