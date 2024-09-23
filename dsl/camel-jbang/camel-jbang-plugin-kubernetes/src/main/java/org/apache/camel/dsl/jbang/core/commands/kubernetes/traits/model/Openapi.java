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
@JsonPropertyOrder({ "configmaps", "configuration", "enabled" })
public class Openapi {
    @JsonProperty("configmaps")
    @JsonPropertyDescription("The configmaps holding the spec of the OpenAPI")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> configmaps;
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

    public Openapi() {
    }

    public List<String> getConfigmaps() {
        return this.configmaps;
    }

    public void setConfigmaps(List<String> configmaps) {
        this.configmaps = configmaps;
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
}
