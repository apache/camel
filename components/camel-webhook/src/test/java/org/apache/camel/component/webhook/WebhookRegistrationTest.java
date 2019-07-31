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
package org.apache.camel.component.webhook;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.webhook.support.TestComponent;
import org.apache.camel.spi.Registry;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

public class WebhookRegistrationTest extends WebhookTestBase {

    private AtomicInteger registered;

    private AtomicInteger unregistered;

    @Before
    public void initialize() {
        this.registered = new AtomicInteger(0);
        this.unregistered = new AtomicInteger(0);
    }

    @Test
    public void testContext() throws Exception {
        context().addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration()
                        .host("0.0.0.0")
                        .port(port);

                from("webhook:wb-delegate://xx")
                        .transform(body().prepend("msg: "));

                from("webhook:wb-delegate://xx?webhookPath=/p2&webhookAutoRegister=false")
                        .transform(body().prepend("uri: "));
            }
        });

        assertEquals(0, registered.get());
        assertEquals(0, unregistered.get());

        context.start();

        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> registered.get() == 1);
        assertEquals(0, unregistered.get());

        context.stop();
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(() -> unregistered.get() == 1);
        assertEquals(1, registered.get());
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        registry.bind("wb-delegate-component", new TestComponent(endpoint -> {
            endpoint.setWebhookHandler(proc -> ex -> {
                ex.getMessage().setBody("webhook");
                proc.process(ex);
            });

            endpoint.setRegisterWebhook(() -> this.registered.incrementAndGet());
            endpoint.setUnregisterWebhook(() -> this.unregistered.incrementAndGet());
        }));
    }
}
