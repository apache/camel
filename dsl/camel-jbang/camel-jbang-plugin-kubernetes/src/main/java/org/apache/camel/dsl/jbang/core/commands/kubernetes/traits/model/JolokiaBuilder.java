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

public final class JolokiaBuilder {
    private Boolean enabled;
    private Long containerPort;
    private String containerPortName;
    private Boolean expose;
    private Long servicePort;
    private String servicePortName;

    private JolokiaBuilder() {}

    public static JolokiaBuilder jolokia() {
        return new JolokiaBuilder();
    }

    public JolokiaBuilder withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public JolokiaBuilder withContainerPort(Long port) {
        this.containerPort = port;
        return this;
    }

    public JolokiaBuilder withContainerPortName(String portName) {
        this.containerPortName = portName;
        return this;
    }

    public JolokiaBuilder withExpose(Boolean expose) {
        this.expose = expose;
        return this;
    }

    public JolokiaBuilder withServicePort(Long port) {
        this.servicePort = port;
        return this;
    }

    public JolokiaBuilder withServicePortName(String portName) {
        this.servicePortName = portName;
        return this;
    }

    public Jolokia build() {
        Jolokia jolokia = new Jolokia();
        jolokia.setEnabled(enabled);
        jolokia.setContainerPort(containerPort);
        jolokia.setContainerPortName(containerPortName);
        jolokia.setExpose(expose);
        jolokia.setServicePort(servicePort);
        jolokia.setServicePortName(servicePortName);
        return jolokia;
    }
}
