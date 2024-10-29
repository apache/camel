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
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "allowPrivilegeEscalation", "auto", "capabilitiesAdd", "capabilitiesDrop", "enabled", "expose",
        "image", "imagePullPolicy", "imagePullSecrets", "limitCPU", "limitMemory", "name", "port", "portName", "requestCPU",
        "requestMemory",
        "runAsNonRoot", "runAsUser", "seccompProfileType", "servicePort", "servicePortName" })
public class Container {
    @JsonProperty("allowPrivilegeEscalation")
    @JsonPropertyDescription("Security Context AllowPrivilegeEscalation configuration (default false).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean allowPrivilegeEscalation;
    @JsonProperty("auto")
    @JsonPropertyDescription("To automatically enable the trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean auto;
    @JsonProperty("capabilitiesAdd")
    @JsonPropertyDescription("Security Context Capabilities Add configuration (default none).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> capabilitiesAdd;
    @JsonProperty("capabilitiesDrop")
    @JsonPropertyDescription("Security Context Capabilities Drop configuration (default ALL).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> capabilitiesDrop;
    @JsonProperty("enabled")
    @JsonPropertyDescription("Can be used to enable or disable a trait.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean enabled;
    @JsonProperty("expose")
    @JsonPropertyDescription("Can be used to enable/disable exposure via kubernetes Service.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean expose;
    @JsonProperty("image")
    @JsonPropertyDescription("The main container image to use for the Integration. When using this parameter the operator will create a synthetic IntegrationKit which won't be able to execute traits requiring CamelCatalog. If the container image you're using is coming from an IntegrationKit, use instead Integration `.spec.integrationKit` parameter. If you're moving the Integration across environments, you will also need to create an \"external\" IntegrationKit.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String image;
    @JsonProperty("imagePullPolicy")
    @JsonPropertyDescription("The pull policy: Always|Never|IfNotPresent")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private ImagePullPolicy imagePullPolicy;
    @JsonProperty("imagePullSecrets")
    @JsonPropertyDescription("The pull secrets for private registries")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private List<String> imagePullSecrets;
    @JsonProperty("imagePush")
    @JsonPropertyDescription("Enable image push to the registry")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private boolean imagePush;
    @JsonProperty("limitCPU")
    @JsonPropertyDescription("The maximum amount of CPU to be provided (default 500 millicores).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String limitCPU;
    @JsonProperty("limitMemory")
    @JsonPropertyDescription("The maximum amount of memory to be provided (default 512 Mi).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String limitMemory;
    @JsonProperty("name")
    @JsonPropertyDescription("The main container name. It's named `integration` by default.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String name;
    @JsonProperty("port")
    @JsonPropertyDescription("To configure a different port exposed by the container (default `8080`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Long port;
    @JsonProperty("portName")
    @JsonPropertyDescription("To configure a different port name for the port exposed by the container. It defaults to `http` only when the `expose` parameter is true.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String portName;
    @JsonProperty("requestCPU")
    @JsonPropertyDescription("The minimum amount of CPU required (default 125 millicores).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String requestCPU;
    @JsonProperty("requestMemory")
    @JsonPropertyDescription("The minimum amount of memory required (default 128 Mi).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String requestMemory;
    @JsonProperty("runAsNonRoot")
    @JsonPropertyDescription("Security Context RunAsNonRoot configuration (default false).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Boolean runAsNonRoot;
    @JsonProperty("runAsUser")
    @JsonPropertyDescription("Security Context RunAsUser configuration (default none): this value is automatically retrieved in OpenShift clusters when not explicitly set.")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Long runAsUser;
    @JsonProperty("seccompProfileType")
    @JsonPropertyDescription("Security Context SeccompProfileType configuration (default RuntimeDefault).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private SeccompProfileType seccompProfileType;
    @JsonProperty("servicePort")
    @JsonPropertyDescription("To configure under which service port the container port is to be exposed (default `80`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Long servicePort;
    @JsonProperty("servicePortName")
    @JsonPropertyDescription("To configure under which service port name the container port is to be exposed (default `http`).")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private String servicePortName;

    public Container() {
    }

    public Boolean getAllowPrivilegeEscalation() {
        return this.allowPrivilegeEscalation;
    }

    public void setAllowPrivilegeEscalation(Boolean allowPrivilegeEscalation) {
        this.allowPrivilegeEscalation = allowPrivilegeEscalation;
    }

    public Boolean getAuto() {
        return this.auto;
    }

    public void setAuto(Boolean auto) {
        this.auto = auto;
    }

    public List<String> getCapabilitiesAdd() {
        return this.capabilitiesAdd;
    }

    public void setCapabilitiesAdd(List<String> capabilitiesAdd) {
        this.capabilitiesAdd = capabilitiesAdd;
    }

    public List<String> getCapabilitiesDrop() {
        return this.capabilitiesDrop;
    }

    public void setCapabilitiesDrop(List<String> capabilitiesDrop) {
        this.capabilitiesDrop = capabilitiesDrop;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getExpose() {
        return this.expose;
    }

    public void setExpose(Boolean expose) {
        this.expose = expose;
    }

    public String getImage() {
        return this.image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public ImagePullPolicy getImagePullPolicy() {
        return this.imagePullPolicy;
    }

    public void setImagePullPolicy(ImagePullPolicy imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public List<String> getImagePullSecrets() {
        return imagePullSecrets;
    }

    public void setImagePullSecrets(List<String> imagePullSecret) {
        this.imagePullSecrets = imagePullSecret;
    }

    public boolean getImagePush() {
        return imagePush;
    }

    public void setImagePush(boolean imagePush) {
        this.imagePush = imagePush;
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

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPort() {
        return this.port;
    }

    public void setPort(Long port) {
        this.port = port;
    }

    public String getPortName() {
        return this.portName;
    }

    public void setPortName(String portName) {
        this.portName = portName;
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

    public Boolean getRunAsNonRoot() {
        return this.runAsNonRoot;
    }

    public void setRunAsNonRoot(Boolean runAsNonRoot) {
        this.runAsNonRoot = runAsNonRoot;
    }

    public Long getRunAsUser() {
        return this.runAsUser;
    }

    public void setRunAsUser(Long runAsUser) {
        this.runAsUser = runAsUser;
    }

    public SeccompProfileType getSeccompProfileType() {
        return this.seccompProfileType;
    }

    public void setSeccompProfileType(SeccompProfileType seccompProfileType) {
        this.seccompProfileType = seccompProfileType;
    }

    public Long getServicePort() {
        return this.servicePort;
    }

    public void setServicePort(Long servicePort) {
        this.servicePort = servicePort;
    }

    public String getServicePortName() {
        return this.servicePortName;
    }

    public void setServicePortName(String servicePortName) {
        this.servicePortName = servicePortName;
    }

    public enum ImagePullPolicy {
        @JsonProperty("Always")
        ALWAYS("Always"),
        @JsonProperty("Never")
        NEVER("Never"),
        @JsonProperty("IfNotPresent")
        IFNOTPRESENT("IfNotPresent");

        private final String value;

        ImagePullPolicy(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }

    public enum SeccompProfileType {
        @JsonProperty("Unconfined")
        UNCONFINED("Unconfined"),
        @JsonProperty("RuntimeDefault")
        RUNTIMEDEFAULT("RuntimeDefault");

        private final String value;

        SeccompProfileType(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return this.value;
        }
    }
}
