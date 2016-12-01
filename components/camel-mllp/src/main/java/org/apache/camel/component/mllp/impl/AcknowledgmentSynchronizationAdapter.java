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
package org.apache.camel.component.mllp.impl;

import java.io.IOException;
import java.net.Socket;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Route;
import org.apache.camel.component.mllp.MllpAcknowledgementDeliveryException;
import org.apache.camel.component.mllp.MllpException;
import org.apache.camel.component.mllp.MllpInvalidAcknowledgementException;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerationException;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerator;
import org.apache.camel.support.SynchronizationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_AUTO_ACKNOWLEDGE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CLOSE_CONNECTION_AFTER_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CLOSE_CONNECTION_BEFORE_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RESET_CONNECTION_AFTER_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RESET_CONNECTION_BEFORE_SEND;
import static org.apache.camel.component.mllp.MllpEndpoint.SEGMENT_DELIMITER;

public class AcknowledgmentSynchronizationAdapter extends SynchronizationAdapter {
    Logger log = LoggerFactory.getLogger(this.getClass());
    final byte[] originalHl7MessageBytes;
    Hl7AcknowledgementGenerator acknowledgementGenerator = new Hl7AcknowledgementGenerator();
    private Socket clientSocket;

    public AcknowledgmentSynchronizationAdapter(Socket clientSocket, byte[] hl7MessageBytes) {
        this.clientSocket = clientSocket;
        this.originalHl7MessageBytes = hl7MessageBytes;
    }

    @Override
    public int getOrder() {
        return HIGHEST;
    }

