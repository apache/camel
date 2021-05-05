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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// TODO: bind supplier (do not cache) mention in docs that on each lookup it will call the supplier
// TODO: local bind extrapolate {{key}} with hashed so they are local (endpoint uri lookup problem)

public class RouteTemplateLocalBeanTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGlobalBean() throws Exception {
        context.getRegistry().bind("myBar", (Processor) ex -> ex.getMessage().setBody("Global " + ex.getMessage().getBody()));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .routeId("myRoute")
                .add();

        assertEquals(1, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Global World", out);

        // should be a global bean
        assertNotNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanInBuilder() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .bind("myBar", (Processor) ex -> ex.getMessage().setBody("Builder " + ex.getMessage().getBody()))
                .routeId("myRoute")
                .add();

        assertEquals(1, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    @Disabled("TODO: Fix me")
    public void testLocalBeanInBuilderTwo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .bind("myBar", new BuilderProcessor())
                .routeId("myRoute")
                .add();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "two")
                .parameter("bar", "myBar")
                .bind("myBar", new BuilderTwoProcessor())
                .routeId("myRoute2")
                .add();

        assertEquals(2, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Builder World", out);
        Object out2 = template.requestBody("direct:two", "Camel");
        assertEquals("Builder2 Camel", out2);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanInConfigure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .configure(rtc -> {
                    rtc.bind("myBar", (Processor) ex -> ex.getMessage().setBody("Configure " + ex.getMessage().getBody()
                                                                                + " from " + rtc.getProperty("foo")));
                })
                .routeId("myRoute")
                .add();

        assertEquals(1, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Configure World from one", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    @Disabled("TODO: Fix me")
    public void testLocalBeanInConfigureTwo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .configure(rtc -> {
                    rtc.bind("myBar", (Processor) ex -> ex.getMessage().setBody("Configure " + ex.getMessage().getBody()
                                                                                + " from " + rtc.getProperty("foo")));
                })
                .routeId("myRoute")
                .add();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "two")
                .parameter("bar", "myBar")
                .configure(rtc -> {
                    rtc.bind("myBar", (Processor) ex -> ex.getMessage().setBody("Configure2 " + ex.getMessage().getBody()
                                                                                + " from " + rtc.getProperty("foo")));
                })
                .routeId("myRoute2")
                .add();

        assertEquals(2, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Configure World from one", out);
        Object out2 = template.requestBody("direct:two", "Camel");
        assertEquals("Configure2 Camel from two", out2);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    private class BuilderProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getMessage().setBody("Builder " + exchange.getMessage().getBody());
        }
    }

    private class BuilderTwoProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getMessage().setBody("Builder2 " + exchange.getMessage().getBody());
        }
    }

}
