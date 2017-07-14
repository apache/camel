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
package org.apache.camel.component.milo.client;

import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.milo.Messages;
import org.apache.camel.component.milo.client.MiloClientConnection.MonitorHandle;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultMessage;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiloClientConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientConsumer.class);

    private final MiloClientConnection connection;

    private final MiloClientItemConfiguration configuration;

    private MonitorHandle handle;

    public MiloClientConsumer(final MiloClientEndpoint endpoint, final Processor processor, final MiloClientConnection connection,
                              final MiloClientItemConfiguration configuration) {
        super(endpoint, processor);

        Objects.requireNonNull(connection);
        Objects.requireNonNull(configuration);

        this.connection = connection;
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.handle = this.connection.monitorValue(this.configuration, this::handleValueUpdate);
    }

    @Override
    protected void doStop() throws Exception {
        if (this.handle != null) {
            this.handle.unregister();
            this.handle = null;
        }

        super.doStop();
    }

    private void handleValueUpdate(final DataValue value) {
        final Exchange exchange = getEndpoint().createExchange();
        exchange.setIn(mapMessage(value));
        try {
            getAsyncProcessor().process(exchange);
        } catch (final Exception e) {
            LOG.debug("Failed to process message", e);
        }
    }

    private Message mapMessage(final DataValue value) {
        if (value == null) {
            return null;
        }

        final DefaultMessage result = new DefaultMessage(getEndpoint().getCamelContext());

        Messages.fillFromDataValue(value, result);

        return result;
    }

}
