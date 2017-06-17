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

public abstract class AbstractAtomixClientComponent<C extends AtomixClientConfiguration> extends DefaultComponent {
    protected AbstractAtomixClientComponent() {
    }

    protected AbstractAtomixClientComponent(CamelContext camelContext) {
        super(camelContext);
    }

    // *****************************************
    // Properties
    // *****************************************

    public AtomixClient getAtomix() {
        return getComponentConfiguration().getAtomix();
    }

    /**
     * The shared AtomixClient instance
     */
    public void setAtomix(AtomixClient client) {
        getComponentConfiguration().setAtomix(client);
    }

    public List<Address> getNodes() {
        return getComponentConfiguration().getNodes();
    }

    /**
     * The nodes the AtomixClient should connect to
     */
    public void setNodes(List<Address> nodes) {
        getComponentConfiguration().setNodes(nodes);
    }

    public void setNodes(String nodes) {
        getComponentConfiguration().setNodes(nodes);
    }

    public String getConfigurationUri() {
        return getComponentConfiguration().getConfigurationUri();
    }

    /**
     * The path to the AtomixClient configuration
     */
    public void setConfigurationUri(String configurationUri) {
        getComponentConfiguration().setConfigurationUri(configurationUri);
    }


    // *****************************************
    // Properties
    // *****************************************
    
    protected abstract C getComponentConfiguration();
}
