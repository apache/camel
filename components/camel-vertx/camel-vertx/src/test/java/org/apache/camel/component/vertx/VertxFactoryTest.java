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
package org.apache.camel.component.vertx;

import java.util.concurrent.atomic.AtomicBoolean;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.VertxMetrics;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxFactoryTest extends VertxBaseTestSupport {

    private final AtomicBoolean metricsCreated = new AtomicBoolean();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // The metrics factory is only consulted when Vert.x is built by this builder
        VertxMetricsFactory metricsFactory = options -> {
            metricsCreated.set(true);
            return new VertxMetrics() {
            };
        };

        VertxComponent component = new VertxComponent();
        component.setVertxFactory(Vertx.builder().with(new VertxOptions()).withMetrics(metricsFactory));
        context.addComponent("vertx", component);
        return context;
    }

    @Test
    public void testVertxFactory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("vertx:foo", "Hello World");

        mock.assertIsSatisfied();
        assertTrue(metricsCreated.get(), "Vert.x was not created by the configured vertxFactory");
        assertTrue(context.getComponent("vertx", VertxComponent.class).getVertx().isMetricsEnabled());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("vertx:foo").to("mock:result");
            }
        };
    }
}
