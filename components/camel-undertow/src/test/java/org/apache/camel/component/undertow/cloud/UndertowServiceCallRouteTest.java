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
package org.apache.camel.component.undertow.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndertowServiceCallRouteTest extends CamelTestSupport {
    private final int port1 = AvailablePortFinder.getNextAvailable();
    private final int port2 = AvailablePortFinder.getNextAvailable();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(context);
        beanIntrospection.setExtendedStatistics(true);
        beanIntrospection.setLoggingLevel(LoggingLevel.INFO);
        return context;
    }

    @Test
    public void testCustomCall() {
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);

        assertEquals("8081", template.requestBody("direct:custom", "hello", String.class));
        assertEquals("8082", template.requestBody("direct:custom", "hello", String.class));

        // should not use reflection
        assertEquals(0, bi.getInvokedCounter());
    }

    @Test
    public void testDefaultSchema() {
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);

        try {
            assertEquals("8081", template.requestBody("direct:default", "hello", String.class));
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof ResolveEndpointFailedException);
        }

        // should not use reflection
        assertEquals(0, bi.getInvokedCounter());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:custom")
                        .serviceCall()
                        .name("myService")
                        .component("undertow")
                        .staticServiceDiscovery()
                        .servers("myService@localhost:" + port1)
                        .servers("myService@localhost:" + port2)
                        .endParent();

                from("direct:default")
                        .serviceCall()
                        .name("myService")
                        .staticServiceDiscovery()
                        .servers("myService@localhost:" + port1)
                        .servers("myService@localhost:" + port2)
                        .endParent();

                from("undertow:http://localhost:" + port1)
                        .transform().constant("8081");
                from("undertow:http://localhost:" + port2)
                        .transform().constant("8082");
            }
        };
    }
}
