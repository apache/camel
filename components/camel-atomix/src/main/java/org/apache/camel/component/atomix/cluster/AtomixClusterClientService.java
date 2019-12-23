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
package org.apache.camel.component.atomix.cluster;

import java.util.List;

import io.atomix.AtomixClient;
import io.atomix.catalyst.transport.Address;
import org.apache.camel.CamelContext;
import org.apache.camel.component.atomix.client.AtomixClientConfiguration;
import org.apache.camel.component.atomix.client.AtomixClientHelper;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixClusterClientService extends AbstractCamelClusterService<AtomixClusterView> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixClusterClientService.class);
    private AtomixClientConfiguration configuration;
    private AtomixClient atomix;

    public AtomixClusterClientService() {
        this.configuration = new AtomixClientConfiguration();
    }

    public AtomixClusterClientService(CamelContext camelContext, AtomixClientConfiguration configuration) {
        super(null, camelContext);

        this.configuration = configuration.copy();
    }

    // **********************************
    // Properties
    // **********************************

    public AtomixClientConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AtomixClientConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    public List<Address> getNodes() {
        return configuration.getNodes();
    }

    public void setNodes(List<Address> nodes) {
        configuration.setNodes(nodes);
    }

    public void setNodes(String nodes) {
        configuration.setNodes(nodes);
    }

    public String getTransport() {
        return configuration.getTransportClassName();
    }

    public void setTransportClassName(String transport) {
        configuration.setTransportClassName(transport);
    }

    public AtomixClient getAtomix() {
        return (AtomixClient) configuration.getAtomix();
    }

    public void setAtomix(AtomixClient atomix) {
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
            LOGGER.debug("Shutdown atomix client {}", atomix);
            atomix.close().join();
        }
    }

    @Override
    protected AtomixClusterView createView(String namespace) throws Exception {
        return new AtomixClusterView(this, namespace, getOrCreateClient(), configuration);
    }

    private AtomixClient getOrCreateClient() throws Exception {
        if (atomix == null) {
            // Validate parameters
            ObjectHelper.notNull(getCamelContext(), "Camel Context");
            ObjectHelper.notNull(configuration, "Atomix Node Configuration");

            if (ObjectHelper.isEmpty(configuration.getNodes())) {
                throw new IllegalArgumentException("Atomix nodes should not be empty");
            }

            atomix = AtomixClientHelper.createClient(getCamelContext(), configuration);

            LOGGER.debug("Connect to cluster nodes: {}", configuration.getNodes());
            atomix.connect(configuration.getNodes()).join();
            LOGGER.debug("Connect to cluster done");
        }

        return this.atomix;
    }
}
