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

import org.apache.camel.component.clickup.model.Webhook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The configuration and the webhook model are printed when logged, so their toString() masks the credentials.
 */
public class ClickUpToStringTest {

    @Test
    public void configurationMasksTheCredentials() {
        ClickUpConfiguration configuration = new ClickUpConfiguration();
        configuration.setWorkspaceId(42L);
        configuration.setWebhookSecret("the-webhook-secret");
        configuration.setAuthorizationToken("the-authorization-token");

        assertThat(configuration.toString())
                .contains("workspaceId=42")
                .contains("webhookSecret='xxxxxx'")
                .contains("authorizationToken='xxxxxx'")
                .doesNotContain("the-webhook-secret")
                .doesNotContain("the-authorization-token");
    }

    @Test
    public void configurationShowsACredentialThatIsNotSet() {
        assertThat(new ClickUpConfiguration().toString())
                .contains("webhookSecret='null'")
                .contains("authorizationToken='null'");
    }

    @Test
    public void webhookMasksTheSecret() {
        Webhook webhook = new Webhook();
        webhook.setSecret("the-webhook-secret");

        assertThat(webhook.toString())
                .contains("secret='xxxxxx'")
                .doesNotContain("the-webhook-secret");
    }
}
