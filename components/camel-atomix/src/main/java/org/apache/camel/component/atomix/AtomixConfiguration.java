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
package org.apache.camel.component.atomix;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.Transport;
import io.atomix.catalyst.transport.netty.NettyTransport;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

public class AtomixConfiguration {
    @UriParam(javaType = "java.lang.String")
    private List<Address> nodes = Collections.emptyList();

    @UriParam(defaultValue = "io.atomix.catalyst.transport.netty.NettyTransport")
    private Class<? extends Transport> transport = NettyTransport.class;

    @UriParam
    private String configurationUri;

    protected AtomixConfiguration() {
    }

    public List<Address> getNodes() {
        return nodes;
    }

    /**
     * The address of the nodes composing the cluster.
     */
    public void setNodes(List<Address> nodes) {
        this.nodes = ObjectHelper.notNull(nodes, "Atomix Nodes");
    }

    public void setNodes(String nodes) {
        if (ObjectHelper.isNotEmpty(nodes)) {
            setNodes(Stream.of(nodes.split(",")).map(Address::new).collect(Collectors.toList()));
        }
    }

    public Class<? extends Transport> getTransport() {
        return transport;
    }

    /**
     * Sets the Atomix transport.
     */
    public void setTransport(Class<? extends Transport> transport) {
        this.transport = transport;
    }

    public String getConfigurationUri() {
        return configurationUri;
    }

    /**
     * The Atomix configuration uri.
     */
    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }
}
