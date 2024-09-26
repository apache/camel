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

import java.util.List;
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
        "annotations", "auto", "enabled", "host", "path", "pathType", "tlsHosts", "tlsSecretName" })
public class Ingress {
    @JsonProperty("annotations")
    @JsonPropertyDescription("The annotations added to the ingress. This can be used to set controller specific annotations, e.g., when using the NGINX Ingress controller: See https://github.com/kubernetes/ingress-nginx/blob/main/docs/user-guide/nginx-configuration/annotations.md")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Map<String, String> annotations;
    @JsonProperty("auto")
    @JsonPropertyDescription("To automatically add an ingress whenever the camel route uses an HTTP endpoint consumer.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean auto;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("host")
    @JsonPropertyDescription("To configure the host exposed by the ingress.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String host;
    @JsonProperty("path")
    @JsonPropertyDescription("To configure the path exposed by the ingress (default `/`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String path;
    @JsonProperty("pathType")
    @JsonPropertyDescription("To configure the path type exposed by the ingress. One of `Exact`, `Prefix`, `ImplementationSpecific` (default to `Prefix`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private PathType pathType;
    @JsonProperty("tlsHosts")
    @JsonPropertyDescription("To configure tls hosts")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> tlsHosts;
    @JsonProperty("tlsSecretName")
    @JsonPropertyDescription("To configure tls secret name")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tlsSecretName;

    public Ingress() {
    }

    public Map<String, String> getAnnotations() {
        return this.annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public Boolean getAuto() {
        return this.auto;
    }

    public void setAuto(Boolean auto) {
        this.auto = auto;
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

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public PathType getPathType() {
        return this.pathType;
    }

    public void setPathType(PathType pathType) {
        this.pathType = pathType;
    }

    public List<String> getTlsHosts() {
        return this.tlsHosts;
    }

    public void setTlsHosts(List<String> tlsHosts) {
        this.tlsHosts = tlsHosts;
    }

    public String getTlsSecretName() {
        return this.tlsSecretName;
    }

    public void setTlsSecretName(String tlsSecretName) {
        this.tlsSecretName = tlsSecretName;
    }

    public enum PathType {
        @JsonProperty("Exact")
        EXACT("Exact"),
        @JsonProperty("Prefix")
        PREFIX("Prefix"),
        @JsonProperty("ImplementationSpecific")
        IMPLEMENTATIONSPECIFIC("ImplementationSpecific");

        private final String value;

        PathType(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }
}
