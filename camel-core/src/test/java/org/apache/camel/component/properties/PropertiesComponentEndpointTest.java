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
package org.apache.camel.component.properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class PropertiesComponentEndpointTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testPropertiesComponentEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPropertiesComponentEndpoints() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("{{cool.start}}")
                    .to("log:{{cool.start}}?showBodyType=false&showExchangeId={{cool.showid}}")
                    .to("mock:{{cool.result}}");
            }
        });
        context.start();

        getMockEndpoint("mock:{{cool.result}}").expectedMessageCount(1);

        template.sendBody("{{cool.start}}", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPropertiesComponentMandatory() throws Exception {
        context.removeComponent("properties");
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("{{cool.start}}")
                        .to("log:{{cool.start}}?showBodyType=false&showExchangeId={{cool.showid}}")
                        .to("mock:{{cool.result}}");
                }
            });
            context.start();
            fail("Should throw exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            String msg = "PropertiesComponent with name properties must be defined in CamelContext to support property placeholders.";
            assertTrue(cause.getMessage().startsWith(msg));
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        context.addComponent("properties", pc);

        return context;
    }

}