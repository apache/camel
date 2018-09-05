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
package org.apache.camel.component.atomix.cluster;

import java.util.List;

import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.Transport;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.cluster.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixClusterService extends AbstractCamelClusterService<AtomixClusterView> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixClusterService.class);

    private Address address;
    private AtomixClusterConfiguration configuration;
    private AtomixReplica atomix;

    public AtomixClusterService() {
        this.configuration = new AtomixClusterConfiguration();
    }

    public AtomixClusterService(CamelContext camelContext, Address address, AtomixClusterConfiguration configuration) {
        super(null, camelContext);

        this.address = address;
        this.configuration = configuration.copy();
    }

    // **********************************
    // Properties
    // **********************************

    public Address getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = new Address(address);
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public AtomixClusterConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AtomixClusterConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    public String getStoragePath() {
        return configuration.getStoragePath();
    }

    public void setStoragePath(String storagePath) {
        configuration.setStoragePath(storagePath);
    }

    public StorageLevel getStorageLevel() {
        return configuration.getStorageLevel();
    }

    public List<Address> getNodes() {
        return configuration.getNodes();
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

    public AtomixReplica getAtomix() {
        return configuration.getAtomix();
    }

    public void setAtomix(AtomixReplica atomix) {
        configuration.setAtomix(atomix);
    }

    public String getConfigurationUri() {
        return configuration.getConfigurationUri();
    }

    public void setConfigurationUri(String configurationUri) {
        configuration.setConfigurationUri(configurationUri);
    }

    public boolean isEphemeral() {
        return configuration.isEphemeral();
    }

    public void setEphemeral(boolean ephemeral) {
        configuration.setEphemeral(ephemeral);
    }

    // *********************************************
    // Lifecycle
    // *********************************************

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (atomix != null) {
            LOGGER.debug("Leaving atomix cluster replica {}", atomix);
            atomix.leave().join();
        }
    }

    @Override
    protected AtomixClusterView createView(String namespace) throws Exception {
        return new AtomixClusterView(this, namespace, getOrCreateReplica(), configuration);
    }

    private AtomixReplica getOrCreateReplica() throws Exception {
        if (atomix == null) {
            // Validate parameters
            ObjectHelper.notNull(getCamelContext(), "Camel Context");
            ObjectHelper.notNull(address, "Atomix Node Address");
            ObjectHelper.notNull(configuration, "Atomix Node Configuration");

            atomix = AtomixClusterHelper.createReplica(getCamelContext(), address, configuration);

            if (ObjectHelper.isNotEmpty(configuration.getNodes())) {
                LOGGER.debug("Bootstrap cluster on address {} for nodes: {}", address, configuration.getNodes());
                this.atomix.bootstrap(configuration.getNodes()).join();
                LOGGER.debug("Bootstrap cluster done");
            } else {
                LOGGER.debug("Bootstrap cluster on address {}", address, configuration.getNodes());
                this.atomix.bootstrap().join();
                LOGGER.debug("Bootstrap cluster done");
            }
        }

        return this.atomix;
    }
}
