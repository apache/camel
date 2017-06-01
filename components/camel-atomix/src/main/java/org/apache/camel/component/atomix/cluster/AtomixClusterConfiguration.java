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
 */
package org.apache.camel.component.atomix.cluster;

import io.atomix.AtomixReplica;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.component.atomix.AtomixConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AtomixClusterConfiguration extends AtomixConfiguration {

    @UriParam
    private String storagePath;

    @UriParam(defaultValue = "MEMORY")
    private StorageLevel storageLevel = StorageLevel.MEMORY;

    @UriParam
    private String replicaRef;

    @UriParam
    private AtomixReplica replica;

    public AtomixClusterConfiguration() {
    }

    public String getStoragePath() {
        return storagePath;
    }

    /**
     * Sets the log directory.
     */
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public StorageLevel getStorageLevel() {
        return storageLevel;
    }

    /**
     * Sets the log storage level.
     */
    public void setStorageLevel(StorageLevel storageLevel) {
        this.storageLevel = storageLevel;
    }

    public String getReplicaRef() {
        return replicaRef;
    }

    /**
     * Set the reference of an instance of {@link AtomixReplica}.
     */
    public void setReplicaRef(String clusterref) {
        this.replicaRef = clusterref;
    }

    public AtomixReplica getReplica() {
        return replica;
    }

    /**
     * Set an instance of {@link AtomixReplica}.
     */
    public void setReplica(AtomixReplica replica) {
        this.replica = replica;
    }
}