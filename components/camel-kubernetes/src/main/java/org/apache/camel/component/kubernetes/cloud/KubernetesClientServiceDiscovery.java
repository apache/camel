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
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
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
    private KubernetesClient client;

    public KubernetesClientServiceDiscovery(KubernetesConfiguration configuration) {
        super(configuration);
        this.namespace
                = configuration.getNamespace() != null ? configuration.getNamespace() : System.getenv("KUBERNETES_NAMESPACE");
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
                LOG.debug("Found {} endpoints in namespace: {} for name: {} and portName: {}", endpoints.getSubsets().size(),
                        this.namespace, name, this.portName);
            }
            for (EndpointSubset subset : endpoints.getSubsets()) {
                if (subset.getPorts().size() == 1) {
                    addServers(name, result, subset.getPorts().get(0), subset);
                } else {
                    addPortSet(name, result, subset);
                }
            }
        }

        return result;
    }

    private void addPortSet(String name, List<ServiceDefinition> result, EndpointSubset subset) {
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

        if (hasUsernameAndPassword(configuration) && ObjectHelper.isEmpty(configuration.getOauthToken())) {
            builder.withUsername(configuration.getUsername());
            builder.withPassword(configuration.getPassword());
        } else {
            builder.withOauthToken(configuration.getOauthToken());
        }

        ObjectHelper.ifNotEmpty(configuration.getCaCertData(), builder::withCaCertData);
        ObjectHelper.ifNotEmpty(configuration.getCaCertFile(), builder::withCaCertFile);
        ObjectHelper.ifNotEmpty(configuration.getClientCertData(), builder::withClientCertData);
        ObjectHelper.ifNotEmpty(configuration.getClientCertFile(), builder::withClientCertFile);
        ObjectHelper.ifNotEmpty(configuration.getApiVersion(), builder::withApiVersion);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyAlgo(), builder::withClientKeyAlgo);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyData(), builder::withClientKeyData);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyFile(), builder::withClientKeyFile);
        ObjectHelper.ifNotEmpty(configuration.getClientKeyPassphrase(), builder::withClientKeyPassphrase);
        ObjectHelper.ifNotEmpty(configuration.getTrustCerts(), builder::withTrustCerts);

        client = new KubernetesClientBuilder().withConfig(builder.build()).build();
    }

    private boolean hasUsernameAndPassword(KubernetesConfiguration configuration) {
        return ObjectHelper.isNotEmpty(configuration.getUsername()) && ObjectHelper.isNotEmpty(configuration.getPassword());
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
