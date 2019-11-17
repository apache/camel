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
package org.apache.camel.component.micrometer.routepolicy;

import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import io.micrometer.core.instrument.Meter;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedMicrometerRoutePolicyTest extends AbstractMicrometerRoutePolicyTest {


    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        int count = 10;
        getMockEndpoint("mock:result").expectedMessageCount(count);

        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                template.sendBody("seda:foo", "Hello " + i);
            } else {
                template.sendBody("seda:bar", "Hello " + i);
            }
        }

        assertMockEndpointsSatisfied();

        // there should be 3 names
        List<Meter> meters = meterRegistry.getMeters();
        assertEquals(3, meters.size());

        String name = String.format("org.apache.camel:context=%s,type=services,name=MicrometerRoutePolicyService", context.getManagementName());
        ObjectName on = ObjectName.getInstance(name);
        String json = (String) getMBeanServer().invoke(on, "dumpStatisticsAsJson", null, null);
        assertNotNull(json);
        log.info(json);

        assertFalse(json.contains("\"name\" : \"test\""));  // the MicrometerRoutePolicy does NOT display producer metrics
        assertTrue(json.contains("\"routeId\" : \"bar\""));
        assertTrue(json.contains("\"routeId\" : \"foo\""));

        // there should be 2 route policy meter mbeans
        Set<ObjectName> set = getMBeanServer().queryNames(new ObjectName("org.apache.camel.micrometer:name=CamelRoutePolicy.*"), null);
        assertEquals(2, set.size());

        String camelContextName = context().getName();
        Long testCount = (Long)getMBeanServer().getAttribute(new ObjectName("org.apache.camel.micrometer:name=test.camelContext." + camelContextName), "Count");
        assertEquals(count / 2, testCount.longValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo")
                        .to("micrometer:counter:test")
                        .to("mock:result");

                from("seda:bar").routeId("bar")
                        .to("mock:result");
            }
        };
    }
}
