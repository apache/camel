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
package org.apache.camel.component.atomix.ha;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import io.atomix.Atomix;
import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.Transport;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.atomix.cluster.AtomixClusterConfiguration;
import org.apache.camel.ha.CamelCluster;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomixClusterService extends ServiceSupport implements CamelContextAware, CamelClusterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixClusterService.class);

    private final AtomixClusterConfiguration configuration;

    private CamelContext camelContext;
    private Address address;
    private AtomixCluster cluster;

    public AtomixClusterService() {
        this.configuration = new AtomixClusterConfiguration();
    }

    // **********************************
    // Properties
    // **********************************

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = new Address(address);
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getStoragePath() {
        return configuration.getStoragePath();
    }

    public void setStoragePath(String storagePath) {
        configuration.setStoragePath(storagePath);
    }

    public List<Address> getNodes() {
        return configuration.getNodes();
    }

    public StorageLevel getStorageLevel() {
        return configuration.getStorageLevel();
    }

    public void setNodes(List<Address> nodes) {
        configuration.setNodes(nodes);
    }

    public void setStorageLevel(StorageLevel storageLevel) {
        configuration.setStorageLevel(storageLevel);
    }

    public void setNodes(String nodes) {
        configuration.setNodes(nodes);
    }

    public Class<? extends Transport> getTransport() {
        return configuration.getTransport();
    }

    public void setTransport(Class<? extends Transport> transport) {
        configuration.setTransport(transport);
    }

    public String getReplicaRef() {
        return configuration.getReplicaRef();
    }

    /**
     * Set the reference of an instance of {@link AtomixReplica}.
     * @param clusterref
     */
    public void setReplicaRef(String clusterref) {
        configuration.setReplicaRef(clusterref);
    }

    public Atomix getReplica() {
        return configuration.getReplica();
    }

    /**
     * Set an instance of {@link AtomixReplica}.
     * @param replica
     */
    public void setReplica(AtomixReplica replica) {
        configuration.setReplica(replica);
    }

    public String getConfigurationUri() {
        return configuration.getConfigurationUri();
    }

    public void setConfigurationUri(String configurationUri) {
        configuration.setConfigurationUri(configurationUri);
    }

    // **********************************
    // Cluster
    // **********************************

    @Override
    public synchronized CamelCluster getCluster() throws Exception  {
        if (this.cluster == null) {
            AtomixReplica atomix = configuration.getReplica();

            if (atomix == null) {
                if (configuration.getReplicaRef() != null) {
                    atomix = CamelContextHelper.mandatoryLookup(camelContext, configuration.getReplicaRef(), AtomixReplica.class);
                } else {
                    ObjectHelper.notNull(this.address, "Atomix Address");

                    final AtomixReplica.Builder atomixBuilder;

                    String uri = configuration.getConfigurationUri();
                    if (ObjectHelper.isNotEmpty(uri)) {
                        uri = camelContext.resolvePropertyPlaceholders(uri);
                        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri)) {
                            Properties properties = new Properties();
                            properties.load(is);

                            atomixBuilder = AtomixReplica.builder(this.address, properties);
                        }
                    } else {
                        atomixBuilder = AtomixReplica.builder(this.address);
                    }

                    Storage.Builder storageBuilder = Storage.builder();
                    ObjectHelper.ifNotEmpty(configuration.getStorageLevel(), storageBuilder::withStorageLevel);
                    ObjectHelper.ifNotEmpty(configuration.getStoragePath(), storageBuilder::withDirectory);

                    atomixBuilder.withStorage(storageBuilder.build());

                    if (configuration.getTransport() != null) {
                        atomixBuilder.withTransport(
                            camelContext.getInjector().newInstance(configuration.getTransport())
                        );
                    }

                    atomix = atomixBuilder.build();
                }
            }

            this.cluster = new AtomixCluster(atomix, configuration.getNodes());
            this.cluster.setCamelContext(camelContext);
        }

        return this.cluster;
    }

    @Override
    public CamelClusterView createView(String namespace) throws Exception {
        return getCluster().createView(namespace);
    }

    // **********************************
    // Service
    // **********************************

    @Override
    protected void doStart() throws Exception {
        LOGGER.debug("Starting cluster on address {}", address);
        getCluster().start();
    }

    @Override
    protected void doStop() throws Exception {
        if (this.cluster != null) {
            this.cluster.stop();
            this.cluster = null;
        }
    }
}
