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
package org.apache.camel.component.milo.server;

import java.util.function.Consumer;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.milo.Messages;
import org.apache.camel.component.milo.server.internal.CamelServerItem;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultMessage;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

public class MiloServerConsumer extends DefaultConsumer {

    private final CamelServerItem item;
    private final Consumer<DataValue> writeHandler = this::performWrite;

    public MiloServerConsumer(final Endpoint endpoint, final Processor processor, final CamelServerItem item) {
        super(endpoint, processor);
        this.item = item;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.item.addWriteListener(this.writeHandler);
    }

    @Override
    protected void doStop() throws Exception {
        this.item.removeWriteListener(this.writeHandler);

        super.doStop();
    }

    protected void performWrite(final DataValue value) {

        final Exchange exchange = getEndpoint().createExchange();
        exchange.setIn(mapToMessage(value));

        try {
            getAsyncProcessor().process(exchange);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DefaultMessage mapToMessage(final DataValue value) {
        if (value == null) {
            return null;
        }

        final DefaultMessage result = new DefaultMessage(getEndpoint().getCamelContext());

        Messages.fillFromDataValue(value, result);

        return result;
    }

}
