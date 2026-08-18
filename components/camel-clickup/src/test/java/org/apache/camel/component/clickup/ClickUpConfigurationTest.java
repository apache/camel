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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickUpConfigurationTest extends ClickUpTestSupport {

    private static final String BASE_URL = "https://mock-api.clickup.com";
    private static final Set<String> EVENTS = new HashSet<>(Arrays.asList("taskTimeTrackedUpdated"));

    @Test
    void testClickUpConfiguration() {
        ClickUpEndpoint endpoint = (ClickUpEndpoint) context().getEndpoints().stream()
                .filter(e -> e instanceof ClickUpEndpoint).findAny().get();
        ClickUpConfiguration config = endpoint.getConfiguration();

        assertThat(config.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(config.getBaseUrl()).isEqualTo(BASE_URL);
        assertThat(config.getAuthorizationToken()).isEqualTo(AUTHORIZATION_TOKEN);
        assertThat(config.getWebhookSecret()).isEqualTo(WEBHOOK_SECRET);
        assertThat(config.getEvents()).isEqualTo(EVENTS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("webhook:clickup:%s?baseUrl=%s&authorizationToken=%s&webhookSecret=%s&events=%s&webhookAutoRegister=false",
                        WORKSPACE_ID, BASE_URL, AUTHORIZATION_TOKEN, WEBHOOK_SECRET, String.join(",", EVENTS))
                        .log("Received: ${body}");
            }
        };
    }

}
