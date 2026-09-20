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
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A component resolved while the routes start is built and initialized but not started, and an endpoint that starts
 * before it found no Vert.x (CAMEL-24822): the managed Vert.x is created on first use.
 */
public class VertxHttpComponentLazyVertxTest {

    @Test
    public void testVertxBeforeStart() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            VertxHttpComponent component = new VertxHttpComponent();
            component.setCamelContext(context);
            component.init();

            Vertx vertx = component.getVertx();
            assertNotNull(vertx, "a Vert.x before the component is started");

            component.start();
            assertSame(vertx, component.getVertx(), "the same Vert.x after start");

            component.stop();
            assertNull(component.getVertx(), "no new Vert.x once stopped");
        }
    }

    @Test
    public void testConfiguredVertxIsKept() throws Exception {
        Vertx own = Vertx.vertx();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            VertxHttpComponent component = new VertxHttpComponent();
            component.setCamelContext(context);
            component.setVertx(own);
            component.init();
            assertSame(own, component.getVertx());
            component.start();
            assertSame(own, component.getVertx());
            component.stop();
        } finally {
            own.close();
        }
    }
}
