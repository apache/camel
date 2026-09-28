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
package org.apache.camel.reifier;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ReifierEdgeCasesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void start(RouteBuilder builder) throws Exception {
        context.addRoutes(builder);
        context.start();
    }

    private RedeliveryErrorHandler firstErrorHandler() {
        List<Processor> nodes = context.getRoutes().get(0).navigate().next();
        Channel channel = unwrapChannel(nodes.get(0));
        return (RedeliveryErrorHandler) channel.getErrorHandler();
    }

    @Test
    public void testOnExceptionUseOriginalBody() throws Exception {
        start(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalArgumentException.class).useOriginalBody().handled(true).to("mock:dead");

                from("direct:start").setBody(constant("Changed")).throwException(new IllegalArgumentException("Forced"));
            }
        });
        getMockEndpoint("mock:dead").expectedBodiesReceived("Hello");
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDefaultErrorHandlerLogName() throws Exception {
        start(new RouteBuilder() {
            @Override
            public void configure() {
                DefaultErrorHandlerDefinition eh = new DefaultErrorHandlerDefinition();
                eh.setLogName("com.foo.Errors");
                errorHandler(eh);

                from("direct:start").to("mock:result");
            }
        });
        assertThat(firstErrorHandler().getLogger().getLog().getName()).isEqualTo("com.foo.Errors");
    }

    @Test
    public void testDeadLetterChannelLogName() throws Exception {
        start(new RouteBuilder() {
            @Override
            public void configure() {
                DeadLetterChannelDefinition eh = new DeadLetterChannelDefinition("mock:dead");
                eh.setLogName("com.foo.Dead");
                errorHandler(eh);

                from("direct:start").to("mock:result");
            }
        });
        assertThat(firstErrorHandler().getLogger().getLog().getName()).isEqualTo("com.foo.Dead");
    }

    @Test
    public void testDisabledMulticastWithSingleOutput() throws Exception {
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").multicast().disabled().to("mock:a").end().to("mock:result");
            }
        });
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDisabledPipelineWithSingleOutput() throws Exception {
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").pipeline().disabled().to("mock:a").end().to("mock:result");
            }
        });
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPipelineWithSingleOutput() throws Exception {
        start(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").pipeline().to("mock:a").end().to("mock:result");
            }
        });
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testErrorHandlerWithUnknownExecutorService() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().executorServiceRef("unknown"));

                from("direct:start").to("mock:result");
            }
        });
        assertThatThrownBy(() -> context.start())
                .isInstanceOf(FailedToCreateRouteException.class)
                .rootCause().isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ExecutorService unknown not found in registry");
    }

    @Test
    public void testDelayWithExecutorServiceProfileFromPlaceholder() throws Exception {
        ThreadPoolProfile profile = new ThreadPoolProfile("myPool");
        profile.setPoolSize(1);
        context.getExecutorServiceManager().registerThreadPoolProfile(profile);
        Properties prop = new Properties();
        prop.put("pool", "myPool");
        context.getPropertiesComponent().setInitialProperties(prop);

        start(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").delay(10).asyncDelayed().executorService("{{pool}}").to("mock:result");
            }
        });
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnExceptionRedeliveryInheritsErrorHandlerDelay() throws Exception {
        start(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().redeliveryDelay(0));
                onException(IOException.class).maximumRedeliveries(3).handled(true).to("mock:dead");

                from("direct:start").throwException(new IOException("Forced"));
            }
        });
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        StopWatch watch = new StopWatch();
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
        // 3 redeliveries with the delay of the error handler (0) and not the default (1000)
        assertThat(watch.taken()).isLessThan(1000);
    }

    @Test
    public void testSplitAggregationStrategyMethodNameWithPlaceholder() throws Exception {
        Properties prop = new Properties();
        prop.put("method", "join");
        context.getPropertiesComponent().setInitialProperties(prop);
        context.getRegistry().bind("myJoiner", new MyJoiner());

        start(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body().tokenize(",")).aggregationStrategy("myJoiner")
                        .aggregationStrategyMethodName("{{method}}")
                            .to("mock:split")
                        .end()
                        .to("mock:result");
            }
        });
        getMockEndpoint("mock:result").expectedBodiesReceived("A+B");
        template.sendBody("direct:start", "A,B");
        assertMockEndpointsSatisfied();
    }

    public static class MyJoiner {
        public String join(String existing, String next) {
            return existing == null ? next : existing + "+" + next;
        }
    }
}
