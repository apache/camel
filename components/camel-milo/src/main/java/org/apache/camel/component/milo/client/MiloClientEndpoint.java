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
package org.apache.camel.component.milo.client;

import java.util.Objects;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.milo.MiloConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;

import static org.apache.camel.component.milo.MiloConstants.SCHEME_CLIENT;

/**
 * Connect to OPC UA servers using the binary protocol for acquiring telemetry data.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = SCHEME_CLIENT, syntax = "milo-client:endpointUri", title = "OPC UA Client",
             category = { Category.IOT }, headersClass = MiloConstants.class)
public class MiloClientEndpoint extends DefaultEndpoint {

    private final MiloClientConnectionManager connectionManager;

    /**
     * The OPC UA server endpoint
     */
    @UriPath
    @Metadata(required = true)
    private final String endpointUri;

    /**
     * The node definition (see Node ID)
     */
    @UriParam
    private String node;

    /**
     * Comma-separated node definitions to monitor with one consumer, instead of node (see Node ID)
     */
    @UriParam(label = "consumer")
    private String nodes;

    /**
     * The method definition (see Method ID)
     */
    @UriParam
    private String method;

    /**
     * The sampling interval in milliseconds
     */
    @UriParam(defaultValue = "1000.0")
    private Double samplingInterval = 1000.0;

    /**
     * The queue size used for OPC UA subscriptions. If not set, a queue size of 1 is requested.
     */
    @UriParam
    private Integer queueSize;

    /**
     * The client configuration
     */
    @UriParam
    private MiloClientConfiguration configuration;

    /**
     * Default "await" setting for writes
     */
    @UriParam
    private boolean defaultAwaitWrites;

    /**
     * Omit a data change whose value is null and whose status is Good, for example the initial value at startup. A data
     * change with a Bad or Uncertain status is always delivered.
     */
    @UriParam(defaultValue = "true")
    private boolean omitNullValues = true;

    @UriParam
    private MonitorFilterConfiguration monitorFilterConfiguration;

    @UriParam
    private MonitorFilterType monitorFilterType;

    public MiloClientEndpoint(final String uri, final MiloClientComponent component, final String endpointUri,
                              final MiloClientConnectionManager connectionManager) {
        super(uri, component);

        Objects.requireNonNull(component);
        Objects.requireNonNull(endpointUri);
        Objects.requireNonNull(connectionManager);

        this.endpointUri = endpointUri;
        this.setMonitorFilterConfiguration(new MonitorFilterConfiguration());
        this.connectionManager = connectionManager;
    }

    public void setConfiguration(MiloClientConfiguration configuration) {
        this.configuration = configuration;
    }

    public MiloClientConfiguration getConfiguration() {
        return configuration;
    }

    public MonitorFilterConfiguration getMonitorFilterConfiguration() {
        return monitorFilterConfiguration;
    }

    public void setMonitorFilterConfiguration(MonitorFilterConfiguration monitorFilterConfiguration) {
        this.monitorFilterConfiguration = monitorFilterConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MiloClientProducer(this, this.defaultAwaitWrites);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        MiloClientConsumer consumer = new MiloClientConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public MiloClientConnection createConnection() {
        return this.connectionManager.createConnection(configuration, monitorFilterConfiguration);
    }

    public void releaseConnection(MiloClientConnection connection) {
        this.connectionManager.releaseConnection(connection);
    }

    // item configuration
    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public void setNode(final String node) {
        this.node = node;
    }

    public String getNode() {
        return node;
    }

    ExpandedNodeId getNodeId() {
        if (this.node != null) {
            return ExpandedNodeId.parse(this.node);
        } else {
            return null;
        }
    }

    public void setNodes(final String nodes) {
        this.nodes = nodes;
    }

    public String getNodes() {
        return nodes;
    }

    ExpandedNodeId getMethodId() {
        if (this.method != null) {
            return ExpandedNodeId.parse(this.method);
        } else {
            return null;
        }
    }

    public Double getSamplingInterval() {
        return this.samplingInterval;
    }

    public void setSamplingInterval(final Double samplingInterval) {
        this.samplingInterval = samplingInterval;
    }

    public Integer getQueueSize() {
        return this.queueSize;
    }

    public void setQueueSize(final Integer queueSize) {
        if (queueSize != null && queueSize < 0) {
            throw new IllegalArgumentException("queueSize must be >= 0, got: " + queueSize);
        }
        this.queueSize = queueSize;
    }

    public boolean isDefaultAwaitWrites() {
        return this.defaultAwaitWrites;
    }

    public void setDefaultAwaitWrites(final boolean defaultAwaitWrites) {
        this.defaultAwaitWrites = defaultAwaitWrites;
    }

    public MonitorFilterType getMonitorFilterType() {
        return monitorFilterType;
    }

    public void setMonitorFilterType(MonitorFilterType monitorFilterType) {
        this.monitorFilterType = monitorFilterType;
    }

    public boolean isOmitNullValues() {
        return omitNullValues;
    }

    public void setOmitNullValues(boolean omitNullValues) {
        this.omitNullValues = omitNullValues;
    }

    public String getUsername() {
        return configuration != null ? configuration.getUsername() : null;
    }

    public void setUsername(String username) {
        if (configuration != null) {
            configuration.setUsername(username);
        }
    }

    public String getPassword() {
        return configuration != null ? configuration.getPassword() : null;
    }

    public void setPassword(String password) {
        if (configuration != null) {
            configuration.setPassword(password);
        }
    }
}
