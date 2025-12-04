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

package org.apache.camel.component.milo.server;

import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.milo.Messages;
import org.apache.camel.component.milo.server.internal.CamelServerItem;
import org.apache.camel.support.DefaultConsumer;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

public class MiloServerConsumer extends DefaultConsumer {

    private final Consumer<DataValue> writeHandler = this::performWrite;
    private CamelServerItem item;

    public MiloServerConsumer(final MiloServerEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public MiloServerEndpoint getEndpoint() {
        return (MiloServerEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.item = getEndpoint().getItem();
        this.item.addWriteListener(this.writeHandler);
    }

    @Override
    protected void doStop() throws Exception {
        this.item.removeWriteListener(this.writeHandler);
        super.doStop();
    }

    protected void performWrite(final DataValue value) {
        Exchange exchange = createExchange(true);

        try {
            mapToMessage(value, exchange.getMessage());
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException("Error processing exchange", e);
        }
    }

    private void mapToMessage(final DataValue value, final Message message) {
        if (value != null) {
            Messages.fillFromDataValue(value, message);
        }
    }
}
