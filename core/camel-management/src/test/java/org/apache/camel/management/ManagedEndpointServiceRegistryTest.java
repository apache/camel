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
package org.apache.camel.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_SERVICE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class ManagedEndpointServiceRegistryTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("service", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new ServiceEndpoint(uri, this);
            }
        });
        // the registry is created on first use, so create it before the context is started to have it managed
        context.getCamelContextExtension().getEndpointServiceRegistry();
        return context;
    }

    @Test
    public void testTwoRoutesConsumeSameService() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_SERVICE, "DefaultEndpointServiceRegistry");

        // both routes consume the same service, which failed with KeyAlreadyExistsException
        TabularData data = (TabularData) mbeanServer.invoke(on, "listEndpointServices", null, null);

        List<String> routeIds = new ArrayList<>();
        for (Object row : data.values()) {
            CompositeData cd = (CompositeData) row;
            assertEquals("localhost:8080", cd.get("serviceUrl"));
            if ("in".equals(cd.get("dir"))) {
                routeIds.add((String) cd.get("routeId"));
            }
        }
        routeIds.sort(null);
        assertEquals(List.of("a", "b"), routeIds);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("service:foo").routeId("a").to("mock:a");
                from("service:foo").routeId("b").to("mock:b");
            }
        };
    }

    private static class ServiceEndpoint extends DefaultEndpoint implements EndpointServiceLocation, MultipleConsumersSupport {

        ServiceEndpoint(String uri, DefaultComponent component) {
            super(uri, component);
        }

        @Override
        public String getServiceUrl() {
            return "localhost:8080";
        }

        @Override
        public String getServiceProtocol() {
            return "tcp";
        }

        @Override
        public boolean isMultipleConsumersSupported() {
            return true;
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor);
        }
    }
}
