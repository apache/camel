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
package org.apache.camel.model;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

import org.apache.camel.BeanScope;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessorDefinitionBuilderEdgeCasesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private RouteDefinition route(RouteBuilder builder) throws Exception {
        context.addRoutes(builder);
        context.start();
        return context.getRouteDefinitions().get(context.getRouteDefinitions().size() - 1);
    }

    @Test
    public void testNoteOnChoice() throws Exception {
        RouteDefinition route = route(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").choice().note("my note").when(header("a")).to("mock:a").end();
            }
        });
        assertThat(route.getOutputs().get(0).getNote()).isEqualTo("my note");
    }

    @Test
    public void testThrottleWithCorrelationKey() throws Exception {
        RouteDefinition route = route(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").throttle(header("max"), 7L).to("mock:a");
            }
        });
        ThrottleDefinition throttle = (ThrottleDefinition) route.getOutputs().get(0);
        assertThat(throttle.getExpression().getExpression()).isEqualTo("max");
        assertThat(throttle.getCorrelationExpression().getExpressionType().getExpression()).isEqualTo("7");
    }

    @Test
    public void testRouteConfigurationOnExceptionWithThreeExceptions() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                routeConfiguration().onException(IOException.class).handled(true).to("mock:io")
                        .onException(IllegalArgumentException.class, IllegalStateException.class,
                                UnsupportedOperationException.class)
                        .handled(true).to("mock:other");
            }
        });
        List<String> exceptions = context.getRouteConfigurationDefinitions().get(0).getOnExceptions().get(1).getExceptions();
        assertThat(exceptions).containsExactly(IllegalArgumentException.class.getName(),
                IllegalStateException.class.getName(), UnsupportedOperationException.class.getName());
    }

    @Test
    public void testSimpleWithResultTypePrettyAndTrim() throws Exception {
        route(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody().simple("${body}", String.class, false, true).to("mock:result");
            }
        });
        getMockEndpoint("mock:result").expectedBodiesReceived("x");
        template.sendBody("direct:start", "  x  ");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEndDoTryFromBlockInsideCatchAndFinally() throws Exception {
        RouteDefinition route = route(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .doTry()
                            .to("mock:try")
                        .doCatch(Exception.class)
                            .filter(header("x"))
                                .to("mock:filtered")
                            .endDoTry()
                        .doFinally()
                            .filter(header("y"))
                                .to("mock:finally")
                            .endDoTry()
                        .end()
                        .to("mock:result");
            }
        });
        TryDefinition doTry = (TryDefinition) route.getOutputs().get(0);
        assertThat(doTry.getCatchClauses()).hasSize(1);
        assertThat(doTry.getFinallyClause()).isNotNull();
        assertThat(route.getOutputs().get(1)).isInstanceOf(ToDefinition.class);
    }

    @Test
    public void testEnrichWithEndpointBuilderIsDynamic() throws Exception {
        RouteDefinition route = route(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").enrichWith(producerBuilder("direct:${header.target}")).body((a, b) -> b).to("mock:result");
            }
        });
        EnrichDefinition enrich = (EnrichDefinition) route.getOutputs().get(0);
        assertThat(enrich.getExpression().getLanguage()).isEqualTo("simple");
    }

    @Test
    public void testBeanTypePrefix() throws Exception {
        RouteDefinition route = route(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .bean("type:" + MyBean.class.getName())
                        .bean("type:" + MyBean.class.getName(), BeanScope.Prototype)
                        .bean("type:" + MyBean.class.getName(), "hello", BeanScope.Prototype)
                        .to("mock:result");
            }
        });
        for (int i = 0; i < 3; i++) {
            BeanDefinition bean = (BeanDefinition) route.getOutputs().get(i);
            assertThat(bean.getBeanType()).isEqualTo(MyBean.class.getName());
            assertThat(bean.getRef()).isNull();
        }

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Hello Hello World");
        template.sendBody("direct:start", "World");
        assertMockEndpointsSatisfied();
    }

    private static EndpointProducerBuilder producerBuilder(String uri) {
        return (EndpointProducerBuilder) Proxy.newProxyInstance(EndpointProducerBuilder.class.getClassLoader(),
                new Class<?>[] { EndpointProducerBuilder.class }, (proxy, method, args) -> {
                    if ("getRawUri".equals(method.getName()) || "getUri".equals(method.getName())) {
                        return uri;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    public static class MyBean {
        public String hello(String body) {
            return "Hello " + body;
        }
    }
}
