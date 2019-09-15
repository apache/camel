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
package org.apache.camel.component.kubernetes.cloud;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesClientServiceDiscovery extends KubernetesServiceDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesClientServiceDiscovery.class);

    private final String namespace;
    private final String portName;
    private AutoAdaptableKubernetesClient client;

    public KubernetesClientServiceDiscovery(KubernetesConfiguration configuration) {
        super(configuration);
        this.namespace = configuration.getNamespace() != null ? configuration.getNamespace() : System.getenv("KUBERNETES_NAMESPACE");
        this.portName = configuration.getPortName();
        this.client = null;
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        LOG.debug("Discovering endpoints from namespace: {} with name: {}", this.namespace, name);
        Endpoints endpoints = client.endpoints().inNamespace(this.namespace).withName(name).get();
        List<ServiceDefinition> result = new ArrayList<>();
        if (endpoints != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found {} endpoints in namespace: {} for name: {} and portName: {}", endpoints.getSubsets().size(), this.namespace, name, this.portName);
            }
            for (EndpointSubset subset : endpoints.getSubsets()) {
                if (subset.getPorts().size() == 1) {
                    addServers(name, result, subset.getPorts().get(0), subset);
                } else {
                    final List<EndpointPort> ports = subset.getPorts();
                    final int portSize = ports.size();

                    EndpointPort port;
                    for (int p = 0; p < portSize; p++) {
                        port = ports.get(p);
                        if (ObjectHelper.isEmpty(this.portName) || this.portName.endsWith(port.getName())) {
                            addServers(name, result, port, subset);
                        }
                    }
                }
            }
        }

        return result;
    }

    protected void addServers(String name, List<ServiceDefinition> servers, EndpointPort port, EndpointSubset subset) {
        final List<EndpointAddress> addresses = subset.getAddresses();
        final int size = addresses.size();

        for (int i = 0; i < size; i++) {
            servers.add(new DefaultServiceDefinition(name, addresses.get(i).getIp(), port.getPort()));
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (client != null) {
            return;
        }

        final KubernetesConfiguration configuration = getConfiguration();

        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(configuration.getMasterUrl());

        if ((ObjectHelper.isNotEmpty(configuration.getUsername()) && ObjectHelper.isNotEmpty(configuration.getPassword())) && ObjectHelper.isEmpty(configuration.getOauthToken())) {
            builder.withUsername(configuration.getUsername());
            builder.withPassword(configuration.getPassword());
        } else {
            builder.withOauthToken(configuration.getOauthToken());
        }
        if (ObjectHelper.isNotEmpty(configuration.getCaCertData())) {
            builder.withCaCertData(configuration.getCaCertData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getCaCertFile())) {
            builder.withCaCertFile(configuration.getCaCertFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientCertData())) {
            builder.withClientCertData(configuration.getClientCertData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientCertFile())) {
            builder.withClientCertFile(configuration.getClientCertFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getApiVersion())) {
            builder.withApiVersion(configuration.getApiVersion());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyAlgo())) {
            builder.withClientKeyAlgo(configuration.getClientKeyAlgo());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyData())) {
            builder.withClientKeyData(configuration.getClientKeyData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyFile())) {
            builder.withClientKeyFile(configuration.getClientKeyFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyPassphrase())) {
            builder.withClientKeyPassphrase(configuration.getClientKeyPassphrase());
        }
        if (ObjectHelper.isNotEmpty(configuration.getTrustCerts())) {
            builder.withTrustCerts(configuration.getTrustCerts());
        }

        client = new AutoAdaptableKubernetesClient(builder.build());
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            IOHelper.close(client);
            client = null;
        }
    }

    @Override
    public String toString() {
        return "KubernetesClientServiceDiscovery{" + "namespace='" + namespace + '\'' + ", portName='" + portName + '\'' + '}';
    }
}
