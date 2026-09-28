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
package org.apache.camel.processor.onexception;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.TemplatedRouteBuilder;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OnExceptionReifierEdgeCasesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOnWhenWithRouteTemplateParameter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate").templateParameter("code")
                        .from("direct:start")
                        .onException(IOException.class).onWhen(simple("${header.code} == '{{code}}'")).handled(true)
                        .to("mock:when").end()
                        .onException(IOException.class).handled(true).to("mock:fallback").end()
                        .throwException(new IOException("Forced"));
            }
        });
        context.start();
        TemplatedRouteBuilder.builder(context, "myTemplate").parameter("code", "abc").add();

        getMockEndpoint("mock:when").expectedMessageCount(1);
        getMockEndpoint("mock:fallback").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello", "code", "abc");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnWhenInvalidSyntaxFailsOnStartup() {
        assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .onException(IOException.class).onWhen(simple("${header.foo ==")).handled(true).end()
                            .to("mock:result");
                }
            });
            context.start();
        });
    }

    @Test
    public void testRedeliveryPolicyRefOnly() throws Exception {
        RedeliveryPolicy policy = new RedeliveryPolicy();
        policy.setMaximumRedeliveries(2);
        policy.setRedeliveryDelay(0);
        context.getRegistry().bind("myPolicy", policy);

        AtomicInteger attempts = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IOException.class).redeliveryPolicyRef("myPolicy");

                from("direct:start").process(e -> {
                    attempts.incrementAndGet();
                    throw new IOException("Forced");
                });
            }
        });
        context.start();

        assertThrows(Exception.class, () -> template.sendBody("direct:start", "Hello"));
        assertEquals(3, attempts.get());
    }

    @Test
    public void testNotAnExceptionClass() {
        assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    OnExceptionDefinition def = onException(IOException.class).handled(true);
                    def.getExceptions().clear();
                    def.getExceptions().add("java.lang.String");

                    from("direct:start").to("mock:result");
                }
            });
            context.start();
        });
    }
}
