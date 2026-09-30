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
package org.apache.camel.impl.engine;

import java.time.Duration;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route whose restarts were exhausted, and which is then removed, is no longer regarded as exhausted (or unhealthy).
 */
public class DefaultSupervisingRouteControllerExhaustedRemoveTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRemoveAfterExhausted() throws Exception {
        context.addComponent("flaky", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new DefaultEndpoint(uri, this) {
                    @Override
                    public Producer createProducer() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Consumer createConsumer(Processor processor) {
                        return new DefaultConsumer(this, processor) {
                            @Override
                            protected void doStart() throws Exception {
                                throw new IllegalStateException("Cannot connect");
                            }
                        };
                    }
                };
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("flaky:start").routeId("flaky").to("mock:result");
            }
        });

        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(10);
        src.setBackOffMaxAttempts(2);
        src.setInitialDelay(10);
        src.setUnhealthyOnExhausted(true);
        context.start();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, src.getExhaustedRoutes().size()));
        assertTrue(((DefaultSupervisingRouteController) src).hasUnhealthyRoutes());

        // the route is given up and removed
        assertTrue(context.getRouteController().getRouteStatus("flaky").isStopped());
        assertTrue(context.removeRoute("flaky"));

        assertNull(context.getRoute("flaky"));
        assertEquals(0, src.getExhaustedRoutes().size(), "the removed route should no longer be exhausted");
        assertEquals(0, src.getControlledRoutes().size());
        assertNull(src.getRestartException("flaky"));
        assertFalse(((DefaultSupervisingRouteController) src).hasUnhealthyRoutes(),
                "the removed route should no longer be unhealthy");
    }
}
