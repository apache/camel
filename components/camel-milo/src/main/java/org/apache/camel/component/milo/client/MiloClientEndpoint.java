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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;

import static org.apache.camel.component.milo.MiloConstants.SCHEME_CLIENT;

/**
 * Read, write and monitor the values of nodes, and call methods, on an OPC UA server.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = SCHEME_CLIENT, syntax = "milo-client:endpointUri", title = "OPC UA Client",
             category = { Category.IOT }, headersClass = MiloConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   javaType = "org.eclipse.milo.opcua.stack.core.types.builtin.DataValue",
                                   description = "The Milo DataValue of each change of a monitored node: its"
                                                 + " getValue().getValue() is the value, and the status code tells a good"
                                                 + " value from a lost connection. The node is in the CamelMiloNodeId header"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.VALUE, Carrier.POJO, Carrier.NULL },
                                  description = "The value to write to the node, or the input arguments of the method: an"
                                                + " OPC UA built-in value such as a String, a number, a boolean, a Variant"
                                                + " or a DataValue. A stream, a file, a Map or a bean fails with"
                                                + " Bad_EncodingError. With the CamelMiloNodeIds header the body is not read:"
                                                + " the producer reads the values of those nodes"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = @BodyType.When(option = "defaultAwaitWrites", value = "true",
                                                             carriers = { Carrier.POJO, Carrier.LIST }),
                                   description = "The producer does not wait, unless the CamelMiloAwait header or"
                                                 + " defaultAwaitWrites says so; it then sets the result: the StatusCode"
                                                 + " of a write, the CallMethodResult of a method call, or a List of"
                                                 + " DataValue of the nodes read"))
public class MiloClientEndpoint extends DefaultEndpoint {

    private final MiloClientConnectionManager connectionManager;

    @UriPath(description = "The endpoint URL of the OPC UA server, for example opc.tcp://localhost:4840/path. It can hold a"
                           + " user name and password in its user info part. The username and password options take"
                           + " precedence over them.")
    @Metadata(required = true)
    private final String endpointUri;

    @UriParam(description = "The node ID of the node that the consumer monitors and that the producer writes the body to,"
                            + " for example ns=2;s=item or nsu=urn:org:apache:camel;s=item. With the method option, the node"
                            + " ID of the object that the method is called on.")
    private String node;

    @UriParam(label = "consumer",
              description = "A comma-separated list of the node IDs that the consumer monitors, instead of the node option,"
                            + " which must then not be set. The CamelMiloNodeId header of each exchange tells which node the"
                            + " value is of.")
    private String nodes;

    @UriParam(description = "The node ID of a method. When set, the producer calls this method on the object of the node"
                            + " option, with the input arguments from the body, instead of writing the body to the node.")
    private String method;

    @UriParam(defaultValue = "1000.0",
              description = "The sampling interval in milliseconds that the consumer requests for its monitored items: how"
                            + " often the server checks the node for a change.")
    private Double samplingInterval = 1000.0;

    @UriParam(description = "The queue size used for OPC UA subscriptions. If not set, a queue size of 1 is requested.")
    private Integer queueSize;

    /**
     * The client configuration
     */
    @UriParam
    private MiloClientConfiguration configuration;

    @UriParam(description = "Whether the producer waits for the outcome of its request and sets it as the body: the"
                            + " StatusCode of a write, the CallMethodResult of a method call, or the List of DataValue of a"
                            + " read. If false, the route goes on at once and a failed request is not reported. The"
                            + " CamelMiloAwait header replaces this option.")
    private boolean defaultAwaitWrites;

    @UriParam(defaultValue = "true",
              description = "Omit a data change whose value is null and whose status is Good. A data change with a Bad or"
                            + " Uncertain status is always delivered.")
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
        final MiloClientConnection connection
                = this.connectionManager.createConnection(configuration, monitorFilterConfiguration);
        connection.setCamelContext(getCamelContext());
        return connection;
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
