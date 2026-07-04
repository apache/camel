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
package org.apache.camel.component.a2a.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.component.a2a.A2AConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentCardTest {

    private final ObjectMapper objectMapper;

    public AgentCardTest() {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Test
    void deserializesFromJson() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/cards/test-agent-card.json")) {
            assertNotNull(is, "test-agent-card.json not found");

            AgentCard card = objectMapper.readValue(is, AgentCard.class);

            assertNotNull(card);
            assertEquals("Weather Agent", card.getName());
            assertEquals("Provides weather forecasts", card.getDescription());
            assertEquals("1.0.0", card.getVersion());

            assertNotNull(card.getProvider());
            assertEquals("ACME Corp", card.getProvider().getName());
            assertEquals("https://acme.example.com", card.getProvider().getUrl());

            assertNotNull(card.getCapabilities());
            assertFalse(card.getCapabilities().isStreaming());
            assertFalse(card.getCapabilities().isPushNotifications());

            assertNotNull(card.getSkills());
            assertEquals(1, card.getSkills().size());
            Skill skill = card.getSkills().get(0);
            assertEquals("get-forecast", skill.getId());
            assertEquals("Get Forecast", skill.getName());
            assertEquals("Returns weather forecast for a location", skill.getDescription());
            assertEquals(2, skill.getTags().size());
            assertEquals("weather", skill.getTags().get(0));
            assertEquals("forecast", skill.getTags().get(1));

            assertNotNull(card.getSupportedInterfaces());
            assertEquals(1, card.getSupportedInterfaces().size());
            SupportedInterface iface = card.getSupportedInterfaces().get(0);
            assertEquals("https://weather.example.com", iface.getUrl());
            assertEquals(A2AConstants.PROTOCOL_REST, iface.getProtocolBinding());
        }
    }

    @Test
    void serializationRoundtrip() throws IOException {
        AgentProvider provider = new AgentProvider();
        provider.setName("Test Provider");
        provider.setUrl("https://test.example.com");

        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        capabilities.setPushNotifications(false);

        Skill skill = new Skill();
        skill.setId("test-skill");
        skill.setName("Test Skill");
        skill.setDescription("Test skill description");
        skill.getTags().add("test");

        SupportedInterface iface = new SupportedInterface();
        iface.setUrl("https://test.example.com/a2a");
        iface.setProtocolBinding("rest");
        iface.setProtocolVersion("1.0");

        AgentCard card = AgentCard.builder()
                .setName("Test Agent")
                .setDescription("Test description")
                .setVersion("2.0.0")
                .setProvider(provider)
                .setCapabilities(capabilities)
                .setSkills(List.of(skill))
                .setSupportedInterfaces(List.of(iface))
                .build();

        String json = objectMapper.writeValueAsString(card);
        assertNotNull(json);

        AgentCard deserialized = objectMapper.readValue(json, AgentCard.class);
        assertNotNull(deserialized);
        assertEquals("Test Agent", deserialized.getName());
        assertEquals("Test description", deserialized.getDescription());
        assertEquals("2.0.0", deserialized.getVersion());
        assertEquals("Test Provider", deserialized.getProvider().getName());
        assertEquals(true, deserialized.getCapabilities().isStreaming());
        assertEquals(1, deserialized.getSkills().size());
        assertEquals("test-skill", deserialized.getSkills().get(0).getId());
        assertEquals(1, deserialized.getSupportedInterfaces().size());
        assertEquals("https://test.example.com/a2a", deserialized.getSupportedInterfaces().get(0).getUrl());
        assertEquals(A2AConstants.PROTOCOL_REST, deserialized.getSupportedInterfaces().get(0).getProtocolBinding());
    }

    @Test
    void serializesSecurityRequirementsAndKeepsLegacySecurityWriteOnly() throws Exception {
        AgentCard card = AgentCard.builder()
                .setName("Secure Agent")
                .setVersion("1.0.0")
                .setSecuritySchemes(Map.of("oauth", SecurityScheme.oauth2(null)))
                .setSecurityRequirements(List.of(SecurityRequirement.of("oauth", List.of("message:send"))))
                .build();

        String json = objectMapper.writeValueAsString(card);
        assertFalse(json.contains("\"security\""));

        AgentCard parsed = objectMapper.readValue(json, AgentCard.class);
        assertEquals(1, parsed.getSecurityRequirements().size());
        assertEquals(List.of("message:send"),
                parsed.getSecurityRequirements().get(0).asScopeMap().get("oauth"));
    }

    @Test
    void legacySecurityInputIsConvertedToSecurityRequirements() throws Exception {
        AgentCard card = objectMapper.readValue("""
                {
                  "name": "Legacy Secure Agent",
                  "version": "1.0.0",
                  "securitySchemes": {
                    "apikey": {
                      "apiKeySecurityScheme": {
                        "location": "header",
                        "name": "X-API-Key"
                      }
                    }
                  },
                  "security": [
                    { "apikey": ["tasks:read"] }
                  ]
                }
                """, AgentCard.class);

        assertEquals(1, card.getSecurityRequirements().size());
        assertEquals(List.of("tasks:read"),
                card.getSecurityRequirements().get(0).asScopeMap().get("apikey"));
        assertFalse(objectMapper.writeValueAsString(card).contains("\"security\""));
    }

    @Test
    void supportedInterfaceAcceptsLegacyProtocolBindingAlias() {
        SupportedInterface iface = new SupportedInterface();
        iface.setProtocolBinding("jsonrpc");

        assertEquals(A2AConstants.PROTOCOL_JSONRPC, iface.getProtocolBinding());
    }

    @Test
    void collectionsAreUnmodifiable() {
        Skill skill = new Skill();
        skill.setId("s1");

        AgentCard card = AgentCard.builder()
                .setName("Test")
                .setSkills(List.of(skill))
                .build();

        assertNotNull(card.getSkills());
        assertEquals(1, card.getSkills().size());

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> card.getSkills().add(new Skill()));
    }
}
