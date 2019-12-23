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
package org.apache.camel.component.metrics.routepolicy;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.codahale.metrics.MetricRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.metrics.MetricsComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ManagedMetricsRoutePolicyTest extends CamelTestSupport {

    @BindToRegistry(MetricsComponent.METRIC_REGISTRY_NAME)
    private MetricRegistry metricRegistry = new MetricRegistry();

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        MetricsRoutePolicyFactory factory = new MetricsRoutePolicyFactory();
        factory.setUseJmx(true);
        factory.setPrettyPrint(true);
        context.addRoutePolicyFactory(factory);

        return context;
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
        assertEquals(3, metricRegistry.getNames().size());

        // there should be 3 mbeans
        Set<ObjectName> set = getMBeanServer().queryNames(new ObjectName("org.apache.camel.metrics:*"), null);
        assertEquals(3, set.size());

        String name = String.format("org.apache.camel:context=%s,type=services,name=MetricsRegistryService", context.getManagementName());
        ObjectName on = ObjectName.getInstance(name);
        String json = (String)getMBeanServer().invoke(on, "dumpStatisticsAsJson", null, null);
        assertNotNull(json);
        log.info(json);

        assertTrue(json.contains("test"));
        assertTrue(json.contains("bar.responses"));
        assertTrue(json.contains("foo.responses"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").to("metrics:counter:test").to("mock:result");

                from("seda:bar").routeId("bar").to("mock:result");
            }
        };
    }
}
