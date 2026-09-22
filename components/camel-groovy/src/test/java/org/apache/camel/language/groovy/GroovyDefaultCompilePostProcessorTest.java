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
package org.apache.camel.language.groovy;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.SimpleFunction;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Without any {@link CompilePostProcessor} in the registry (as in a camel-main, Spring Boot or Quarkus application,
 * unlike camel-jbang) the compiler falls back to the built-in processors for the Camel annotations.
 */
public class GroovyDefaultCompilePostProcessorTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        DefaultGroovyScriptCompiler compiler = new DefaultGroovyScriptCompiler();
        compiler.setCamelContext(context);
        compiler.setScriptPattern("file:src/test/resources/camel-groovy-default/*");
        context.addService(compiler);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:mask")
                        .setBody().simple("${maskEmail(${body})}")
                        .to("mock:result");

                from("direct:order")
                        .convertBodyTo(Order.class)
                        .setBody().simple("${body.id}")
                        .to("mock:result");

                // the Groovy sources are compiled when the compiler service starts, after the routes are created,
                // so the beans are resolved at runtime
                from("direct:namedLazy")
                        .toD("bean:named-lazy?method=hello")
                        .to("mock:result");

                from("direct:unnamedLazy")
                        .toD("bean:UnnamedLazyBean?method=hello")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testBindToRegistryWithoutRegisteredPostProcessor() throws Exception {
        assertTrue(context.getRegistry().findByType(CompilePostProcessor.class).isEmpty());
        assertInstanceOf(SimpleFunction.class, context.getRegistry().lookupByName("mask-email-function"));

        getMockEndpoint("mock:result").expectedBodiesReceived("j***@example.com");
        template.sendBody("direct:mask", "john.doe@example.com");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConverterWithoutRegisteredPostProcessor() throws Exception {
        assertNotNull(context.getTypeConverterRegistry().lookup(Order.class, String.class));

        getMockEndpoint("mock:result").expectedBodiesReceived("123");
        template.sendBody("direct:order", " 123 ");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testEventNotifierWithoutRegisteredPostProcessor() throws Exception {
        Object bean = context.getRegistry().lookupByName("OrderEventNotifier");
        assertInstanceOf(EventNotifier.class, bean);
        assertTrue(context.getManagementStrategy().getEventNotifiers().contains(bean),
                "EventNotifier from the Groovy source should be added to the management strategy");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:order", "123");
        MockEndpoint.assertIsSatisfied(context);

        // the notifier counts completed exchanges (the event is fired before sendBody returns)
        AtomicInteger completed = (AtomicInteger) bean.getClass().getMethod("getCompleted").invoke(bean);
        assertEquals(1, completed.get());
    }

    @Test
    public void testLazyBeanWithoutRegisteredPostProcessor() throws Exception {
        // a lazy bean is bound by the annotation value, else by the simple class name, as an eager bean is
        assertNotNull(context.getRegistry().lookupByName("named-lazy"));
        assertNull(context.getRegistry().lookupByName("NamedLazyBean"));
        assertNotNull(context.getRegistry().lookupByName("UnnamedLazyBean"));

        getMockEndpoint("mock:result").expectedBodiesReceived("named", "unnamed");
        template.sendBody("direct:namedLazy", "");
        template.sendBody("direct:unnamedLazy", "");
        MockEndpoint.assertIsSatisfied(context);
    }

    public record Order(String id) {
    }
}
