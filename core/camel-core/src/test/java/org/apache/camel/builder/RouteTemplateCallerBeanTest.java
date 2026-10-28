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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bean that the caller binds with the same name as a template bean takes precedence over the template bean.
 */
class RouteTemplateCallerBeanTest extends ContextTestSupport {

    private final AtomicInteger templateGreetings = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @BeforeEach
    void addTemplate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateBean("greeting", Processor.class, rtc -> {
                            templateGreetings.incrementAndGet();
                            return (Processor) e -> e.getMessage().setBody("template");
                        })
                        .templateBean("suffix", Processor.class,
                                rtc -> (Processor) e -> e.getMessage().setBody(e.getMessage().getBody() + "!"))
                        .from("direct:{{foo}}")
                        .process("greeting")
                        .process("suffix");
            }
        });
        context.start();
    }

    @Test
    void builderBeanTakesPrecedence() {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .bean("greeting", mine())
                .add();
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "two")
                .bean("greeting", Processor.class, mine())
                .add();

        assertThat(template.requestBody("direct:one", "World")).isEqualTo("caller!");
        assertThat(template.requestBody("direct:two", "World")).isEqualTo("caller!");
        // the template bean with the same name is not created
        assertThat(templateGreetings.get()).isZero();
    }

    @Test
    void templatedRouteBeanTakesPrecedence() throws Exception {
        TemplatedRouteDefinition def = new TemplatedRouteDefinition();
        def.setRouteTemplateRef("myTemplate");
        def.parameter("foo", "one").bean("greeting", mine());
        ((ModelCamelContext) context).addRouteFromTemplatedRoute(def);

        assertThat(template.requestBody("direct:one", "World")).isEqualTo("caller!");
        assertThat(templateGreetings.get()).isZero();
    }

    @Test
    void configurerBeanTakesPrecedence() {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .configure(rtc -> rtc.bind("greeting", Processor.class, mine()))
                .add();

        assertThat(template.requestBody("direct:one", "World")).isEqualTo("caller!");
    }

    private static Processor mine() {
        return e -> e.getMessage().setBody("caller");
    }
}
