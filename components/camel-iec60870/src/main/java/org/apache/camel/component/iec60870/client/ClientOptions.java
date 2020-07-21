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
package org.apache.camel.component.iec60870.client;

import java.util.Objects;

import org.apache.camel.component.iec60870.BaseOptions;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.eclipse.neoscada.protocol.iec60870.ProtocolOptions;
import org.eclipse.neoscada.protocol.iec60870.client.data.DataModuleOptions;

@UriParams
public class ClientOptions extends BaseOptions<ClientOptions> {

    /**
     * Data module options
     */
    @UriParam(javaType = "org.eclipse.neoscada.protocol.iec60870.client.data.DataModuleOptions")
    private DataModuleOptions.Builder dataModuleOptions;

    // dummy for doc generation
    /**
     * Whether background scan transmissions should be ignored.
     */
    @UriParam(label = "data", defaultValue = "true")
    private boolean ignoreBackgroundScan = true;

    // dummy for doc generation
    /**
     * Whether to include the source address
     */
    @UriParam(label = "data")
    private byte causeSourceAddress;

    /**
     * Timeout in millis to wait for client to establish a connected connection.
     */
    @UriParam(label = "data", defaultValue = "10000")
    private int connectionTimeout = 10000;

    public ClientOptions() {
        this.dataModuleOptions = new DataModuleOptions.Builder();
    }

    public ClientOptions(final ClientOptions other) {
        this(other.getProtocolOptions(), other.getDataModuleOptions());
    }

    public ClientOptions(final ProtocolOptions protocolOptions, final DataModuleOptions dataOptions) {
        super(protocolOptions);

        Objects.requireNonNull(dataOptions);

        this.dataModuleOptions = new DataModuleOptions.Builder(dataOptions);
    }

    public void setDataModuleOptions(final DataModuleOptions dataModuleOptions) {
        Objects.requireNonNull(dataModuleOptions);

        this.dataModuleOptions = new DataModuleOptions.Builder(dataModuleOptions);
    }

    public DataModuleOptions getDataModuleOptions() {
        return this.dataModuleOptions.build();
    }

    @Override
    public ClientOptions copy() {
        return new ClientOptions(this);
    }

    // wrapper methods - DataModuleOptions

    public byte getCauseSourceAddress() {
        return causeSourceAddress;
    }

    public void setCauseSourceAddress(final byte causeSourceAddress) {
        this.causeSourceAddress = causeSourceAddress;
        this.dataModuleOptions.setCauseSourceAddress(causeSourceAddress);
    }

    public void setIgnoreBackgroundScan(final boolean ignoreBackgroundScan) {
        this.dataModuleOptions.setIgnoreBackgroundScan(ignoreBackgroundScan);
    }

    public boolean isIgnoreBackgroundScan() {
        return this.dataModuleOptions.isIgnoreBackgroundScan();
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
}
