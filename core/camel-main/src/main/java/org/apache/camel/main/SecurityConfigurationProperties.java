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
package org.apache.camel.main;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Security policy configuration for Camel applications.
 * <p>
 * Controls how Camel reacts to insecure configuration at startup. Policies can be set globally or per security
 * category:
 * <ul>
 * <li>{@code allow} — no warnings, allow the configuration</li>
 * <li>{@code warn} — log a warning at startup (default)</li>
 * <li>{@code fail} — throw an exception and prevent startup</li>
 * </ul>
 *
 * @since 4.19.0
 */
@Configurer(extended = true)
public class SecurityConfigurationProperties implements BootstrapCloseable {

    private static final Set<String> VALID_POLICIES = Set.of("allow", "warn", "fail");

    private MainConfigurationProperties parent;

    @Metadata(defaultValue = "warn", enums = "allow,warn,fail",
              description = "Global security policy applied to all categories unless overridden."
                            + " Controls how Camel reacts when insecure configuration is detected at startup.")
    private String policy = "warn";

    @Metadata(enums = "allow,warn,fail",
              description = "Security policy for plain-text secrets."
                            + " When set, overrides the global policy for properties that contain sensitive values"
                            + " (passwords, tokens, etc.) configured as plain text instead of using vault references"
                            + " or environment variable placeholders.")
    private String secretPolicy;

    @Metadata(enums = "allow,warn,fail",
              description = "Security policy for insecure SSL/TLS configuration."
                            + " When set, overrides the global policy for options that disable certificate validation"
                            + " or hostname verification (e.g., trustAllCertificates=true).")
    private String insecureSslPolicy;

    @Metadata(enums = "allow,warn,fail",
              description = "Security policy for insecure deserialization configuration."
                            + " When set, overrides the global policy for options that enable dangerous deserialization"
                            + " of untrusted data (e.g., allowJavaSerializedObject=true).")
    private String insecureSerializationPolicy;

    @Metadata(enums = "allow,warn,fail",
              description = "Security policy for development-only features."
                            + " When set, overrides the global policy for options intended only for development"
                            + " (e.g., devConsoleEnabled, uploadEnabled).")
    private String insecureDevPolicy;

    @Metadata(description = "Comma-separated list of property keys to exclude from security policy checks."
                            + " Use full property paths (e.g., camel.component.aws2-s3.trustAllCertificates)"
                            + " to allow specific properties regardless of the configured policy.")
    private String allowedProperties;

    public SecurityConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public String getPolicy() {
        return policy;
    }

    /**
     * Global security policy applied to all categories unless overridden. Controls how Camel reacts when insecure
     * configuration is detected at startup.
     */
    public void setPolicy(String policy) {
        this.policy = normalizePolicy(policy);
    }

    public String getSecretPolicy() {
        return secretPolicy;
    }

    /**
     * Security policy for plain-text secrets. When set, overrides the global policy for properties that contain
     * sensitive values configured as plain text.
     */
    public void setSecretPolicy(String secretPolicy) {
        this.secretPolicy = normalizePolicy(secretPolicy);
    }

    public String getInsecureSslPolicy() {
        return insecureSslPolicy;
    }

    /**
     * Security policy for insecure SSL/TLS configuration. When set, overrides the global policy for options that
     * disable certificate validation or hostname verification.
     */
    public void setInsecureSslPolicy(String insecureSslPolicy) {
        this.insecureSslPolicy = normalizePolicy(insecureSslPolicy);
    }

    public String getInsecureSerializationPolicy() {
        return insecureSerializationPolicy;
    }

    /**
     * Security policy for insecure deserialization configuration. When set, overrides the global policy for options
     * that enable dangerous deserialization of untrusted data.
     */
    public void setInsecureSerializationPolicy(String insecureSerializationPolicy) {
        this.insecureSerializationPolicy = normalizePolicy(insecureSerializationPolicy);
    }

    public String getInsecureDevPolicy() {
        return insecureDevPolicy;
    }

    /**
     * Security policy for development-only features. When set, overrides the global policy for options intended only
     * for development environments.
     */
    public void setInsecureDevPolicy(String insecureDevPolicy) {
        this.insecureDevPolicy = normalizePolicy(insecureDevPolicy);
    }

    public String getAllowedProperties() {
        return allowedProperties;
    }

    /**
     * Comma-separated list of property keys to exclude from security policy checks. Use full property paths to allow
     * specific properties regardless of the configured policy.
     */
    public void setAllowedProperties(String allowedProperties) {
        this.allowedProperties = allowedProperties;
    }

    /**
     * Resolve the effective policy for a given security category.
     *
     * @param  category the security category (e.g., "secret", "insecure:ssl")
     * @return          the effective policy ("allow", "warn", or "fail")
     */
    public String resolvePolicy(String category) {
        String categoryPolicy = switch (category) {
            case "secret" -> secretPolicy;
            case "insecure:ssl" -> insecureSslPolicy;
            case "insecure:serialization" -> insecureSerializationPolicy;
            case "insecure:dev" -> insecureDevPolicy;
            default -> null;
        };
        return categoryPolicy != null ? categoryPolicy : policy;
    }

    private static String normalizePolicy(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(java.util.Locale.ENGLISH);
        if (!VALID_POLICIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid security policy value: '" + value + "'. Must be one of: " + VALID_POLICIES);
        }
        return normalized;
    }

    /**
     * Whether the given property key is in the allowed list.
     */
    public boolean isAllowed(String propertyKey) {
        return getAllowedPropertySet().contains(propertyKey);
    }

    /**
     * Returns the allowed properties as a set.
     */
    public Set<String> getAllowedPropertySet() {
        if (allowedProperties == null || allowedProperties.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String allowed : allowedProperties.split(",")) {
            String trimmed = allowed.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
