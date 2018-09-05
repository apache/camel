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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.atomix.Atomix;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.Transport;
import io.atomix.catalyst.transport.netty.NettyTransport;
import io.atomix.resource.ReadConsistency;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

public class AtomixConfiguration<T extends Atomix> implements Cloneable {
    @UriParam(javaType = "io.atomix.Atomix")
    private T atomix;
    @UriParam(javaType = "java.lang.String")
    private List<Address> nodes = Collections.emptyList();
    @UriParam(javaType = "io.atomix.catalyst.transport.Transport", defaultValue = "io.atomix.catalyst.transport.netty.NettyTransport")
    private Class<? extends Transport> transport = NettyTransport.class;
    @UriParam
    private String configurationUri;
    @UriParam(label = "advanced")
    private ReadConsistency readConsistency;
    @UriParam(label = "advanced")
    private Properties defaultResourceConfig;
    @UriParam(label = "advanced")
    private Properties defaultResourceOptions;
    @UriParam(label = "advanced", prefix = "resource.config")
    private Map<String, Properties> resourceConfigs;
    @UriParam(label = "advanced", prefix = "resource.options")
    private Map<String, Properties> resourceOptions;
    @UriParam(label = "advanced", defaultValue = "false")
    private boolean ephemeral;


    protected AtomixConfiguration() {
    }

    // *****************************************
    // Properties
    // *****************************************

    public T getAtomix() {
        return atomix;
    }

    /**
     * The Atomix instance to use
     */
    public void setAtomix(T client) {
        this.atomix = client;
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

    public ReadConsistency getReadConsistency() {
        return readConsistency;
    }

    /**
     * The read consistency level.
     */
    public void setReadConsistency(ReadConsistency readConsistency) {
        this.readConsistency = readConsistency;
    }

    // ***********************************
    // Properties - Resource configuration
    // ***********************************

    public Properties getDefaultResourceConfig() {
        return defaultResourceConfig;
    }

    /**
     * The cluster wide default resource configuration.
     */
    public void setDefaultResourceConfig(Properties defaultResourceConfig) {
        this.defaultResourceConfig = defaultResourceConfig;
    }

    public Properties getDefaultResourceOptions() {
        return defaultResourceOptions;
    }

    /**
     * The local default resource options.
     */
    public void setDefaultResourceOptions(Properties defaultResourceOptions) {
        this.defaultResourceOptions = defaultResourceOptions;
    }

    public Map<String, Properties> getResourceConfigs() {
        return resourceConfigs;
    }

    /**
     * Cluster wide resources configuration.
     */
    public void setResourceConfigs(Map<String, Properties> resourceConfigs) {
        this.resourceConfigs = resourceConfigs;
    }

    public void addResourceConfig(String name, Properties config) {
        if (this.resourceConfigs == null) {
            this.resourceConfigs = new HashMap<>();
        }

        this.resourceConfigs.put(name, config);
    }

    public Properties getResourceConfig(String name) {
        Properties properties = null;

        if (this.resourceConfigs != null) {
            Properties props = this.resourceConfigs.getOrDefault(name, this.defaultResourceConfig);
            if (props != null) {
                properties = new Properties(props);
            }
        } else if (this.defaultResourceConfig != null) {
            properties = new Properties(this.defaultResourceConfig);
        }

        if (properties == null) {
            properties = new Properties();
        }

        return properties;
    }

    public Map<String, Properties> getResourceOptions() {
        return resourceOptions;
    }

    /**
     * Local resources configurations
     */
    public void setResourceOptions(Map<String, Properties> resourceOptions) {
        this.resourceOptions = resourceOptions;
    }

    public void addResourceOption(String name, Properties config) {
        if (this.resourceOptions == null) {
            this.resourceOptions = new HashMap<>();
        }

        this.resourceOptions.put(name, config);
    }

    public Properties getResourceOptions(String name) {
        Properties properties = null;

        if (this.resourceOptions != null) {
            Properties props = this.resourceOptions.getOrDefault(name, this.defaultResourceOptions);
            if (props != null) {
                properties = new Properties(props);
            }
        } else if (this.defaultResourceOptions != null) {
            properties = new Properties(this.defaultResourceOptions);
        }

        if (properties == null) {
            properties = new Properties();
        }

        return properties;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    /**
     * Sets if the local member should join groups as PersistentMember or not.
     *
     * If set to ephemeral the local member will receive an auto generated ID thus
     * the local one is ignored.
     */
    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
}
