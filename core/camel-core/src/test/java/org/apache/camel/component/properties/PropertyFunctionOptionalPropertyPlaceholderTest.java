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
package org.apache.camel.component.properties;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PropertiesFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyFunctionOptionalPropertyPlaceholderTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testNoFunctionNotPresent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody().constant("{{?myKey}}")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").allMessages().body().isNull();

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

    @Test
    public void testNoFunctionPresent() throws Exception {
        Properties prop = new Properties();
        prop.put("myKey", "123");
        context.getPropertiesComponent().setInitialProperties(prop);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody().constant("{{?myKey}}")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("123", "123");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

    @Test
    public void testFunctionNotPresent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody().constant("{{reverse:?myKey}}")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").allMessages().body().isNull();

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

    @Test
    public void testFunctionPresent() throws Exception {
        Properties prop = new Properties();
        prop.put("myKey", "123");
        context.getPropertiesComponent().setInitialProperties(prop);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody().constant("{{reverse:?myKey}}")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("321", "321");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testKeepUnresolved() throws Exception {
        String out = context.getCamelContextExtension()
                .resolvePropertyPlaceholders("{{reverse:?myKey}}", true);
        Assertions.assertEquals("{{?myKey}}", out);
    }

    @Test
    public void testQueryOptionalNotPresent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("mock:result?retainFirst={{reverse:?maxKeep}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(2, getMockEndpoint("mock:result").getReceivedExchanges().size());
    }

    @Test
    public void testQueryOptionalPresent() throws Exception {
        context.getPropertiesComponent().addInitialProperty("maxKeep", "321");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("mock:result?retainFirst={{reverse:?maxKeep}}");
            }
        });
        context.start();

        getMockEndpoint("mock:result?retainFirst=123").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();

        assertEquals(2, getMockEndpoint("mock:result?retainFirst=123").getReceivedExchanges().size());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        ReverseFunction func = new ReverseFunction();
        func.setCamelContext(context);
        context.getPropertiesComponent().addPropertiesFunction(func);
        return context;
    }

    private static class ReverseFunction implements PropertiesFunction, CamelContextAware {

        private CamelContext camelContext;

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public String getName() {
            return "reverse";
        }

        @Override
        public boolean lookupFirst(String remainder) {
            return true;
        }

        @Override
        public String apply(String remainder) {
            if (remainder == null || remainder.isEmpty()) {
                return remainder;
            }
            StringBuilder sb = new StringBuilder(remainder);
            return sb.reverse().toString();
        }
    }

}
