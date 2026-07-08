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
import org.apache.camel.component.a2a.model.AgentCapabilities;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.Skill;
import org.apache.camel.component.a2a.model.SupportedInterface;

/**
 * Resolves agent cards with layered precedence: file < bean < URI params.
 */
public final class AgentCardResolver {

    private AgentCardResolver() {
    }

    /**
     * Resolves an agent card by merging file card, bean card, and URI parameters. Precedence (lowest to highest): file
     * card, bean card, URI params.
     *
     * @param  fileCard         the card loaded from file/classpath/URL (may be null)
     * @param  beanCard         the card from bean reference (may be null)
     * @param  nameParam        the name URI parameter (may be null)
     * @param  descriptionParam the description URI parameter (may be null)
     * @param  versionParam     the version URI parameter (may be null)
     * @return                  the resolved agent card
     */
    public static AgentCard resolve(
            AgentCard fileCard, AgentCard beanCard, String nameParam, String descriptionParam, String versionParam) {
        return resolve(fileCard, beanCard, null, nameParam, descriptionParam, versionParam);
    }

    /**
     * Resolves an agent card by merging file card, bean card, URI source, and URI parameters. Plain URI sources are
     * used as a default agent name when the card does not provide one.
     *
     * @param  fileCard         the card loaded from file/classpath/URL (may be null)
     * @param  beanCard         the card from bean reference (may be null)
     * @param  agentCardSource  the endpoint path source (may be null)
     * @param  nameParam        the name URI parameter (may be null)
     * @param  descriptionParam the description URI parameter (may be null)
     * @param  versionParam     the version URI parameter (may be null)
     * @return                  the resolved agent card
     */
    public static AgentCard resolve(
            AgentCard fileCard, AgentCard beanCard, String agentCardSource,
            String nameParam, String descriptionParam, String versionParam) {

        AgentCard.Builder builder = AgentCard.builder();

        // Layer 1: file card
        if (fileCard != null) {
            applyNonNull(fileCard, builder);
        }

        // Layer 2: bean card
        if (beanCard != null) {
            applyNonNull(beanCard, builder);
        }

        // Layer 3: URI params (highest precedence)
        if (nameParam != null) {
            builder.setName(nameParam);
        }
        if (descriptionParam != null) {
            builder.setDescription(descriptionParam);
        }
        if (versionParam != null) {
            builder.setVersion(versionParam);
        }

        AgentCard resolved = builder.build();
        builder = AgentCard.builder();
        applyNonNull(resolved, builder);
        applyDefaults(builder, resolved, agentCardSource);
        AgentCard answer = builder.build();
        validate(answer);
        return answer;
    }

    private static void applyNonNull(AgentCard source, AgentCard.Builder builder) {
        if (source.getName() != null) {
            builder.setName(source.getName());
        }
        if (source.getDescription() != null) {
            builder.setDescription(source.getDescription());
        }
        if (source.getUrl() != null) {
            builder.setUrl(source.getUrl());
        }
        if (source.getVersion() != null) {
            builder.setVersion(source.getVersion());
        }
        if (source.getProvider() != null) {
            builder.setProvider(source.getProvider());
        }
        if (source.getCapabilities() != null) {
            builder.setCapabilities(source.getCapabilities());
        }
        if (!source.getSkills().isEmpty()) {
            builder.setSkills(source.getSkills());
        }
        if (!source.getSupportedInterfaces().isEmpty()) {
            builder.setSupportedInterfaces(source.getSupportedInterfaces());
        }
        if (!source.getSecuritySchemes().isEmpty()) {
            builder.setSecuritySchemes(source.getSecuritySchemes());
        }
        if (!source.getSecurityRequirements().isEmpty()) {
            builder.setSecurityRequirements(source.getSecurityRequirements());
        }
        if (source.getIconUrl() != null) {
            builder.setIconUrl(source.getIconUrl());
        }
        if (source.getDocumentationUrl() != null) {
            builder.setDocumentationUrl(source.getDocumentationUrl());
        }
        if (source.getDefaultInputModes() != null) {
            builder.setDefaultInputModes(source.getDefaultInputModes());
        }
        if (source.getDefaultOutputModes() != null) {
            builder.setDefaultOutputModes(source.getDefaultOutputModes());
        }
        if (source.getSupportsAuthenticatedExtendedCard() != null) {
            builder.setSupportsAuthenticatedExtendedCard(source.getSupportsAuthenticatedExtendedCard());
        }
        source.getAdditionalProperties().forEach(builder::setAdditionalProperty);
    }

