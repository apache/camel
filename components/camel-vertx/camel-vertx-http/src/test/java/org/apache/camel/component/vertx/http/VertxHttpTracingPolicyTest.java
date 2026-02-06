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
package org.apache.camel.component.vertx.http;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;
import io.vertx.core.tracing.TracingPolicy;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxHttpTracingPolicyTest extends VertxHttpTestSupport {

    @Test
    void customTracingPolicyFromComponentConfiguration() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Vertx vertx = Vertx.builder().withTracer(new VertxTracerFactory() {
            @Override
            public VertxTracer<?, ?> tracer(TracingOptions options) {
                return new VertxTracer<>() {
                    @Override
                    public Object sendRequest(
                            Context context, SpanKind kind, TracingPolicy policy, Object request, String operation,
                            BiConsumer headers, TagExtractor tagExtractor) {
                        if (policy.equals(TracingPolicy.IGNORE)) {
                            latch.countDown();
                        }
                        return null;
                    }
                };
            }
        }).build();

        VertxHttpComponent component = new VertxHttpComponent();
        component.setVertx(vertx);
        component.setTracingPolicy(TracingPolicy.IGNORE);
        context.addComponent("vertx-http", component);

        template.sendBody(getProducerUri() + "/trace", "Test");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Expected IGNORE trace policy was not matched");
    }

    @Test
    void customTracingPolicyFromEndpointUri() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Vertx vertx = Vertx.builder().withTracer(new VertxTracerFactory() {
            @Override
            public VertxTracer<?, ?> tracer(TracingOptions options) {
                return new VertxTracer<>() {
                    @Override
                    public Object sendRequest(
                            Context context, SpanKind kind, TracingPolicy policy, Object request, String operation,
                            BiConsumer headers, TagExtractor tagExtractor) {
                        if (policy.equals(TracingPolicy.IGNORE)) {
                            latch.countDown();
                        }
                        return null;
                    }
                };
            }
        }).build();

        VertxHttpComponent component = new VertxHttpComponent();
        component.setVertx(vertx);
        context.addComponent("vertx-http", component);

        template.sendBody(getProducerUri() + "/trace?tracingPolicy=IGNORE", "Test");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Expected IGNORE trace policy was not matched");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerUri() + "/trace")
                        .log("Traced");
            }
        };
    }
}
