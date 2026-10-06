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
package org.apache.camel.component.openfeature;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class OpenFeatureConfiguration implements Cloneable {

    @UriParam(label = "common",
              description = "The feature flag key to evaluate. Can be overridden per message via the CamelOpenFeatureFlagKey header.")
    private String flagKey;

    @UriParam(label = "common", defaultValue = "false",
              description = "Default value when flag evaluation fails. When evaluationType is not set,"
                            + " also determines the evaluation type: \"true\" or \"false\" (case-insensitive)"
                            + " selects boolean evaluation, any other value selects string evaluation.")
    private String defaultValue = "false";

    @UriParam(label = "common", enums = "boolean,variant",
              description = "The evaluation type. When set to 'boolean', boolean evaluation is used (getBooleanValue)."
                            + " When set to 'variant', string evaluation is used (getStringValue)."
                            + " When not set, the type is inferred from defaultValue.")
    private String evaluationType;

    @UriParam(label = "producer",
              description = "Store the evaluation result in this exchange property, preserving the original message body.")
    private String resultProperty;

    @UriParam(label = "common", description = "Remote flagd service host. When set, the flagd RPC resolver is used.")
    private String host;

    @UriParam(label = "common", defaultValue = "8013", description = "Remote flagd service port.")
    private int port = 8013;

    @UriParam(label = "common", defaultValue = "false",
              description = "Whether to use TLS for the remote flagd connection.")
    private boolean tls;

    @UriParam(label = "common",
              description = "Path to the TLS certificate for the remote flagd connection.")
    private String certPath;

    @UriParam(label = "common", defaultValue = "500",
              description = "Deadline in milliseconds for the remote flagd connection.")
    private int deadline = 500;

    @UriParam(label = "common",
              description = "A JSON object defining feature flags in flagd format (inline). Mutually exclusive with flagsResource.")
    private String flags;

    @UriParam(label = "common",
              description = "Camel resource URI pointing to a feature flag definition file in flagd format."
                            + " Mutually exclusive with flags and provider.")
    private String flagsResource;

    @UriParam(label = "common",
              description = "Bean reference to a custom FeatureProvider (e.g. #myProvider)."
                            + " When set, takes precedence over flags, flagsResource, and host.")
    private String provider;

    @UriParam(label = "common", defaultValue = "false",
              description = "When true, a Map message body is used as the evaluation context."
                            + " When false (default), the body is not used as context."
                            + " The CamelOpenFeatureEvaluationContext header is always used regardless of this setting.")
    private boolean contextFromBody;

    public String getFlagKey() {
        return flagKey;
    }

    /** The feature flag key to evaluate. */
    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Default value when flag evaluation fails. When evaluationType is not set, also determines the evaluation type:
     * "true" or "false" (case-insensitive) selects boolean evaluation, any other value selects string evaluation.
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getEvaluationType() {
        return evaluationType;
    }

    /**
     * The evaluation type. When set to 'boolean', boolean evaluation is used. When set to 'variant', string evaluation
     * is used. When not set, the type is inferred from defaultValue.
     */
    public void setEvaluationType(String evaluationType) {
        this.evaluationType = evaluationType;
    }

    public String getResultProperty() {
        return resultProperty;
    }

    /** Store the evaluation result in this exchange property, preserving the original message body. */
    public void setResultProperty(String resultProperty) {
        this.resultProperty = resultProperty;
    }

    public String getHost() {
        return host;
    }

    /** Remote flagd service host. When set, the flagd RPC resolver is used. */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /** Remote flagd service port. */
    public void setPort(int port) {
        this.port = port;
    }

    public boolean isTls() {
        return tls;
    }

    /** Whether to use TLS for the remote flagd connection. */
    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public String getCertPath() {
        return certPath;
    }

    /** Path to the TLS certificate for the remote flagd connection. */
    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public int getDeadline() {
        return deadline;
    }

    /** Deadline in milliseconds for the remote flagd connection. */
    public void setDeadline(int deadline) {
        this.deadline = deadline;
    }

    public String getFlags() {
        return flags;
    }

    /** A JSON object defining feature flags in flagd format (inline). Mutually exclusive with flagsResource. */
    public void setFlags(String flags) {
        this.flags = flags;
    }

    public String getFlagsResource() {
        return flagsResource;
    }

    /**
     * Camel resource URI pointing to a feature flag definition file in flagd format. Mutually exclusive with flags and
     * provider.
     */
    public void setFlagsResource(String flagsResource) {
        this.flagsResource = flagsResource;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Bean reference to a custom FeatureProvider (e.g. #myProvider). When set, takes precedence over flags,
     * flagsResource, and host.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isContextFromBody() {
        return contextFromBody;
    }

    /** When true, a Map message body is used as the evaluation context. */
    public void setContextFromBody(boolean contextFromBody) {
        this.contextFromBody = contextFromBody;
    }

    public OpenFeatureConfiguration copy() {
        try {
            return (OpenFeatureConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    void validate() {
        if (flags != null && flagsResource != null) {
            throw new IllegalArgumentException("flags and flagsResource are mutually exclusive");
        }
        if (flagsResource != null && flagsResource.isBlank()) {
            throw new IllegalArgumentException("flagsResource must not be blank");
        }
        if (flags != null && flags.isBlank()) {
            throw new IllegalArgumentException("flags must not be blank");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port must be positive");
        }
    }
}
