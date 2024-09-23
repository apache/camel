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
        "annotations", "baseImage", "configuration", "enabled", "incrementalImageBuild", "limitCPU", "limitMemory",
        "mavenProfiles", "nodeSelector", "orderStrategy", "platforms", "properties", "requestCPU", "requestMemory", "strategy",
        "tasks", "tasksFilter", "tasksLimitCPU", "tasksLimitMemory", "tasksRequestCPU", "tasksRequestMemory", "verbose" })
public class Builder {
    @JsonProperty("annotations")
    @JsonPropertyDescription("When using `pod` strategy, annotation to use for the builder pod.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Map<String, String> annotations;
    @JsonProperty("baseImage")
    @JsonPropertyDescription("Specify a base image. In order to have the application working properly it must be a container image which has a Java JDK installed and ready to use on path (ie `/usr/bin/java`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String baseImage;
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
    @JsonProperty("incrementalImageBuild")
    @JsonPropertyDescription("Use the incremental image build option, to reuse existing containers (default `true`)")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean incrementalImageBuild;
    @JsonProperty("limitCPU")
    @JsonPropertyDescription("When using `pod` strategy, the maximum amount of CPU required by the pod builder. Deprecated: use TasksRequestCPU instead with task name `builder`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String limitCPU;
    @JsonProperty("limitMemory")
    @JsonPropertyDescription("When using `pod` strategy, the maximum amount of memory required by the pod builder. Deprecated: use TasksRequestCPU instead with task name `builder`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String limitMemory;
    @JsonProperty("mavenProfiles")
    @JsonPropertyDescription("A list of references pointing to configmaps/secrets that contains a maven profile. This configmap/secret is a resource of the IntegrationKit created, therefore it needs to be present in the namespace where the operator is going to create the IntegrationKit. The content of the maven profile is expected to be a text containing a valid maven profile starting with `<profile>` and ending with `</profile>` that will be integrated as an inline profile in the POM. Syntax: [configmap|secret]:name[/key], where name represents the resource name, key optionally represents the resource key to be filtered (default key value = profile.xml).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> mavenProfiles;
    @JsonProperty("nodeSelector")
    @JsonPropertyDescription("Defines a set of nodes the builder pod is eligible to be scheduled on, based on labels on the node.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Map<String, String> nodeSelector;
    @JsonProperty("orderStrategy")
    @JsonPropertyDescription("The build order strategy to use, either `dependencies`, `fifo` or `sequential` (default is the platform default)")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private OrderStrategy orderStrategy;
    @JsonProperty("platforms")
    @JsonPropertyDescription("The list of manifest platforms to use to build a container image (default `linux/amd64`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> platforms;
    @JsonProperty("properties")
    @JsonPropertyDescription("A list of properties to be provided to the build task")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> properties;
    @JsonProperty("requestCPU")
    @JsonPropertyDescription("When using `pod` strategy, the minimum amount of CPU required by the pod builder. Deprecated: use TasksRequestCPU instead with task name `builder`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String requestCPU;
    @JsonProperty("requestMemory")
    @JsonPropertyDescription("When using `pod` strategy, the minimum amount of memory required by the pod builder. Deprecated: use TasksRequestCPU instead with task name `builder`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String requestMemory;
    @JsonProperty("strategy")
    @JsonPropertyDescription("The strategy to use, either `pod` or `routine` (default `routine`)")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Strategy strategy;
    @JsonProperty("tasks")
    @JsonPropertyDescription("A list of tasks to be executed (available only when using `pod` strategy) with format `<name>;<container-image>;<container-command>`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> tasks;
    @JsonProperty("tasksFilter")
    @JsonPropertyDescription("A list of tasks sorted by the order of execution in a csv format, ie, `<taskName1>,<taskName2>,...`. Mind that you must include also the operator tasks (`builder`, `quarkus-native`, `package`, `jib`, `s2i`) if you need to execute them. Useful only with `pod` strategy.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String tasksFilter;
    @JsonProperty("tasksLimitCPU")
    @JsonPropertyDescription("A list of limit cpu configuration for the specific task with format `<task-name>:<limit-cpu-conf>`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> tasksLimitCPU;
    @JsonProperty("tasksLimitMemory")
    @JsonPropertyDescription("A list of limit memory configuration for the specific task with format `<task-name>:<limit-memory-conf>`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> tasksLimitMemory;
    @JsonProperty("tasksRequestCPU")
    @JsonPropertyDescription("A list of request cpu configuration for the specific task with format `<task-name>:<request-cpu-conf>`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> tasksRequestCPU;
    @JsonProperty("tasksRequestMemory")
    @JsonPropertyDescription("A list of request memory configuration for the specific task with format `<task-name>:<request-memory-conf>`.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> tasksRequestMemory;
    @JsonProperty("verbose")
    @JsonPropertyDescription("Enable verbose logging on build components that support it (e.g. Kaniko build pod). Deprecated no longer in use")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean verbose;

    public Builder() {
    }

    public Map<String, String> getAnnotations() {
        return this.annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public String getBaseImage() {
        return this.baseImage;
    }

    public void setBaseImage(String baseImage) {
        this.baseImage = baseImage;
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

    public Boolean getIncrementalImageBuild() {
        return this.incrementalImageBuild;
    }

    public void setIncrementalImageBuild(Boolean incrementalImageBuild) {
        this.incrementalImageBuild = incrementalImageBuild;
    }

    public String getLimitCPU() {
        return this.limitCPU;
    }

    public void setLimitCPU(String limitCPU) {
        this.limitCPU = limitCPU;
    }

    public String getLimitMemory() {
        return this.limitMemory;
    }

    public void setLimitMemory(String limitMemory) {
        this.limitMemory = limitMemory;
    }

    public List<String> getMavenProfiles() {
        return this.mavenProfiles;
    }

    public void setMavenProfiles(List<String> mavenProfiles) {
        this.mavenProfiles = mavenProfiles;
    }

    public Map<String, String> getNodeSelector() {
        return this.nodeSelector;
    }

    public void setNodeSelector(Map<String, String> nodeSelector) {
        this.nodeSelector = nodeSelector;
    }

    public OrderStrategy getOrderStrategy() {
        return this.orderStrategy;
    }

    public void setOrderStrategy(OrderStrategy orderStrategy) {
        this.orderStrategy = orderStrategy;
    }

    public List<String> getPlatforms() {
        return this.platforms;
    }

    public void setPlatforms(List<String> platforms) {
        this.platforms = platforms;
    }

    public List<String> getProperties() {
        return this.properties;
    }

    public void setProperties(List<String> properties) {
        this.properties = properties;
    }

    public String getRequestCPU() {
        return this.requestCPU;
    }

    public void setRequestCPU(String requestCPU) {
        this.requestCPU = requestCPU;
    }

    public String getRequestMemory() {
        return this.requestMemory;
    }

    public void setRequestMemory(String requestMemory) {
        this.requestMemory = requestMemory;
    }

    public Strategy getStrategy() {
        return this.strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public List<String> getTasks() {
        return this.tasks;
    }

    public void setTasks(List<String> tasks) {
        this.tasks = tasks;
    }

    public String getTasksFilter() {
        return this.tasksFilter;
    }

    public void setTasksFilter(String tasksFilter) {
        this.tasksFilter = tasksFilter;
    }

    public List<String> getTasksLimitCPU() {
        return this.tasksLimitCPU;
    }

    public void setTasksLimitCPU(List<String> tasksLimitCPU) {
        this.tasksLimitCPU = tasksLimitCPU;
    }

    public List<String> getTasksLimitMemory() {
        return this.tasksLimitMemory;
    }

    public void setTasksLimitMemory(List<String> tasksLimitMemory) {
        this.tasksLimitMemory = tasksLimitMemory;
    }

    public List<String> getTasksRequestCPU() {
        return this.tasksRequestCPU;
    }

    public void setTasksRequestCPU(List<String> tasksRequestCPU) {
        this.tasksRequestCPU = tasksRequestCPU;
    }

    public List<String> getTasksRequestMemory() {
        return this.tasksRequestMemory;
    }

    public void setTasksRequestMemory(List<String> tasksRequestMemory) {
        this.tasksRequestMemory = tasksRequestMemory;
    }

    public Boolean getVerbose() {
        return this.verbose;
    }

    public void setVerbose(Boolean verbose) {
        this.verbose = verbose;
    }

    public enum OrderStrategy {
        @JsonProperty("dependencies")
        DEPENDENCIES("dependencies"),
        @JsonProperty("fifo")
        FIFO("fifo"),
        @JsonProperty("sequential")
        SEQUENTIAL("sequential");

        private final String value;

        OrderStrategy(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }

    public enum Strategy {
        @JsonProperty("pod")
        POD("pod"),
        @JsonProperty("routine")
        ROUTINE("routine");

        private final String value;

        Strategy(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }
}
