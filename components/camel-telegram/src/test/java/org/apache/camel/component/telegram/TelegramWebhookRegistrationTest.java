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
package org.apache.camel.component.telegram;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Test;

import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests a producer that sends media information.
 */
public class TelegramWebhookRegistrationTest extends TelegramTestSupport {

    @Test
    public void testAutomaticRegistration() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("webhook:telegram:bots?authorizationToken=mock-token").to("mock:endpoint");
            }
        });
        context().start();
        verify(this.currentMockService()).setWebhook(eq("mock-token"), anyString());
        context().stop();
        waitAtMost(5, TimeUnit.SECONDS).until(() -> context().getStatus() == ServiceStatus.Stopped);
        verify(this.currentMockService()).removeWebhook("mock-token");
    }

    @Test
    public void testNoRegistration() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("webhook:telegram:bots?authorizationToken=mock-token&webhookAutoRegister=false").to("mock:endpoint");
            }
        });
        context().start();
        verify(this.currentMockService(), never()).setWebhook(eq("mock-token"), anyString());
        context().stop();
        waitAtMost(5, TimeUnit.SECONDS).until(() -> context().getStatus() == ServiceStatus.Stopped);
        verify(this.currentMockService(),  never()).removeWebhook("mock-token");
    }

    @Override
    protected void doPreSetup() throws Exception {
        TelegramService api = mockTelegramService();
        when(api.setWebhook(anyString(), anyString())).thenReturn(true);
        when(api.removeWebhook(anyString())).thenReturn(true);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