    private static void applyDefaults(AgentCard.Builder builder, AgentCard resolved, String agentCardSource) {
        if (isBlank(resolved.getName()) && isPlainName(agentCardSource)) {
            builder.setName(agentCardSource);
        }
        if (isBlank(resolved.getVersion())) {
            builder.setVersion(A2AConstants.A2A_VERSION);
        }
        if (resolved.getDefaultInputModes() == null || resolved.getDefaultInputModes().isEmpty()) {
            builder.setDefaultInputModes(List.of("text/plain"));
        }
        if (resolved.getDefaultOutputModes() == null || resolved.getDefaultOutputModes().isEmpty()) {
            builder.setDefaultOutputModes(List.of("text/plain"));
        }
        if (resolved.getCapabilities() == null) {
            AgentCapabilities capabilities = new AgentCapabilities();
            capabilities.setStreaming(false);
            capabilities.setPushNotifications(false);
            builder.setCapabilities(capabilities);
        }
        if (resolved.getSkills().isEmpty()) {
            Skill skill = new Skill();
            skill.setId("message");
            skill.setName("Message handling");
            skill.setDescription(isBlank(resolved.getDescription())
                    ? "Handles A2A messages."
                    : resolved.getDescription());
            skill.setTags(List.of("a2a"));
            builder.setSkills(List.of(skill));
        }
        if (resolved.getSupportedInterfaces().isEmpty() && !isBlank(resolved.getUrl())) {
            SupportedInterface supportedInterface = new SupportedInterface();
            supportedInterface.setUrl(resolved.getUrl());
            supportedInterface.setProtocolBinding(A2AConstants.PROTOCOL_REST);
            supportedInterface.setProtocolVersion(A2AConstants.A2A_VERSION);
            builder.setSupportedInterfaces(List.of(supportedInterface));
        }
    }

    private static void validate(AgentCard card) {
        if (isBlank(card.getName())) {
            throw new IllegalArgumentException("Agent card name is required");
        }
        if (isBlank(card.getVersion())) {
            throw new IllegalArgumentException("Agent card version is required");
        }
        validateModes(card.getDefaultInputModes(), "defaultInputModes");
        validateModes(card.getDefaultOutputModes(), "defaultOutputModes");
        for (SupportedInterface supportedInterface : card.getSupportedInterfaces()) {
            String protocolBinding = supportedInterface.getProtocolBinding();
            if (isBlank(supportedInterface.getUrl())) {
                throw new IllegalArgumentException("Agent card supportedInterfaces entries require a URL");
            }
            if (!A2AConstants.PROTOCOL_REST.equals(protocolBinding)
                    && !A2AConstants.PROTOCOL_JSONRPC.equals(protocolBinding)) {
                throw new IllegalArgumentException("Unsupported A2A protocol binding: " + protocolBinding);
            }
        }
    }

    private static void validateModes(List<String> modes, String fieldName) {
        if (modes == null || modes.isEmpty()) {
            throw new IllegalArgumentException("Agent card " + fieldName + " is required");
        }
    }

    private static boolean isPlainName(String source) {
        return !isBlank(source)
                && !source.startsWith("classpath:")
                && !source.startsWith("file:")
                && !source.startsWith("http://")
                && !source.startsWith("https://");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
