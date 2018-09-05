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
package org.apache.camel.component.atomix.cluster.springboot;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.atomix.copycat.server.storage.StorageLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.component.atomix.cluster.service")
public class AtomixClusterServiceConfiguration {
    enum Mode {
        node,
        client
    }

    /**
     * Sets if the atomix cluster service should be enabled or not, default is false.
     */
    private boolean enabled;

    /**
     * Sets the cluster mode.
     */
    private Mode mode;
    /**
     * The address of the nodes composing the cluster.
     */
    private Set<String> nodes = new HashSet<>();

    /**
     * The cluster id.
     */
    private String id;

    /**
     * The address of the node - node only.
     */
    private String address;

    /**
     * Sets if the local member should join groups as PersistentMember or not (node only).
     */
    private Boolean ephemeral;

    /**
     * The storage directory - node only.
     */
    private String storagePath;

    /**
     * The storage mode - node only.
     */
    private StorageLevel storageLevel = StorageLevel.MEMORY;

    /**
     * The Atomix configuration uri.
     */
    private String configurationUri;

    /**
     * Custom service attributes.
     */
    private Map<String, Object> attributes;
    
    /**
     * Service lookup order/priority.
     */
    private Integer order;

    // *********************************
    // Properties
    // *********************************

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setNodes(Set<String> nodes) {
        this.nodes = nodes;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Set<String> getNodes() {
        return nodes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean isEphemeral() {
        return ephemeral;
    }

    public void setEphemeral(Boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public StorageLevel getStorageLevel() {
        return storageLevel;
    }

    public void setStorageLevel(StorageLevel storageLevel) {
        this.storageLevel = storageLevel;
    }

    public String getConfigurationUri() {
        return configurationUri;
    }

    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }
}
