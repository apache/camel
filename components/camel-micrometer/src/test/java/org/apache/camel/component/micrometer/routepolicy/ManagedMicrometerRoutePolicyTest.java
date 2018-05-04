/**
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
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ManagedMicrometerRoutePolicyTest extends AbstractMicrometerRoutePolicyTest {


    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testMetricsRoutePolicy() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                template.sendBody("seda:foo", "Hello " + i);
            } else {
                template.sendBody("seda:bar", "Hello " + i);
            }
        }

        assertMockEndpointsSatisfied();

        // there should be 3 names
        List<Meter> meters = registry.getMeters();
        assertEquals(3, meters.size());

        // there should be 3 mbeans
        Set<ObjectName> set = getMBeanServer().queryNames(new ObjectName("org.apache.camel.micrometer:*"), null);
        assertEquals(3, set.size());

        String name = String.format("org.apache.camel:context=%s,type=services,name=MicrometerRegistryService", context.getManagementName());
        ObjectName on = ObjectName.getInstance(name);
        String json = (String) getMBeanServer().invoke(on, "dumpStatisticsAsJson", null, null);
        assertNotNull(json);
        log.info(json);

        assertTrue(json.contains("test"));
        assertTrue(json.contains("bar.responses"));
        assertTrue(json.contains("foo.responses"));
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
