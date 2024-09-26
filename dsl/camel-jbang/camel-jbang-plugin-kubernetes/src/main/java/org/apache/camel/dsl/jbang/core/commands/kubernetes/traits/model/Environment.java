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
@JsonPropertyOrder({ "containerMeta", "enabled", "httpProxy", "vars" })
public class Environment {
    @JsonProperty("containerMeta")
    @JsonPropertyDescription("Enables injection of `NAMESPACE` and `POD_NAME` environment variables (default `true`)")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean containerMeta;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("httpProxy")
    @JsonPropertyDescription("Propagates the `HTTP_PROXY`, `HTTPS_PROXY` and `NO_PROXY` environment variables (default `true`)")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean httpProxy;
    @JsonProperty("vars")
    @JsonPropertyDescription("A list of environment variables to be added to the integration container. The syntax is KEY=VALUE, e.g., `MY_VAR=\"my value\"`. These take precedence over the previously defined environment variables.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> vars;

    public Environment() {
    }

    public Boolean getContainerMeta() {
        return this.containerMeta;
    }

    public void setContainerMeta(Boolean containerMeta) {
        this.containerMeta = containerMeta;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getHttpProxy() {
        return this.httpProxy;
    }

    public void setHttpProxy(Boolean httpProxy) {
        this.httpProxy = httpProxy;
    }

    public List<String> getVars() {
        return this.vars;
    }

    public void setVars(List<String> vars) {
        this.vars = vars;
    }
}
