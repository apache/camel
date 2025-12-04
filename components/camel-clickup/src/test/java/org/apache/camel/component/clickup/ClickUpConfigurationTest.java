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

package org.apache.camel.component.clickup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.junit.jupiter.api.Test;

public class ClickUpConfigurationTest extends ClickUpTestSupport {

    private static final Long WORKSPACE_ID = 12345L;
    private static final String BASE_URL = "https://mock-api.clickup.com";
    private static final String AUTHORIZATION_TOKEN = "mock-authorization-token";
    private static final String WEBHOOK_SECRET = "mock-webhook-secret";
    private static final Set<String> EVENTS = new HashSet<>(Arrays.asList("taskTimeTrackedUpdated"));

    @Test
    public void testClickUpConfiguration() {
        ClickUpEndpoint endpoint = (ClickUpEndpoint) context().getEndpoints().stream()
                .filter(e -> e instanceof ClickUpEndpoint)
                .findAny()
                .get();
        ClickUpConfiguration config = endpoint.getConfiguration();

        assertEquals(WORKSPACE_ID, config.getWorkspaceId());
        assertEquals(BASE_URL, config.getBaseUrl());
        assertEquals(AUTHORIZATION_TOKEN, config.getAuthorizationToken());
        assertEquals(WEBHOOK_SECRET, config.getWebhookSecret());
        assertEquals(EVENTS, config.getEvents());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("webhook:clickup:" + WORKSPACE_ID + "?baseUrl=" + BASE_URL + "&authorizationToken="
                                + AUTHORIZATION_TOKEN
                                + "&webhookSecret=" + WEBHOOK_SECRET + "&events=" + String.join(",", EVENTS)
                                + "&webhookAutoRegister=false")
                        .log("Received: ${body}");
            }
        };
    }
}
