/**
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

package org.apache.camel.component.kubernetes.cloud;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers where services are running on which servers in Kubernetes.
 */
abstract class KubernetesServiceDiscovery extends DefaultServiceDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesServiceDiscovery.class);
    private static final int FIRST = 0;

    private final KubernetesConfiguration configuration;
    private final String namespace;
    private final String portName;
    private AutoAdaptableKubernetesClient client;

    KubernetesServiceDiscovery(KubernetesConfiguration configuration) {
        this.configuration = configuration;
        this.namespace = configuration.getNamespace() != null ? configuration.getNamespace() : System.getenv("KUBERNETES_NAMESPACE");
        this.portName = configuration.getPortName();
        this.client = null;
    }

    @Override
    public String toString() {
        return "KubernetesServiceDiscovery";
    }

    protected ServiceDefinition newServer(String serviceName, EndpointAddress address, EndpointPort port) {
        return new DefaultServiceDefinition(serviceName, address.getIp(), port.getPort());
    }

    protected KubernetesConfiguration getConfiguration() {
        return this.configuration;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPortName() {
        return portName;
    }
}
