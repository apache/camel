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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "auto", "configuration", "enabled", "nodePort", "type" })
public class Service {
    @JsonProperty("auto")
    @JsonPropertyDescription("To automatically detect from the code if a Service needs to be created.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean auto;
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
    @JsonProperty("nodePort")
    @JsonPropertyDescription("Enable Service to be exposed as NodePort (default `false`). Deprecated: Use service type instead.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean nodePort;
    @JsonProperty("type")
    @JsonPropertyDescription("The type of service to be used, either 'ClusterIP', 'NodePort' or 'LoadBalancer'.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Type type;

    public Service() {
    }

    public Boolean getAuto() {
        return this.auto;
    }

    public void setAuto(Boolean auto) {
        this.auto = auto;
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

    public Boolean getNodePort() {
        return this.nodePort;
    }

    public void setNodePort(Boolean nodePort) {
        this.nodePort = nodePort;
    }

    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public enum Type {
        @JsonProperty("ClusterIP")
        CLUSTERIP("ClusterIP"),
        @JsonProperty("NodePort")
        NODEPORT("NodePort"),
        @JsonProperty("LoadBalancer")
        LOADBALANCER("LoadBalancer");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }
}
