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

public final class ContainerBuilder {
    private Boolean allowPrivilegeEscalation;
    private Boolean auto;
    private List<String> capabilitiesAdd;
    private List<String> capabilitiesDrop;
    private Boolean enabled;
    private Boolean expose;
    private String image;
    private Container.ImagePullPolicy imagePullPolicy;
    private List<String> imagePullSecrets;
    private boolean imagePush;
    private String limitCPU;
    private String limitMemory;
    private String name;
    private Long port;
    private String portName;
    private String requestCPU;
    private String requestMemory;
    private Boolean runAsNonRoot;
    private Long runAsUser;
    private Container.SeccompProfileType seccompProfileType;
    private Long servicePort;
    private String servicePortName;

    private ContainerBuilder() {
    }

    public static ContainerBuilder container() {
        return new ContainerBuilder();
    }

    public ContainerBuilder withAllowPrivilegeEscalation(Boolean allowPrivilegeEscalation) {
        this.allowPrivilegeEscalation = allowPrivilegeEscalation;
        return this;
    }

    public ContainerBuilder withAuto(Boolean auto) {
        this.auto = auto;
        return this;
    }

    public ContainerBuilder withCapabilitiesAdd(List<String> capabilitiesAdd) {
        this.capabilitiesAdd = capabilitiesAdd;
        return this;
    }

    public ContainerBuilder withCapabilitiesDrop(List<String> capabilitiesDrop) {
        this.capabilitiesDrop = capabilitiesDrop;
        return this;
    }

    public ContainerBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ContainerBuilder withExpose(Boolean expose) {
        this.expose = expose;
        return this;
    }

    public ContainerBuilder withImage(String image) {
        this.image = image;
        return this;
    }

    public ContainerBuilder withImagePullPolicy(Container.ImagePullPolicy imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
        return this;
    }

    public ContainerBuilder withImagePullSecrets(List<String> imagePullSecrets) {
        this.imagePullSecrets = imagePullSecrets;
        return this;
    }

    public ContainerBuilder withImagePush(boolean imagePush) {
        this.imagePush = imagePush;
        return this;
    }

    public ContainerBuilder withLimitCPU(String limitCPU) {
        this.limitCPU = limitCPU;
        return this;
    }

    public ContainerBuilder withLimitMemory(String limitMemory) {
        this.limitMemory = limitMemory;
        return this;
    }

    public ContainerBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public ContainerBuilder withPort(Long port) {
        this.port = port;
        return this;
    }

    public ContainerBuilder withPortName(String portName) {
        this.portName = portName;
        return this;
    }

    public ContainerBuilder withRequestCPU(String requestCPU) {
        this.requestCPU = requestCPU;
        return this;
    }

    public ContainerBuilder withRequestMemory(String requestMemory) {
        this.requestMemory = requestMemory;
        return this;
    }

    public ContainerBuilder withRunAsNonRoot(Boolean runAsNonRoot) {
        this.runAsNonRoot = runAsNonRoot;
        return this;
    }

    public ContainerBuilder withRunAsUser(Long runAsUser) {
        this.runAsUser = runAsUser;
        return this;
    }

    public ContainerBuilder withSeccompProfileType(Container.SeccompProfileType seccompProfileType) {
        this.seccompProfileType = seccompProfileType;
        return this;
    }

    public ContainerBuilder withServicePort(Long servicePort) {
        this.servicePort = servicePort;
        return this;
    }

    public ContainerBuilder withServicePortName(String servicePortName) {
        this.servicePortName = servicePortName;
        return this;
    }

    public Container build() {
        Container container = new Container();
        container.setAllowPrivilegeEscalation(allowPrivilegeEscalation);
        container.setAuto(auto);
        container.setCapabilitiesAdd(capabilitiesAdd);
        container.setCapabilitiesDrop(capabilitiesDrop);
        container.setEnabled(enabled);
        container.setExpose(expose);
        container.setImage(image);
        container.setImagePullPolicy(imagePullPolicy);
        container.setImagePullSecrets(imagePullSecrets);
        container.setImagePush(imagePush);
        container.setLimitCPU(limitCPU);
        container.setLimitMemory(limitMemory);
        container.setName(name);
        container.setPort(port);
        container.setPortName(portName);
        container.setRequestCPU(requestCPU);
        container.setRequestMemory(requestMemory);
        container.setRunAsNonRoot(runAsNonRoot);
        container.setRunAsUser(runAsUser);
        container.setSeccompProfileType(seccompProfileType);
        container.setServicePort(servicePort);
        container.setServicePortName(servicePortName);
        return container;
    }
}
