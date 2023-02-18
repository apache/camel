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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.webhook.support.TestComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.awaitility.Awaitility.await;

public class WebhookRoutePolicyTest {
    @ParameterizedTest
    @EnumSource(WebhookAction.class)
    public void testPolicy(WebhookAction action) throws Exception {
        Map<WebhookAction, AtomicInteger> counters = new ConcurrentHashMap<>();
        counters.put(WebhookAction.REGISTER, new AtomicInteger());
        counters.put(WebhookAction.UNREGISTER, new AtomicInteger());

        WebhookComponent webhookComponent = new WebhookComponent();
        webhookComponent.getConfiguration().setWebhookAutoRegister(false);

        TestComponent testComponent = new TestComponent(e -> {
            e.setUnregisterWebhook(() -> counters.get(WebhookAction.REGISTER).incrementAndGet());
            e.setRegisterWebhook(() -> counters.get(WebhookAction.UNREGISTER).incrementAndGet());
        });

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getCamelContextExtension().getRegistry().bind("dummy", testComponent);
            context.getCamelContextExtension().getRegistry().bind("webhook", webhookComponent);
            context.addRoutePolicyFactory(new WebhookRoutePolicyFactory(action));

            await().atMost(10, TimeUnit.SECONDS).until(() -> counters.get(action).get() == 0);
        }
    }
}
