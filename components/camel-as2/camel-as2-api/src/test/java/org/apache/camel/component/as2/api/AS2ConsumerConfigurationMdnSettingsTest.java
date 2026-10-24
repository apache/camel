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
package org.apache.camel.component.as2.api;

import org.apache.camel.component.as2.api.AS2ServerConnection.AS2ConsumerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The asynchronous MDN settings (the credentials attached to an asynchronous MDN and the hosts it may be delivered to)
 * are part of the per-path {@link AS2ConsumerConfiguration}, so the consumers sharing a server connection each keep
 * their own. The fallback configuration of the root path carries the values given to the connection's constructor.
 */
class AS2ConsumerConfigurationMdnSettingsTest {

    @Test
    void configurationCarriesTheMdnSettings() {
        AS2ConsumerConfiguration config = new AS2ConsumerConfiguration(
                AS2SignatureAlgorithm.SHA256WITHRSA, null, null, null, null,
                "user", "password", "token", "receipts.example.com");

        assertEquals(AS2SignatureAlgorithm.SHA256WITHRSA, config.getSigningAlgorithm());
        assertEquals("user", config.getMdnUserName());
        assertEquals("password", config.getMdnPassword());
        assertEquals("token", config.getMdnAccessToken());
        assertEquals("receipts.example.com", config.getAsyncMdnAllowedHosts());
    }

    @Test
    void configurationWithoutMdnSettingsHasNone() {
        AS2ConsumerConfiguration config = new AS2ConsumerConfiguration(
                AS2SignatureAlgorithm.SHA256WITHRSA, null, null, null, null);

        assertNull(config.getMdnUserName());
        assertNull(config.getMdnPassword());
        assertNull(config.getMdnAccessToken());
        assertNull(config.getAsyncMdnAllowedHosts());
    }

    @Test
    void rootPathFallbackCarriesTheConnectionSettings() throws Exception {
        AS2ServerConnection connection = new AS2ServerConnection(
                "1.1", "AS2ConsumerConfigurationMdnSettingsTest Server", "server.example.com", 0,
                AS2SignatureAlgorithm.SHA256WITHRSA, null, null, null, "TBD", null, null,
                "user", "password", "token", "receipts.example.com");
        try {
            AS2ConsumerConfiguration fallback = connection.getConfigurationForPath("/").orElseThrow();
            assertEquals("user", fallback.getMdnUserName());
            assertEquals("password", fallback.getMdnPassword());
            assertEquals("token", fallback.getMdnAccessToken());
            assertEquals("receipts.example.com", fallback.getAsyncMdnAllowedHosts());

            // a consumer registered for another path keeps its own settings, not the fallback's
            connection.registerConsumerConfiguration("/partner", new AS2ConsumerConfiguration(
                    AS2SignatureAlgorithm.SHA256WITHRSA, null, null, null, null,
                    null, null, "partner-token", "partner.example.com"));
            AS2ConsumerConfiguration partner = connection.getConfigurationForPath("/partner").orElseThrow();
            assertNull(partner.getMdnUserName());
            assertNull(partner.getMdnPassword());
            assertEquals("partner-token", partner.getMdnAccessToken());
            assertEquals("partner.example.com", partner.getAsyncMdnAllowedHosts());
            assertEquals("user", connection.getConfigurationForPath("/").orElseThrow().getMdnUserName());

            // registering the root path again replaces the fallback
            connection.registerConsumerConfiguration("/", new AS2ConsumerConfiguration(
                    AS2SignatureAlgorithm.SHA256WITHRSA, null, null, null, null));
            AS2ConsumerConfiguration replaced = connection.getConfigurationForPath("/").orElseThrow();
            assertNull(replaced.getMdnUserName());
            assertNull(replaced.getMdnAccessToken());
            assertNull(replaced.getAsyncMdnAllowedHosts());
        } finally {
            connection.close();
        }
    }
}
