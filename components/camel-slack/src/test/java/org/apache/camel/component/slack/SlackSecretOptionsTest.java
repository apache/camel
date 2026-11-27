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
package org.apache.camel.component.slack;

import java.util.Set;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.URISupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The webhook URL of Slack is the credential to post to the channel, so it must be masked like the token.
 */
public class SlackSecretOptionsTest extends CamelTestSupport {

    private static final String WEBHOOK_URL = "https://hooks.slack.com/services/T0000/B0000/abcdefghijklmnop";

    @Test
    public void testSanitizeUriMasksWebhookUrl() {
        String sanitized = URISupport.sanitizeUri("slack:#general?webhookUrl=" + WEBHOOK_URL);

        assertFalse(sanitized.contains("hooks.slack.com"), sanitized);
        assertEquals("slack:#general?webhookUrl=xxxxxx", sanitized);
    }

    @Test
    public void testSanitizeUriMasksRawWebhookUrl() {
        String sanitized = URISupport.sanitizeUri("slack:#general?iconEmoji=:ghost:&webhookUrl=RAW(" + WEBHOOK_URL + ")");

        assertFalse(sanitized.contains("hooks.slack.com"), sanitized);
        assertEquals("slack:#general?iconEmoji=:ghost:&webhookUrl=xxxxxx", sanitized);
    }

    @Test
    public void testEndpointToStringMasksWebhookUrl() {
        Endpoint endpoint = context.getEndpoint("slack:#general?webhookUrl=RAW(" + WEBHOOK_URL + ")");

        assertEquals(WEBHOOK_URL, ((SlackEndpoint) endpoint).getWebhookUrl());
        assertFalse(endpoint.toString().contains("hooks.slack.com"), endpoint.toString());
    }

    @Test
    public void testWebhookUrlIsSecretOption() {
        EndpointUriFactory factory = context.getCamelContextExtension().getEndpointUriFactory("slack");
        Set<String> secrets = factory.secretPropertyNames();

        assertTrue(secrets.contains("webhookUrl"), secrets.toString());
        assertTrue(secrets.contains("token"), secrets.toString());
        assertTrue(SensitiveUtils.containsSensitive("camel.component.slack.webhookUrl"));
    }
}
