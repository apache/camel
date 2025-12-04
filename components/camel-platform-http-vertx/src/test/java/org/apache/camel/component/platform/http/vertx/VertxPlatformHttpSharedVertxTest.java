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

package org.apache.camel.component.platform.http.vertx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class VertxPlatformHttpSharedVertxTest {
    private static final Vertx vertx = Vertx.vertx();

    @AfterAll
    static void afterAll() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void sharedVertxInstanceNotClosed() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
            configuration.setBindPort(AvailablePortFinder.getNextAvailable());

            context.getRegistry().bind("vertx", vertx);
            context.addService(new VertxPlatformHttpServer(configuration));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("platform-http:/test").setBody().constant("Hello World");
                }
            });
            context.start();
        }

        // Verify shutdown of VertxPlatformHttpServer did not close the shared Vertx instance
        CountDownLatch latch = new CountDownLatch(1);
        vertx.setTimer(1, event -> latch.countDown());
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}
