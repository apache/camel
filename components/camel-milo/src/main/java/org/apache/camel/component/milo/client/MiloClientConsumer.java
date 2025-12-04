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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.milo.Messages;
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
    private ExpandedNodeId node;
    private Double samplingInterval;
    private boolean omitNullValues;

    public MiloClientConsumer(final MiloClientEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        this.node = endpoint.getNodeId();
        this.samplingInterval = endpoint.getSamplingInterval();
        this.omitNullValues = endpoint.isOmitNullValues();
    }

    @Override
    public MiloClientEndpoint getEndpoint() {
        return (MiloClientEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.connection = getEndpoint().createConnection();
        this.handle = this.connection.monitorValue(this.node, this.samplingInterval, this::handleValueUpdate);
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

    private void handleValueUpdate(final DataValue value) {
        LOG.debug("Handle item update - {} = {}", node, value);

        if (omitNullValues
                && (value == null
                        || value.getValue() == null
                        || value.getValue().getValue() == null)) {
            LOG.debug("Handle item update omitted due to null values (see omitNullValues parameter)");
            return;
        }

        final Exchange exchange = createExchange(true);
        try {
            mapToMessage(value, exchange.getMessage());
            getProcessor().process(exchange);
        } catch (final Exception e) {
            getExceptionHandler().handleException("Error processing exchange", e);
        }
    }

    private void mapToMessage(final DataValue value, final Message message) {
        if (value != null) {
            Messages.fillFromDataValue(value, message);
        }
    }
}
