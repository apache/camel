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

import org.apache.camel.Exchange;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.apache.camel.support.DefaultProducer;
import org.eclipse.neoscada.protocol.iec60870.asdu.ASDUHeader;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.SetPointCommandScaledValue;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.SetPointCommandShortFloatingPoint;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.SingleCommand;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.CauseOfTransmission;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.InformationObjectAddress;

public class ClientProducer extends DefaultProducer {

    private final ClientConnection connection;
    private final ASDUHeader header;
    private final InformationObjectAddress address;

    public ClientProducer(final ClientEndpoint endpoint, final ClientConnection connection) {
        super(endpoint);
        this.connection = connection;

        final ObjectAddress address = endpoint.getAddress();
        this.header = new ASDUHeader(CauseOfTransmission.ACTIVATED, address.getASDUAddress());
        this.address = address.getInformationObjectAddress();
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Object command = mapToCommand(exchange);

        if (command != null) {
            if (!this.connection.executeCommand(command)) {
                throw new IllegalStateException("Failed to send command. Not connected.");
            }
        }
    }

    private Object mapToCommand(final Exchange exchange) {
        final Object body = exchange.getIn().getBody();

        if (body instanceof Float || body instanceof Double) {
            return makeFloatCommand(((Number)body).floatValue());
        }

        if (body instanceof Boolean) {
            return makeBooleanCommand((Boolean)body);
        }

        if (body instanceof Integer || body instanceof Short || body instanceof Byte || body instanceof Long) {
            return makeIntCommand(((Number)body).longValue());
        }

        throw new IllegalArgumentException("Unable to map value to a command: " + body);
    }

    private Object makeBooleanCommand(final Boolean state) {
        return new SingleCommand(this.header, this.address, state);
    }

    private Object makeIntCommand(final long value) {

        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(String.format("Integer value is outside of range - min: %s, max: %s", Short.MIN_VALUE, Short.MAX_VALUE));
        }

        return new SetPointCommandScaledValue(this.header, this.address, (short)value);
    }

    private Object makeFloatCommand(final float value) {
        return new SetPointCommandShortFloatingPoint(this.header, this.address, value);
    }
}
