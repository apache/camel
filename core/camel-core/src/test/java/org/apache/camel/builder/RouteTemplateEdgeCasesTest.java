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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RouteTemplateEdgeCasesTest extends ContextTestSupport {

    private static final AtomicInteger CLOSED = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLocalBeanReferredByCamelCaseParameter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateParameter("beanRef")
                        .templateBean("counter").typeClass(AtomicInteger.class).end()
                        .from("direct:{{foo}}")
                        .to("bean:{{beanRef}}?method=getAndIncrement");
            }
        });
        context.start();

        // the parameter is stored as both beanRef and bean-ref, and both must refer to the renamed local bean
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("beanRef", "counter")
                .add();

        assertThat(template.requestBody("direct:one", "World")).isEqualTo(0);
        assertThat(template.requestBody("direct:one", "World")).isEqualTo(1);
    }

    @Test
    public void testDestroyMethodOfLocalBeanReferredByParameter() throws Exception {
        CLOSED.set(0);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateParameter("proc")
                        .templateBean("myProc").typeClass(MyProc.class).destroyMethod("close").end()
                        .from("direct:{{foo}}")
                        .process("{{proc}}");
            }
        });
        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("proc", "myProc")
                .routeId("myRoute")
                .add();
        assertThat(template.requestBody("direct:one", "World")).isEqualTo("Hello World");

        context.getRouteController().stopRoute("myRoute");
        context.removeRoute("myRoute");
        assertThat(CLOSED.get()).isEqualTo(1);
    }

    @Test
    public void testLocalBeanFromSupplierIsSingleton() throws Exception {
        AtomicInteger created = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateBean("counter", AtomicInteger.class, rtc -> {
                            created.incrementAndGet();
                            return new AtomicInteger();
                        })
                        .from("direct:{{foo}}")
                        .to("bean:{{counter}}?method=incrementAndGet")
                        .to("bean:{{counter}}?method=get");
            }
        });
        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate").parameter("foo", "one").add();

        assertThat(template.requestBody("direct:one", "World")).isEqualTo(1);
        assertThat(created.get()).isEqualTo(1);
    }

    @Test
    public void testTemplatedRouteBeanFromScript() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .from("direct:{{foo}}")
                        .to("bean:{{greeting}}?method=length");
            }
        });
        context.start();

        TemplatedRouteDefinition def = new TemplatedRouteDefinition();
        def.setRouteTemplateRef("myTemplate");
        def.setRouteId("myRoute");
        def.parameter("foo", "one").bean("greeting", "simple", "Hello");
        ((ModelCamelContext) context).addRouteFromTemplatedRoute(def);

        assertThat(template.requestBody("direct:one", "World")).isEqualTo(5);
    }

    @Test
    public void testLocalBeanFactoryReadsParameterInOtherKeyStyle() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateParameter("myRegion", "us")
                        .templateBean("region", String.class, rtc -> "region:" + rtc.getProperty("myRegion"))
                        .from("direct:{{foo}}")
                        .setBody().simple("${bean:{{region}}?method=toString} {{myRegion}}");
            }
        });
        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("my-region", "eu")
                .add();

        assertThat(template.requestBody("direct:one", "World")).isEqualTo("region:eu eu");
    }

    @Test
    public void testConfigurerOfTemplateAndBuilder() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .configure(rtc -> calls.add("template"))
                        .from("direct:{{foo}}")
                        .to("mock:result");
            }
        });
        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .configure(rtc -> calls.add("builder"))
                .add();

        assertThat(calls).containsExactly("template", "builder");
    }

    @Test
    public void testNodeIdOfTemplateRouteAndPlainRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .from("direct:{{foo}}")
                        .to("mock:template").id("myNode");

                from("direct:plain").to("mock:plain").id("myNode");
            }
        });
        // the node id of the route from the template is prefixed, so it does not clash with the plain route
        TemplatedRouteBuilder.builder(context, "myTemplate").parameter("foo", "one").add();
        context.start();

        assertThat(context.getRoutes()).hasSize(2);
    }

    @Test
    public void testLocalBeanFromClassCanBeFoundByType() throws Exception {
        AtomicInteger found = new AtomicInteger(-1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateBean("myProc").typeClass(MyProc.class).end()
                        .configure(rtc -> found.set(rtc.getLocalBeanRepository().findByType(MyProc.class).size()))
                        .from("direct:{{foo}}")
                        .process("{{myProc}}");
            }
        });
        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate").parameter("foo", "one").add();

        assertThat(found.get()).isEqualTo(1);
        assertThat(template.requestBody("direct:one", "World")).isEqualTo("Hello World");
    }

    public static class MyProc implements Processor {
        @Override
        public void process(Exchange exchange) {
            exchange.getMessage().setBody("Hello " + exchange.getMessage().getBody(String.class));
        }

        public void close() {
            CLOSED.incrementAndGet();
        }
    }
}
