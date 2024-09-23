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
@JsonPropertyOrder({
        "configs", "configuration", "emptyDirs", "enabled", "hotReload", "resources", "scanKameletsImplicitLabelSecrets",
        "volumes" })
public class Mount {
    @JsonProperty("configs")
    @JsonPropertyDescription("A list of configuration pointing to configmap/secret. The configuration are expected to be UTF-8 resources as they are processed by runtime Camel Context and tried to be parsed as property files. They are also made available on the classpath in order to ease their usage directly from the Route. Syntax: [configmap|secret]:name[/key], where name represents the resource name and key optionally represents the resource key to be filtered")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> configs;
    @JsonProperty("configuration")
    @JsonPropertyDescription("Legacy trait configuration parameters. Deprecated: for backward compatibility.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Configuration configuration;
    @JsonProperty("emptyDirs")
    @JsonPropertyDescription("A list of EmptyDir volumes to be mounted. Syntax: [name:/container/path]")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> emptyDirs;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Deprecated: no longer in use.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("hotReload")
    @JsonPropertyDescription("Enable \"hot reload\" when a secret/configmap mounted is edited (default `false`). The configmap/secret must be marked with `camel.apache.org/integration` label to be taken in account. The resource will be watched for any kind change, also for changes in metadata.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean hotReload;
    @JsonProperty("resources")
    @JsonPropertyDescription("A list of resources (text or binary content) pointing to configmap/secret. The resources are expected to be any resource type (text or binary content). The destination path can be either a default location or any path specified by the user. Syntax: [configmap|secret]:name[/key][@path], where name represents the resource name, key optionally represents the resource key to be filtered and path represents the destination path")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> resources;
    @JsonProperty("scanKameletsImplicitLabelSecrets")
    @JsonPropertyDescription("Deprecated: include your properties in an explicit property file backed by a secret. Let the operator to scan for secret labeled with `camel.apache.org/kamelet` and `camel.apache.org/kamelet.configuration`. These secrets are mounted to the application and treated as plain properties file with their key/value list (ie .spec.data[\"camel.my-property\"] = my-value) (default `true`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean scanKameletsImplicitLabelSecrets;
    @JsonProperty("volumes")
    @JsonPropertyDescription("A list of Persistent Volume Claims to be mounted. Syntax: [pvcname:/container/path]")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> volumes;

    public Mount() {
    }

    public List<String> getConfigs() {
        return this.configs;
    }

    public void setConfigs(List<String> configs) {
        this.configs = configs;
    }

    public Configuration getConfiguration() {
        return this.configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public List<String> getEmptyDirs() {
        return this.emptyDirs;
    }

    public void setEmptyDirs(List<String> emptyDirs) {
        this.emptyDirs = emptyDirs;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getHotReload() {
        return this.hotReload;
    }

    public void setHotReload(Boolean hotReload) {
        this.hotReload = hotReload;
    }

    public List<String> getResources() {
        return this.resources;
    }

    public void setResources(List<String> resources) {
        this.resources = resources;
    }

    public Boolean getScanKameletsImplicitLabelSecrets() {
        return this.scanKameletsImplicitLabelSecrets;
    }

    public void setScanKameletsImplicitLabelSecrets(Boolean scanKameletsImplicitLabelSecrets) {
        this.scanKameletsImplicitLabelSecrets = scanKameletsImplicitLabelSecrets;
    }

    public List<String> getVolumes() {
        return this.volumes;
    }

    public void setVolumes(List<String> volumes) {
        this.volumes = volumes;
    }
}
