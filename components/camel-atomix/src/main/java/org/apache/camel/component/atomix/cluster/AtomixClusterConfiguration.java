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

import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Transport;
import io.atomix.copycat.server.storage.StorageLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.atomix.AtomixConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AtomixClusterConfiguration extends AtomixConfiguration<AtomixReplica> implements Cloneable {

    @UriParam
    private Class<? extends Transport> clientTransport;

    @UriParam
    private Class<? extends Transport> serverTransport;

    @UriParam
    private String storagePath;

    @UriParam(defaultValue = "MEMORY")
    private StorageLevel storageLevel = StorageLevel.MEMORY;

    public AtomixClusterConfiguration() {
    }

    // ******************************************
    // Properties
    // ******************************************


    public Class<? extends Transport> getClientTransport() {
        return clientTransport;
    }

    /**
     * The client transport
     */
    public void setClientTransport(Class<? extends Transport> clientTransport) {
        this.clientTransport = clientTransport;
    }

    public Class<? extends Transport> getServerTransport() {
        return serverTransport;
    }

    /**
     * The server transport
     */
    public void setServerTransport(Class<? extends Transport> serverTransport) {
        this.serverTransport = serverTransport;
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

    // ****************************************
    // Copy
    // ****************************************

    public AtomixClusterConfiguration copy() {
        try {
            return (AtomixClusterConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}