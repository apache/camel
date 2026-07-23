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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Immutable A2A agent card. Use {@link #builder()} to construct instances.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(builder = AgentCard.Builder.class)
public class AgentCard {
    private final String name;
    private final String description;
    private final String url;
    private final String version;
    private final AgentProvider provider;
    private final AgentCapabilities capabilities;
    private final List<Skill> skills;
    private final List<SupportedInterface> supportedInterfaces;
    private final Map<String, SecurityScheme> securitySchemes;
    private final List<SecurityRequirement> securityRequirements;
    private final List<Map<String, List<String>>> security;
    private final String iconUrl;
    private final String documentationUrl;
    private final List<String> defaultInputModes;
    private final List<String> defaultOutputModes;
    private final Boolean supportsAuthenticatedExtendedCard;
    private final Map<String, Object> additionalProperties;

    private AgentCard(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.url = builder.url;
        this.version = builder.version;
        this.provider = builder.provider;
        this.capabilities = builder.capabilities;
        this.skills = builder.skills != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.skills))
                : Collections.emptyList();
        this.supportedInterfaces = builder.supportedInterfaces != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.supportedInterfaces))
                : Collections.emptyList();
        this.securitySchemes = builder.securitySchemes != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.securitySchemes))
                : Collections.emptyMap();
        this.security = builder.security != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.security))
                : Collections.emptyList();
        this.securityRequirements = builder.securityRequirements != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.securityRequirements))
                : Collections.unmodifiableList(toSecurityRequirements(this.security));
        this.iconUrl = builder.iconUrl;
        this.documentationUrl = builder.documentationUrl;
        this.defaultInputModes = builder.defaultInputModes != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.defaultInputModes))
                : null;
        this.defaultOutputModes = builder.defaultOutputModes != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.defaultOutputModes))
                : null;
        this.supportsAuthenticatedExtendedCard = builder.supportsAuthenticatedExtendedCard;
        this.additionalProperties = builder.additionalProperties != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.additionalProperties))
                : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getVersion() {
        return version;
    }

    public AgentProvider getProvider() {
        return provider;
    }

    public AgentCapabilities getCapabilities() {
        return capabilities;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public List<SupportedInterface> getSupportedInterfaces() {
        return supportedInterfaces;
    }

    public Map<String, SecurityScheme> getSecuritySchemes() {
        return securitySchemes;
    }

    public List<SecurityRequirement> getSecurityRequirements() {
        return securityRequirements;
    }

    @JsonIgnore
    public List<Map<String, List<String>>> getSecurity() {
        return security;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getDocumentationUrl() {
        return documentationUrl;
    }

    public List<String> getDefaultInputModes() {
        return defaultInputModes;
    }

    public List<String> getDefaultOutputModes() {
        return defaultOutputModes;
    }

    public Boolean getSupportsAuthenticatedExtendedCard() {
        return supportsAuthenticatedExtendedCard;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonPOJOBuilder(withPrefix = "set")
    public static class Builder {
        private String name;
        private String description;
        private String url;
        private String version;
        private AgentProvider provider;
        private AgentCapabilities capabilities;
        private List<Skill> skills;
        private List<SupportedInterface> supportedInterfaces;
        private Map<String, SecurityScheme> securitySchemes;
        private List<SecurityRequirement> securityRequirements;
        private List<Map<String, List<String>>> security;
        private String iconUrl;
        private String documentationUrl;
        private List<String> defaultInputModes;
        private List<String> defaultOutputModes;
        private Boolean supportsAuthenticatedExtendedCard;
        private Map<String, Object> additionalProperties;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder setProvider(AgentProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder setCapabilities(AgentCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder setSkills(List<Skill> skills) {
            this.skills = skills;
            return this;
        }

        public Builder setSupportedInterfaces(List<SupportedInterface> supportedInterfaces) {
            this.supportedInterfaces = supportedInterfaces;
            return this;
        }

        public Builder setSecuritySchemes(Map<String, SecurityScheme> securitySchemes) {
            this.securitySchemes = securitySchemes;
            return this;
        }

        public Builder setSecurityRequirements(List<SecurityRequirement> securityRequirements) {
            this.securityRequirements = securityRequirements;
            return this;
        }

        @JsonProperty("security")
        public Builder setSecurity(List<Map<String, List<String>>> security) {
            this.security = security;
            return this;
        }

        public Builder setIconUrl(String iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        public Builder setDocumentationUrl(String documentationUrl) {
            this.documentationUrl = documentationUrl;
            return this;
        }

        public Builder setDefaultInputModes(List<String> defaultInputModes) {
            this.defaultInputModes = defaultInputModes;
            return this;
        }

        public Builder setDefaultOutputModes(List<String> defaultOutputModes) {
            this.defaultOutputModes = defaultOutputModes;
            return this;
        }

        public Builder setSupportsAuthenticatedExtendedCard(Boolean supportsAuthenticatedExtendedCard) {
            this.supportsAuthenticatedExtendedCard = supportsAuthenticatedExtendedCard;
            return this;
        }

        @JsonAnySetter
        public Builder setAdditionalProperty(String key, Object value) {
            if (this.additionalProperties == null) {
                this.additionalProperties = new LinkedHashMap<>();
            }
            this.additionalProperties.put(key, value);
            return this;
        }

        public AgentCard build() {
            return new AgentCard(this);
        }
    }

    private static List<SecurityRequirement> toSecurityRequirements(List<Map<String, List<String>>> legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return Collections.emptyList();
        }
        List<SecurityRequirement> requirements = new ArrayList<>(legacy.size());
        for (Map<String, List<String>> requirement : legacy) {
            requirements.add(SecurityRequirement.fromScopeMap(requirement));
        }
        return requirements;
    }
}