    @Override
    public void onAfterRoute(Route route, Exchange exchange) {
        log.info("onAfterRoute");

        // Check BEFORE_SEND Properties
        if (exchange.getProperty(MLLP_RESET_CONNECTION_BEFORE_SEND, boolean.class)) {
            MllpUtil.resetConnection(clientSocket);
            return;
        } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_BEFORE_SEND, boolean.class)) {
            MllpUtil.closeConnection(clientSocket);
            return;
        }

        // Find the acknowledgement body
        // TODO:  Enhance this to say whether or not the acknowledgment is missing or just of an unconvertible type
        byte[] acknowledgementMessageBytes = exchange.getProperty(MLLP_ACKNOWLEDGEMENT, byte[].class);
        String acknowledgementMessageType = null;
        if (null == acknowledgementMessageBytes) {
            boolean autoAck = exchange.getProperty(MLLP_AUTO_ACKNOWLEDGE, true, boolean.class);
            if (!autoAck) {
                exchange.setException(new MllpInvalidAcknowledgementException("Automatic Acknowledgement is disabled and the "
                        + MLLP_ACKNOWLEDGEMENT + " exchange property is null or cannot be converted to byte[]", originalHl7MessageBytes, acknowledgementMessageBytes));
                return;
            }

            String acknowledgmentTypeProperty = exchange.getProperty(MLLP_ACKNOWLEDGEMENT_TYPE, String.class);
            try {
                if (null == acknowledgmentTypeProperty) {
                    if (null == exchange.getException()) {
                        acknowledgementMessageType = "AA";
                        acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationAcceptAcknowledgementMessage(originalHl7MessageBytes);
                    } else {
                        acknowledgementMessageType = "AE";
                        acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationErrorAcknowledgementMessage(originalHl7MessageBytes);
                    }
                } else {
                    switch (acknowledgmentTypeProperty) {
                    case "AA":
                        acknowledgementMessageType = "AA";
                        acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationAcceptAcknowledgementMessage(originalHl7MessageBytes);
                        break;
                    case "AE":
                        acknowledgementMessageType = "AE";
                        acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationErrorAcknowledgementMessage(originalHl7MessageBytes);
                        break;
                    case "AR":
                        acknowledgementMessageType = "AR";
                        acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationRejectAcknowledgementMessage(originalHl7MessageBytes);
                        break;
                    default:
                        exchange.setException(new Hl7AcknowledgementGenerationException("Unsupported acknowledgment type: " + acknowledgmentTypeProperty));
                        return;
                    }
                }
            } catch (Hl7AcknowledgementGenerationException ackGenerationException) {
                exchange.setProperty(MLLP_ACKNOWLEDGEMENT_EXCEPTION, ackGenerationException);
                exchange.setException(ackGenerationException);
            }
        } else {
            final byte bM = 77;
            final byte bS = 83;
            final byte bA = 65;
            final byte bE = 69;
            final byte bR = 82;

            final byte fieldSeparator = originalHl7MessageBytes[3];
            // Acknowledgment is specified in exchange property - determine the acknowledgement type
            for (int i = 0; i < originalHl7MessageBytes.length; ++i) {
                if (SEGMENT_DELIMITER == i) {
                    if (i + 7 < originalHl7MessageBytes.length // Make sure we don't run off the end of the message
                            && bM == originalHl7MessageBytes[i + 1] && bS == originalHl7MessageBytes[i + 2]
                            && bA == originalHl7MessageBytes[i + 3] && fieldSeparator == originalHl7MessageBytes[i + 4]) {
                        if (fieldSeparator != originalHl7MessageBytes[i + 7]) {
                            log.warn("MSA-1 is longer than 2-bytes - ignoring trailing bytes");
                        }
                        // Found MSA - pull acknowledgement bytes
                        byte[] acknowledgmentTypeBytes = new byte[2];
                        acknowledgmentTypeBytes[0] = originalHl7MessageBytes[i + 5];
                        acknowledgmentTypeBytes[1] = originalHl7MessageBytes[i + 6];
                        try {
                            acknowledgementMessageType = IOConverter.toString(acknowledgmentTypeBytes, exchange);
                        } catch (IOException ioEx) {
                            throw new RuntimeException("Failed to convert acknowledgement message to string", ioEx);
                        }

                        // Verify it's a valid acknowledgement code
                        if (bA != acknowledgmentTypeBytes[0]) {
                            switch (acknowledgementMessageBytes[1]) {
                            case bA:
                            case bR:
                            case bE:
                                break;
                            default:
                                log.warn("Invalid acknowledgement type [" + acknowledgementMessageType + "] found in message - should be AA, AE or AR");
                            }
                        }

                        // if the MLLP_ACKNOWLEDGEMENT_TYPE property is set on the exchange, make sure it matches
                        String acknowledgementTypeProperty = exchange.getProperty(MLLP_ACKNOWLEDGEMENT_TYPE, String.class);
                        if (null != acknowledgementTypeProperty && !acknowledgementTypeProperty.equals(acknowledgementMessageType)) {
                            log.warn("Acknowledgement type found in message [" + acknowledgementMessageType + "] does not match "
                                    + MLLP_ACKNOWLEDGEMENT_TYPE + " exchange property value [" + acknowledgementTypeProperty + "] - using value found in message");
                        }
                    }
                }
            }
        }

        Message message;
        if (exchange.hasOut()) {
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }
        message.setHeader(MLLP_ACKNOWLEDGEMENT, acknowledgementMessageBytes);
        message.setHeader(MLLP_ACKNOWLEDGEMENT_TYPE, acknowledgementMessageType);

        // Send the acknowledgement
        log.debug("Sending Acknowledgement");
        try {
            MllpUtil.writeFramedPayload(clientSocket, acknowledgementMessageBytes);
        } catch (MllpException mllpEx) {
            log.error("MLLP Acknowledgement failure: {}", mllpEx);
            MllpAcknowledgementDeliveryException deliveryException = new MllpAcknowledgementDeliveryException(originalHl7MessageBytes, acknowledgementMessageBytes, mllpEx);
            exchange.setProperty(MLLP_ACKNOWLEDGEMENT_EXCEPTION, deliveryException);
            exchange.setException(deliveryException);
        }

        // Check AFTER_SEND Properties
        if (exchange.getProperty(MLLP_RESET_CONNECTION_AFTER_SEND, boolean.class)) {
            MllpUtil.resetConnection(clientSocket);
            return;
        } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_AFTER_SEND, boolean.class)) {
            MllpUtil.closeConnection(clientSocket);
        }

        super.onAfterRoute(route, exchange);
    }

    @Override
    public void onComplete(Exchange exchange) {
        log.info("onComplete");
        super.onComplete(exchange);

    }

    @Override
    public void onFailure(Exchange exchange) {
        log.warn("onFailure");
        super.onFailure(exchange);
    }
}
