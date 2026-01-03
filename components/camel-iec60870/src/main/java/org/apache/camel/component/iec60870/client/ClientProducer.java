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
import org.apache.camel.Message;
import org.apache.camel.component.iec60870.Constants;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.apache.camel.support.DefaultProducer;
import org.eclipse.neoscada.protocol.iec60870.asdu.ASDUHeader;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.SetPointCommandScaledValue;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.SetPointCommandShortFloatingPoint;
import org.eclipse.neoscada.protocol.iec60870.asdu.message.SingleCommand;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.ASDUAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.CauseOfTransmission;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.InformationObjectAddress;
import org.eclipse.neoscada.protocol.iec60870.asdu.types.QualifierOfInterrogation;

public class ClientProducer extends DefaultProducer {

    private final ClientConnection connection;
    private final ASDUHeader header;
    private final InformationObjectAddress address;
    private final ObjectAddress objectAddress;

    public ClientProducer(final ClientEndpoint endpoint, final ClientConnection connection) {
        super(endpoint);
        this.connection = connection;

        this.objectAddress = endpoint.getAddress();
        this.header = new ASDUHeader(CauseOfTransmission.ACTIVATED, objectAddress.getASDUAddress());
        this.address = objectAddress.getInformationObjectAddress();
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Message message = exchange.getIn();
        final String commandType
                = message.getHeader(Constants.IEC60870_COMMAND_TYPE, Constants.COMMAND_TYPE_VALUE, String.class);

        switch (commandType) {
            case Constants.COMMAND_TYPE_INTERROGATION:
                processInterrogation(message);
                break;
            case Constants.COMMAND_TYPE_READ:
                processRead(message);
                break;
            case Constants.COMMAND_TYPE_STATUS:
                // Status command only retrieves connection state, no protocol command sent
                break;
            case Constants.COMMAND_TYPE_VALUE:
            default:
                processValueCommand(exchange);
                break;
        }

        // Always set connection state headers on the exchange after processing
        setConnectionStateHeaders(exchange);
    }

    /**
     * Sets connection state headers on the exchange message.
     */
    private void setConnectionStateHeaders(final Exchange exchange) {
        Message out = exchange.getMessage();
        out.setHeader(Constants.IEC60870_CONNECTION_STATE, connection.getConnectionState());
        out.setHeader(Constants.IEC60870_CONNECTION_UPTIME, connection.getConnectionUptime());
    }

    /**
     * Process a value command (single, scaled, or float setpoint).
     */
    private void processValueCommand(final Exchange exchange) {
        final Object command = mapToCommand(exchange);

        if (command != null) {
            if (!this.connection.executeCommand(command)) {
                throw new IllegalStateException("Failed to send command. Not connected.");
            }
        }
    }

    /**
     * Process an interrogation command (C_IC_NA_1).
     */
    private void processInterrogation(final Message message) {
        // Get ASDU address from header, default to the endpoint's ASDU address or BROADCAST
        ASDUAddress asduAddress = message.getHeader(Constants.IEC60870_ASDU_ADDRESS, ASDUAddress.class);
        if (asduAddress == null) {
            asduAddress = objectAddress.getASDUAddress();
        }

        // Get QOI from header, default to GLOBAL (20)
        Short qoiValue = message.getHeader(Constants.IEC60870_QOI, Short.class);
        short qoi;
        if (qoiValue != null) {
            qoi = qoiValue;
        } else {
            qoi = QualifierOfInterrogation.GLOBAL;
        }

        if (!this.connection.startInterrogation(asduAddress, qoi)) {
            throw new IllegalStateException("Failed to send interrogation command. Not connected.");
        }
    }

    /**
     * Process a read command (C_RD_NA_1).
     */
    private void processRead(final Message message) {
        // Get ASDU address from header, default to the endpoint's ASDU address
        ASDUAddress asduAddress = message.getHeader(Constants.IEC60870_ASDU_ADDRESS, ASDUAddress.class);
        if (asduAddress == null) {
            asduAddress = objectAddress.getASDUAddress();
        }

        // Use the endpoint's IOA for the read command
        if (!this.connection.readValue(asduAddress, this.address)) {
            throw new IllegalStateException("Failed to send read command. Not connected.");
        }
    }

    private Object mapToCommand(final Exchange exchange) {
        final Object body = exchange.getIn().getBody();

        if (body instanceof Float || body instanceof Double) {
            return makeFloatCommand(((Number) body).floatValue());
        }

        if (body instanceof Boolean) {
            return makeBooleanCommand((Boolean) body);
        }

        if (body instanceof Integer || body instanceof Short || body instanceof Byte || body instanceof Long) {
            return makeIntCommand(((Number) body).longValue());
        }

        throw new IllegalArgumentException("Unable to map value to a command: " + body);
    }

    private Object makeBooleanCommand(final Boolean state) {
        return new SingleCommand(this.header, this.address, state);
    }

    private Object makeIntCommand(final long value) {

        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                    String.format("Integer value is outside of range - min: %s, max: %s", Short.MIN_VALUE, Short.MAX_VALUE));
        }

        return new SetPointCommandScaledValue(this.header, this.address, (short) value);
    }

    private Object makeFloatCommand(final float value) {
        return new SetPointCommandShortFloatingPoint(this.header, this.address, value);
    }
}
