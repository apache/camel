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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "configuration", "enabled", "properties", "runtimeVersion" })
public class Camel {

    @JsonProperty("configuration")
    @JsonPropertyDescription("Legacy trait configuration parameters. Deprecated: for backward compatibility.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Configuration configuration;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Deprecated: no longer in use.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("properties")
    @JsonPropertyDescription("A list of properties to be provided to the Integration runtime")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> properties;
    @JsonProperty("runtimeVersion")
    @JsonPropertyDescription("The camel-k-runtime version to use for the integration. It overrides the default version set in the Integration Platform. You can use a fixed version (for example \"3.2.3\") or a semantic version (for example \"3.x\") which will try to resolve to the best matching Catalog existing on the cluster.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String runtimeVersion;

    public Camel() {
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

    public List<String> getProperties() {
        return this.properties;
    }

    public void setProperties(List<String> properties) {
        this.properties = properties;
    }

    public String getRuntimeVersion() {
        return this.runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }
}
