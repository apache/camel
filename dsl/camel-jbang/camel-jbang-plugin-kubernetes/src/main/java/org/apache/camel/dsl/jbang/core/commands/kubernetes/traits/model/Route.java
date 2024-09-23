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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "annotations", "configuration", "enabled", "host", "tlsCACertificate", "tlsCACertificateSecret", "tlsCertificate",
        "tlsCertificateSecret", "tlsDestinationCACertificate", "tlsDestinationCACertificateSecret",
        "tlsInsecureEdgeTerminationPolicy", "tlsKey", "tlsKeySecret", "tlsTermination" })
public class Route {
    @JsonProperty("annotations")
    @JsonPropertyDescription("The annotations added to route. This can be used to set route specific annotations For annotations options see https://docs.openshift.com/container-platform/3.11/architecture/networking/routes.html#route-specific-annotations CLI usage example: -t \"route.annotations.'haproxy.router.openshift.io/balance'=true\"")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Map<String, String> annotations;
    @JsonProperty("configuration")
    @JsonPropertyDescription("Legacy trait configuration parameters. Deprecated: for backward compatibility.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Configuration configuration;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait. All traits share this common property.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("host")
    @JsonPropertyDescription("To configure the host exposed by the route.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String host;
    @JsonProperty("tlsCACertificate")
    @JsonPropertyDescription("The TLS CA certificate contents. \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsCACertificate;
    @JsonProperty("tlsCACertificateSecret")
    @JsonPropertyDescription("The secret name and key reference to the TLS CA certificate. The format is \"secret-name[/key-name]\", the value represents the secret name, if there is only one key in the secret it will be read, otherwise you can set a key name separated with a \"/\". \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsCACertificateSecret;
    @JsonProperty("tlsCertificate")
    @JsonPropertyDescription("The TLS certificate contents. \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsCertificate;
    @JsonProperty("tlsCertificateSecret")
    @JsonPropertyDescription("The secret name and key reference to the TLS certificate. The format is \"secret-name[/key-name]\", the value represents the secret name, if there is only one key in the secret it will be read, otherwise you can set a key name separated with a \"/\". \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsCertificateSecret;
    @JsonProperty("tlsDestinationCACertificate")
    @JsonPropertyDescription("The destination CA certificate provides the contents of the ca certificate of the final destination.  When using reencrypt termination this file should be provided in order to have routers use it for health checks on the secure connection. If this field is not specified, the router may provide its own destination CA and perform hostname validation using the short service name (service.namespace.svc), which allows infrastructure generated certificates to automatically verify. \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsDestinationCACertificate;
    @JsonProperty("tlsDestinationCACertificateSecret")
    @JsonPropertyDescription("The secret name and key reference to the destination CA certificate. The format is \"secret-name[/key-name]\", the value represents the secret name, if there is only one key in the secret it will be read, otherwise you can set a key name separated with a \"/\". \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsDestinationCACertificateSecret;
    @JsonProperty("tlsInsecureEdgeTerminationPolicy")
    @JsonPropertyDescription("To configure how to deal with insecure traffic, e.g. `Allow`, `Disable` or `Redirect` traffic. \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private TlsInsecureEdgeTerminationPolicy tlsInsecureEdgeTerminationPolicy;
    @JsonProperty("tlsKey")
    @JsonPropertyDescription("The TLS certificate key contents. \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsKey;
    @JsonProperty("tlsKeySecret")
    @JsonPropertyDescription("The secret name and key reference to the TLS certificate key. The format is \"secret-name[/key-name]\", the value represents the secret name, if there is only one key in the secret it will be read, otherwise you can set a key name separated with a \"/\". \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsKeySecret;
    @JsonProperty("tlsTermination")
    @JsonPropertyDescription("The TLS termination type, like `edge`, `passthrough` or `reencrypt`. \n Refer to the OpenShift route documentation for additional information.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private TlsTermination tlsTermination;

    public Route() {
    }

    public Map<String, String> getAnnotations() {
        return this.annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public Configuration getConfiguration() {
        return this.configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getTlsCACertificate() {
        return this.tlsCACertificate;
    }

    public void setTlsCACertificate(String tlsCACertificate) {
        this.tlsCACertificate = tlsCACertificate;
    }

    public String getTlsCACertificateSecret() {
        return this.tlsCACertificateSecret;
    }

    public void setTlsCACertificateSecret(String tlsCACertificateSecret) {
        this.tlsCACertificateSecret = tlsCACertificateSecret;
    }

    public String getTlsCertificate() {
        return this.tlsCertificate;
    }

    public void setTlsCertificate(String tlsCertificate) {
        this.tlsCertificate = tlsCertificate;
    }

    public String getTlsCertificateSecret() {
        return this.tlsCertificateSecret;
    }

    public void setTlsCertificateSecret(String tlsCertificateSecret) {
        this.tlsCertificateSecret = tlsCertificateSecret;
    }

    public String getTlsDestinationCACertificate() {
        return this.tlsDestinationCACertificate;
    }

    public void setTlsDestinationCACertificate(String tlsDestinationCACertificate) {
        this.tlsDestinationCACertificate = tlsDestinationCACertificate;
    }

    public String getTlsDestinationCACertificateSecret() {
        return this.tlsDestinationCACertificateSecret;
    }

    public void setTlsDestinationCACertificateSecret(String tlsDestinationCACertificateSecret) {
        this.tlsDestinationCACertificateSecret = tlsDestinationCACertificateSecret;
    }

    public TlsInsecureEdgeTerminationPolicy getTlsInsecureEdgeTerminationPolicy() {
        return this.tlsInsecureEdgeTerminationPolicy;
    }

    public void setTlsInsecureEdgeTerminationPolicy(TlsInsecureEdgeTerminationPolicy tlsInsecureEdgeTerminationPolicy) {
        this.tlsInsecureEdgeTerminationPolicy = tlsInsecureEdgeTerminationPolicy;
    }

    public String getTlsKey() {
        return this.tlsKey;
    }

    public void setTlsKey(String tlsKey) {
        this.tlsKey = tlsKey;
    }

    public String getTlsKeySecret() {
        return this.tlsKeySecret;
    }

    public void setTlsKeySecret(String tlsKeySecret) {
        this.tlsKeySecret = tlsKeySecret;
    }

    public TlsTermination getTlsTermination() {
        return this.tlsTermination;
    }

    public void setTlsTermination(TlsTermination tlsTermination) {
        this.tlsTermination = tlsTermination;
    }

    public enum TlsInsecureEdgeTerminationPolicy {
        @JsonProperty("None")
        NONE("None"),
        @JsonProperty("Allow")
        ALLOW("Allow"),
        @JsonProperty("Redirect")
        REDIRECT("Redirect");

        private final String value;

        TlsInsecureEdgeTerminationPolicy(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }

    public enum TlsTermination {
        @JsonProperty("edge")
        EDGE("edge"),
        @JsonProperty("reencrypt")
        REENCRYPT("reencrypt"),
        @JsonProperty("passthrough")
        PASSTHROUGH("passthrough");

        private final String value;

        TlsTermination(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }
}
