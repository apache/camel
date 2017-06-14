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
package org.apache.camel.component.atomix.client;

import java.util.List;

import io.atomix.AtomixClient;
import io.atomix.catalyst.transport.Address;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;

public abstract class AbstractAtomixClientComponent<C extends AtomixClientConfiguration> extends DefaultComponent {
    @Metadata(label = "advanced")
    private C configuration;

    protected AbstractAtomixClientComponent(C configuration) {
        this.configuration = configuration;
    }

    protected AbstractAtomixClientComponent(CamelContext camelContext, C configuration) {
        super(camelContext);

        this.configuration = configuration;
    }

    // *****************************************
    // Properties
    // *****************************************

    public C getConfiguration() {
        return configuration;
    }

    /**
     * The shared component configuration
     */
    public void setConfiguration(C configuration) {
        this.configuration = configuration;
    }

    public AtomixClient getAtomix() {
        return configuration.getAtomix();
    }

    /**
     * The shared AtomixClient instance
     */
    public void setAtomix(AtomixClient client) {
        configuration.setAtomix(client);
    }

    public List<Address> getNodes() {
        return configuration.getNodes();
    }

    /**
     * The nodes the AtomixClient should connect to
     */
    public void setNodes(List<Address> nodes) {
        configuration.setNodes(nodes);
    }

    public void setNodes(String nodes) {
        configuration.setNodes(nodes);
    }

    public String getConfigurationUri() {
        return configuration.getConfigurationUri();
    }


    /**
     * The path to the AtomixClient configuration
     */
    public void setConfigurationUri(String configurationUri) {
        configuration.setConfigurationUri(configurationUri);
    }
}
