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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxHttpCustomVertxTest {

    @Test
    public void testCustomVertx() {
        Vertx vertx = Vertx.vertx();

        CamelContext context = new DefaultCamelContext();
        VertxHttpComponent component = new VertxHttpComponent();
        component.setVertx(vertx);

        context.start();
        try {
            assertSame(vertx, component.getVertx());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCustomVertxFromRegistry() {
        Vertx vertx = Vertx.vertx();

        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("vertx", vertx);
        context.start();
        try {
            VertxHttpComponent component = context.getComponent("vertx-http", VertxHttpComponent.class);
            assertSame(vertx, component.getVertx());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testCustomVertxOptions() {
        MetricsOptions metrics = new MetricsOptions();
        metrics.setEnabled(true);
        metrics.setFactory(options -> DummyVertxMetrics.INSTANCE);

        VertxOptions options = new VertxOptions();
        options.setMetricsOptions(metrics);

        CamelContext context = new DefaultCamelContext();
        VertxHttpComponent component = new VertxHttpComponent();
        component.setVertxOptions(options);

        context.addComponent("vertx-http", component);
        context.start();
        try {
            Vertx vertx = component.getVertx();
            assertTrue(vertx.isMetricsEnabled());
        } finally {
            context.stop();
        }
    }
}
