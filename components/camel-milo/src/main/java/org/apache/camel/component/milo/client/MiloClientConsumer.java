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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.milo.Messages;
import org.apache.camel.component.milo.MiloConstants;
import org.apache.camel.component.milo.client.MiloClientConnection.MonitorHandle;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiloClientConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientConsumer.class);

    private MiloClientConnection connection;
    private MonitorHandle handle;
    private Double samplingInterval;
    private Integer queueSize;
    private boolean omitNullValues;

    public MiloClientConsumer(final MiloClientEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        if (endpoint.getNode() != null && endpoint.getNodes() != null) {
            throw new IllegalArgumentException("Configure either the node or the nodes option, not both");
        }
        this.samplingInterval = endpoint.getSamplingInterval();
        this.queueSize = endpoint.getQueueSize();
        this.omitNullValues = endpoint.isOmitNullValues();
    }

    @Override
    public MiloClientEndpoint getEndpoint() {
        return (MiloClientEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final Map<ExpandedNodeId, Consumer<DataValue>> valueConsumers = new LinkedHashMap<>();
        resolveNodes().forEach((nodeId, node) -> valueConsumers.put(nodeId, value -> handleValueUpdate(node, value)));
        this.connection = getEndpoint().createConnection();
        this.handle = this.connection.monitorValues(valueConsumers, this.samplingInterval, this.queueSize,
                getEndpoint().getMonitorFilterConfiguration());
    }

    /**
     * The node IDs of the node or nodes option, in the configured order and without duplicates. A node that is listed
     * in several spellings is monitored once, the value is the first spelling of the node ID.
     */
    private Map<ExpandedNodeId, String> resolveNodes() {
        final Map<ExpandedNodeId, String> result = new LinkedHashMap<>();

        final String node = getEndpoint().getNode();
        if (node != null) {
            result.put(parseNodeId(node), node);
            return result;
        }

        final String nodes = getEndpoint().getNodes();
        if (nodes != null) {
            Arrays.stream(nodes.split(",")).map(String::trim).filter(entry -> !entry.isEmpty())
                    .forEach(entry -> result.putIfAbsent(parseNodeId(entry), entry));
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No node to monitor, configure the node or the nodes option");
        }
        return result;
    }

    private static ExpandedNodeId parseNodeId(final String node) {
        try {
            return ExpandedNodeId.parse(node);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid node ID: " + node, e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (this.handle != null) {
            this.handle.unregister();
            this.handle = null;
        }
        if (this.connection != null) {
            getEndpoint().releaseConnection(connection);
        }
        super.doStop();
    }

    private void handleValueUpdate(final String node, final DataValue value) {
        LOG.debug("Handle item update - {} = {}", node, value);

        if (omitNullValues && isNullValueWithGoodStatus(value)) {
            LOG.debug("Handle item update omitted due to a null value with a Good status (see omitNullValues parameter)");
            return;
        }

        final Exchange exchange = createExchange(true);
        try {
            mapToMessage(value, exchange.getMessage());
            exchange.getMessage().setHeader(MiloConstants.HEADER_NODE_ID, node);
            getProcessor().process(exchange);
        } catch (final Exception e) {
            getExceptionHandler().handleException("Error processing exchange", e);
        }
    }

    /**
     * The data change that omitNullValues omits: a null value with a Good status. A data change with a Bad or Uncertain
     * status is delivered even when its value is null.
     */
    private static boolean isNullValueWithGoodStatus(final DataValue value) {
        return value == null
                || (value.getValue().getValue() == null && value.getStatusCode().isGood());
    }

    private void mapToMessage(final DataValue value, final Message message) {
        if (value != null) {
            Messages.fillFromDataValue(value, message);
        }
    }

}
