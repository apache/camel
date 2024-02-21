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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                .bean("myBar", (Processor) ex -> ex.getMessage().setBody("Builder " + ex.getMessage().getBody()))
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
                .bean("myBar", new BuilderProcessor())
                .routeId("myRoute")
                .add();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "two")
                .parameter("bar", "myBar")
                .bean("myBar", new BuilderTwoProcessor())
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
                    rtc.bind("myBar", Processor.class,
                            (Processor) ex -> ex.getMessage().setBody("Configure2 " + ex.getMessage().getBody()
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

    @Test
    public void testLocalBeanInTemplateConfigure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .configure(rtc -> rtc.bind("myBar",
                                (Processor) ex -> ex.getMessage().setBody("Builder " + ex.getMessage().getBody())))
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
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanInTemplateConfigureTwo() throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .configure(rtc -> rtc.bind("myBar", (Processor) ex -> ex.getMessage().setBody("Builder" +
                                                                                                      counter.incrementAndGet()
                                                                                                      + " "
                                                                                                      + ex.getMessage()
                                                                                                              .getBody())))
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

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "two")
                .parameter("bar", "myBar")
                .routeId("myRoute2")
                .add();

        assertEquals(2, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Builder1 World", out);
        Object out2 = template.requestBody("direct:two", "Camel");
        assertEquals("Builder2 Camel", out2);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanInTemplateBeanSupplier() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar", Processor.class,
                                ctx -> (Processor) ex -> ex.getMessage().setBody("Builder " + ex.getMessage().getBody()))
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
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanInTemplateBeanSupplierTwo() throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar", Processor.class, ctx -> (Processor) ex -> ex.getMessage().setBody("Builder" +
                                                                                                                 counter.incrementAndGet()
                                                                                                                 + " "
                                                                                                                 + ex.getMessage()
                                                                                                                         .getBody()))
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

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "two")
                .parameter("bar", "myBar")
                .routeId("myRoute2")
                .add();

        assertEquals(2, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Builder1 World", out);
        Object out2 = template.requestBody("direct:two", "Camel");
        assertEquals("Builder2 Camel", out2);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanInTemplateBean() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar",
                                (Supplier<Processor>) () -> ex -> ex.getMessage()
                                        .setBody("Builder " + ex.getMessage().getBody()))
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
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanInTemplateBeanTwo() throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar", (Supplier<Processor>) () -> ex -> ex.getMessage().setBody("Builder"
                                                                                                         + counter
                                                                                                                 .incrementAndGet()
                                                                                                         + " "
                                                                                                         + ex.getMessage()
                                                                                                                 .getBody()))
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

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "two")
                .parameter("bar", "myBar")
                .routeId("myRoute2")
                .add();

        assertEquals(2, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Builder1 World", out);
        Object out2 = template.requestBody("direct:two", "Camel");
        assertEquals("Builder2 Camel", out2);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar", "bean",
                                RouteTemplateLocalBeanTest.class.getName() + "?method=createBuilderProcessor")
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
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanExpressionFluent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar").bean(RouteTemplateLocalBeanTest.class, "createBuilderProcessor")
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
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanExpressionFluentTwo() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateParameter("greeting", "Davs ")
                        .templateBean("myBar").bean(RouteTemplateLocalBeanTest.class, "createBuilderProcessorTwo")
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
        assertEquals("Davs Builder2 World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanClassExpressionFluent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar").typeClass(BuilderProcessor.class).end()
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
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanClassAsString() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar").type(BuilderProcessor.class.getName()).end()
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
        assertEquals("Builder World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    public static class BuilderProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getMessage().setBody("Builder " + exchange.getMessage().getBody());
        }
    }

    @Test
    public void testLocalBeanClassPropertiesFluent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar").templateParameter("hi")
                        .templateBean("myBar").property("prefix", "{{hi}}").typeClass(BuilderThreeProcessor.class).end()
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .parameter("hi", "Davs")
                .routeId("myRoute")
                .add();

        assertEquals(1, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("DavsBuilder3 World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanMemorize() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar").templateParameter("hi")
                        .templateBean("myBar").property("prefix", "{{hi}}").typeClass(BuilderThreeProcessor.class).end()
                        .from("direct:{{foo}}")
                        // use unique endpoints to force referring the to bean twice
                        .to("bean:{{bar}}")
                        .to("bean:{{bar}}?method=process")
                        .to("mock:result");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .parameter("hi", "Davs")
                .routeId("myRoute")
                .add();

        assertEquals(1, context.getRoutes().size());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("DavsBuilder3 DavsBuilder3 World");
        mock.expectedHeaderReceived("counter", 2);

        Object out = template.requestBody("direct:one", "World");
        assertEquals("DavsBuilder3 DavsBuilder3 World", out);

        assertMockEndpointsSatisfied();

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanFactoryMethod() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar")
                        .type("#class:org.apache.camel.builder.RouteTemplateLocalBeanTest#createBuilderProcessorThree('MyPrefix ')")
                        .end()
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
        assertEquals("MyPrefix Builder3 World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    @Test
    public void testLocalBeanConstructorParameter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .templateBean("myBar").type("#class:org.apache.camel.builder.MyConstructorProcessor('MyCtr ')").end()
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
        assertEquals("MyCtr World", out);

        // should not be a global bean
        assertNull(context.getRegistry().lookupByName("myBar"));

        context.stop();
    }

    public static class BuilderTwoProcessor implements Processor {

        private String prefix = "";

        public BuilderTwoProcessor() {
        }

        public BuilderTwoProcessor(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getMessage().setBody(prefix + "Builder2 " + exchange.getMessage().getBody());
        }
    }

    public static class BuilderThreeProcessor implements Processor {

        private String prefix = "";
        private int counter;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            counter++;
            exchange.getMessage().setBody(prefix + "Builder3 " + exchange.getMessage().getBody());
            exchange.getMessage().setHeader("counter", counter);
        }

    }

    public Processor createBuilderProcessor() {
        return new BuilderProcessor();
    }

    public Processor createBuilderProcessorTwo(RouteTemplateContext rtc) {
        return new BuilderTwoProcessor(rtc.getProperty("greeting", String.class));
    }

    public static Processor createBuilderProcessorThree(String prefix) {
        BuilderThreeProcessor answer = new BuilderThreeProcessor();
        answer.setPrefix(prefix);
        return answer;
    }

}
