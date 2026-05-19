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
package org.apache.camel.component.a2a.card;

import java.util.List;

import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.SupportedInterface;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCardResolverTest {

    @Test
    void uriParamsOverrideCardFields() {
        AgentCard fileCard = AgentCard.builder()
                .setName("File Name")
                .setVersion("1.0.0")
                .setDescription("File Description")
                .build();

        AgentCard result = AgentCardResolver.resolve(fileCard, null, "Override Name", null, "2.0.0");

        assertThat(result.getName()).isEqualTo("Override Name");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
        assertThat(result.getDescription()).isEqualTo("File Description");
    }

    @Test
    void beanOverridesFileButUriWins() {
        AgentCard fileCard = AgentCard.builder()
                .setName("File Name")
                .setVersion("1.0.0")
                .setDescription("File Description")
                .build();

        AgentCard beanCard = AgentCard.builder()
                .setName("Bean Name")
                .setVersion("1.5.0")
                .build();

        AgentCard result = AgentCardResolver.resolve(fileCard, beanCard, "URI Name", null, null);

        assertThat(result.getName()).isEqualTo("URI Name");
        assertThat(result.getVersion()).isEqualTo("1.5.0");
        assertThat(result.getDescription()).isEqualTo("File Description");
    }

    @Test
    void emptyCardBuiltFromParams() {
        AgentCard fileCard = AgentCard.builder().build();

        AgentCard result = AgentCardResolver.resolve(fileCard, null, "Param Name", "Param Desc", "3.0.0");

        assertThat(result.getName()).isEqualTo("Param Name");
        assertThat(result.getDescription()).isEqualTo("Param Desc");
        assertThat(result.getVersion()).isEqualTo("3.0.0");
    }

    @Test
    void nullCardHandledGracefully() {
        AgentCard result = AgentCardResolver.resolve(null, null, "Name", "Desc", "1.0");

        assertThat(result.getName()).isEqualTo("Name");
        assertThat(result.getDescription()).isEqualTo("Desc");
        assertThat(result.getVersion()).isEqualTo("1.0");
    }

    @Test
    void plainSourceProvidesPreviewDefaults() {
        AgentCard result = AgentCardResolver.resolve(null, null, "plain-agent", null, null, null);

        assertThat(result.getName()).isEqualTo("plain-agent");
        assertThat(result.getVersion()).isEqualTo(A2AConstants.A2A_VERSION);
        assertThat(result.getDefaultInputModes()).containsExactly("text/plain");
        assertThat(result.getDefaultOutputModes()).containsExactly("text/plain");
        assertThat(result.getCapabilities()).isNotNull();
        assertThat(result.getCapabilities().getStreaming()).isFalse();
        assertThat(result.getCapabilities().getPushNotifications()).isFalse();
        assertThat(result.getSkills()).hasSize(1);
        assertThat(result.getSkills().get(0).getId()).isEqualTo("message");
        assertThat(result.getSkills().get(0).getTags()).containsExactly("a2a");
    }

    @Test
    void defaultsSupportedInterfaceFromCardUrl() {
        AgentCard fileCard = AgentCard.builder()
                .setName("URL Agent")
                .setUrl("https://agent.example.com/a2a")
                .build();

        AgentCard result = AgentCardResolver.resolve(fileCard, null, null, null, null);

        assertThat(result.getSupportedInterfaces()).hasSize(1);
        SupportedInterface supportedInterface = result.getSupportedInterfaces().get(0);
        assertThat(supportedInterface.getUrl()).isEqualTo("https://agent.example.com/a2a");
        assertThat(supportedInterface.getProtocolBinding()).isEqualTo(A2AConstants.PROTOCOL_REST);
        assertThat(supportedInterface.getProtocolVersion()).isEqualTo(A2AConstants.A2A_VERSION);
    }

    @Test
    void rejectsUnsupportedSupportedInterfaceBinding() {
        SupportedInterface supportedInterface = new SupportedInterface();
        supportedInterface.setUrl("https://agent.example.com");
        supportedInterface.setProtocolBinding("UNSUPPORTED");
        AgentCard card = AgentCard.builder()
                .setName("Agent")
                .setSupportedInterfaces(List.of(supportedInterface))
                .build();

        assertThatThrownBy(() -> AgentCardResolver.resolve(card, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported A2A protocol binding");
    }

    @Test
    void fileCardUsedWhenNoOverrides() {
        AgentCard fileCard = AgentCard.builder()
                .setName("File Name")
                .setDescription("File Description")
                .setVersion("1.0.0")
                .build();

        AgentCard result = AgentCardResolver.resolve(fileCard, null, null, null, null);

        assertThat(result.getName()).isEqualTo("File Name");
        assertThat(result.getDescription()).isEqualTo("File Description");
        assertThat(result.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void mergesFullDynamicCardMetadata() {
        AgentCard fileCard = AgentCard.builder()
                .setName("File Name")
                .setDefaultInputModes(List.of("text/plain"))
                .setDefaultOutputModes(List.of("application/json"))
                .setSupportsAuthenticatedExtendedCard(Boolean.FALSE)
                .setAdditionalProperty("x-file-extension", "file")
                .setAdditionalProperty("x-overridden-extension", "file")
                .build();

        AgentCard beanCard = AgentCard.builder()
                .setName("Bean Name")
                .setDefaultInputModes(List.of("application/json"))
                .setSupportsAuthenticatedExtendedCard(Boolean.TRUE)
                .setAdditionalProperty("x-bean-extension", "bean")
                .setAdditionalProperty("x-overridden-extension", "bean")
                .build();

        AgentCard result = AgentCardResolver.resolve(fileCard, beanCard, null, null, null);

        assertThat(result.getName()).isEqualTo("Bean Name");
        assertThat(result.getDefaultInputModes()).containsExactly("application/json");
        assertThat(result.getDefaultOutputModes()).containsExactly("application/json");
        assertThat(result.getSupportsAuthenticatedExtendedCard()).isTrue();
        assertThat(result.getAdditionalProperties())
                .containsEntry("x-file-extension", "file")
                .containsEntry("x-bean-extension", "bean")
                .containsEntry("x-overridden-extension", "bean");
    }
}
