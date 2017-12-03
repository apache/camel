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
package org.apache.camel.component.mllp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mllp.impl.Hl7Util;
import org.apache.camel.component.mllp.impl.MllpBufferedSocketWriter;
import org.apache.camel.component.mllp.impl.MllpSocketReader;
import org.apache.camel.component.mllp.impl.MllpSocketUtil;
import org.apache.camel.component.mllp.impl.MllpSocketWriter;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.IOHelper;

import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CLOSE_CONNECTION_AFTER_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CLOSE_CONNECTION_BEFORE_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_LOCAL_ADDRESS;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_REMOTE_ADDRESS;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RESET_CONNECTION_AFTER_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RESET_CONNECTION_BEFORE_SEND;
import static org.apache.camel.component.mllp.MllpEndpoint.SEGMENT_DELIMITER;

/**
 * The MLLP producer.
 */
@ManagedResource(description = "MllpTcpClient Producer")
public class MllpTcpClientProducer extends DefaultProducer {
    MllpEndpoint endpoint;

    Socket socket;

    MllpSocketReader mllpSocketReader;
    MllpSocketWriter mllpSocketWriter;

    public MllpTcpClientProducer(MllpEndpoint endpoint) throws SocketException {
        super(endpoint);
        log.trace("MllpTcpClientProducer(endpoint)");

        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        log.trace("doStart()");

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.trace("doStop()");

        MllpSocketUtil.close(socket, log, "Stopping component");

        super.doStop();
    }

    @ManagedOperation(description = "Close client socket")
    public void closeMllpSocket() {
        MllpSocketUtil.close(socket, log, "JMX triggered closing socket");
    }

    @ManagedOperation(description = "Reset client socket")
    public void resetMllpSocket() {
        MllpSocketUtil.reset(socket, log, "JMX triggered resetting socket");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        log.trace("process(exchange)");

        // Check BEFORE_SEND Properties
        if (exchange.getProperty(MLLP_RESET_CONNECTION_BEFORE_SEND, boolean.class)) {
            MllpSocketUtil.reset(socket, log, "Exchange property " + MLLP_RESET_CONNECTION_BEFORE_SEND + " = " + exchange.getProperty(MLLP_RESET_CONNECTION_BEFORE_SEND, boolean.class));
            return;
        } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_BEFORE_SEND, boolean.class)) {
            MllpSocketUtil.close(socket, log, "Exchange property " + MLLP_CLOSE_CONNECTION_BEFORE_SEND + " = " + exchange.getProperty(MLLP_CLOSE_CONNECTION_BEFORE_SEND, boolean.class));
            return;
        }

        // Establish a connection if needed
        try {
            checkConnection();
        } catch (IOException ioEx) {
            exchange.setException(ioEx);
            return;
        }

        Message message;
        if (exchange.hasOut()) {
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }

        message.setHeader(MLLP_LOCAL_ADDRESS, socket.getLocalAddress().toString());
        message.setHeader(MLLP_REMOTE_ADDRESS, socket.getRemoteSocketAddress().toString());

        // Send the message to the external system
        byte[] hl7MessageBytes = message.getMandatoryBody(byte[].class);
        byte[] acknowledgementBytes = null;

        try {
            log.debug("Sending message to external system");
            mllpSocketWriter.writeEnvelopedPayload(hl7MessageBytes, null);
            log.debug("Reading acknowledgement from external system");
            acknowledgementBytes = mllpSocketReader.readEnvelopedPayload(hl7MessageBytes);
        } catch (MllpWriteException writeEx) {
            MllpSocketUtil.reset(socket, log, writeEx.getMessage());
            exchange.setException(writeEx);
            return;
        } catch (MllpReceiveException ackReceiveEx) {
            MllpSocketUtil.reset(socket, log, ackReceiveEx.getMessage());
            exchange.setException(ackReceiveEx);
            return;
        } catch (MllpException mllpEx) {
            Throwable mllpExCause = mllpEx.getCause();
            if (mllpExCause instanceof IOException) {
                MllpSocketUtil.reset(socket, log, mllpEx.getMessage());
            }
            exchange.setException(mllpEx);
            return;
        }

        log.debug("Populating message headers with the acknowledgement from the external system");
        message.setHeader(MLLP_ACKNOWLEDGEMENT, acknowledgementBytes);
        message.setHeader(MLLP_ACKNOWLEDGEMENT_STRING, new String(acknowledgementBytes, IOHelper.getCharsetName(exchange, true)));

        if (endpoint.validatePayload) {
            String exceptionMessage = Hl7Util.generateInvalidPayloadExceptionMessage(acknowledgementBytes);
            if (exceptionMessage != null) {
                exchange.setException(new MllpInvalidAcknowledgementException(exceptionMessage, hl7MessageBytes, acknowledgementBytes));
                return;
            }
        }

        log.debug("Processing the acknowledgement from the external system");
        try {
            String acknowledgementType = processAcknowledgment(hl7MessageBytes, acknowledgementBytes);
            message.setHeader(MLLP_ACKNOWLEDGEMENT_TYPE, acknowledgementType);
        } catch (MllpException mllpEx) {
            exchange.setException(mllpEx);
            return;
        }

        // Check AFTER_SEND Properties
        if (exchange.getProperty(MLLP_RESET_CONNECTION_AFTER_SEND, boolean.class)) {
            MllpSocketUtil.reset(socket, log, "Exchange property " + MLLP_RESET_CONNECTION_AFTER_SEND + " = " + exchange.getProperty(MLLP_RESET_CONNECTION_AFTER_SEND, boolean.class));
        } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_AFTER_SEND, boolean.class)) {
            MllpSocketUtil.close(socket, log, "Exchange property " + MLLP_CLOSE_CONNECTION_AFTER_SEND + " = " + exchange.getProperty(MLLP_CLOSE_CONNECTION_AFTER_SEND, boolean.class));
        }
    }

    private String processAcknowledgment(byte[] hl7MessageBytes, byte[] hl7AcknowledgementBytes) throws MllpException {
        String acknowledgementType = "";

        if (hl7AcknowledgementBytes != null && hl7AcknowledgementBytes.length > 3) {
            // Extract the acknowledgement type and check for a NACK
            byte fieldDelim = hl7AcknowledgementBytes[3];
            // First, find the beginning of the MSA segment - should be the second segment
            int msaStartIndex = -1;
            for (int i = 0; i < hl7AcknowledgementBytes.length; ++i) {
                if (SEGMENT_DELIMITER == hl7AcknowledgementBytes[i]) {
                    final byte bM = 77;
                    final byte bS = 83;
                    final byte bC = 67;
                    final byte bA = 65;
                    final byte bE = 69;
                    final byte bR = 82;
                    /* We've found the start of a new segment - make sure peeking ahead
                       won't run off the end of the array - we need at least 7 more bytes
                     */
                    if (hl7AcknowledgementBytes.length > i + 7) {
                        // We can safely peek ahead
                        if (bM == hl7AcknowledgementBytes[i + 1] && bS == hl7AcknowledgementBytes[i + 2] && bA == hl7AcknowledgementBytes[i + 3] && fieldDelim == hl7AcknowledgementBytes[i + 4]) {
                            // Found the beginning of the MSA - the next two bytes should be our acknowledgement code
                            msaStartIndex = i + 1;
                            if (bA != hl7AcknowledgementBytes[i + 5] && bC != hl7AcknowledgementBytes[i + 5]) {
                                String errorMessage = "Unsupported acknowledgement type: " + new String(hl7AcknowledgementBytes, i + 5, 2);
                                throw new MllpInvalidAcknowledgementException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
                            } else {
                                switch (hl7AcknowledgementBytes[i + 6]) {
                                case bA:
                                    // We have an AA or CA
                                    if (bA == hl7AcknowledgementBytes[i + 5]) {
                                        acknowledgementType = "AA";
                                    } else {
                                        acknowledgementType = "CA";
                                    }
                                    break;
                                case bE:
                                    // We have an AE or CE
                                    if (bA == hl7AcknowledgementBytes[i + 5]) {
                                        throw new MllpApplicationErrorAcknowledgementException(hl7MessageBytes, hl7AcknowledgementBytes);
                                    } else {
                                        throw new MllpCommitErrorAcknowledgementException(hl7MessageBytes, hl7AcknowledgementBytes);
                                    }
                                case bR:
                                    // We have an AR or CR
                                    if (bA == hl7AcknowledgementBytes[i + 5]) {
                                        throw new MllpApplicationRejectAcknowledgementException(hl7MessageBytes, hl7AcknowledgementBytes);
                                    } else {
                                        throw new MllpCommitRejectAcknowledgementException(hl7MessageBytes, hl7AcknowledgementBytes);
                                    }
                                default:
                                    String errorMessage = "Unsupported acknowledgement type: " + new String(hl7AcknowledgementBytes, i + 5, 2);
                                    throw new MllpInvalidAcknowledgementException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
                                }
                            }

                            break;
                        }
                    }
                }

            }
            if (-1 == msaStartIndex  &&  endpoint.validatePayload) {
                // Didn't find an MSA
                throw new MllpInvalidAcknowledgementException("MSA Not found in acknowledgement", hl7MessageBytes, hl7AcknowledgementBytes);
            }
        }

        return acknowledgementType;
    }

    /**
     * Validate the TCP Connection, if closed opens up the socket with
     * the value set via endpoint configuration
     *
     * @throws IOException if the connection is not valid, otherwise the Exception is not
     *                     encountered while checking the connection
     */
    private void checkConnection() throws IOException {
        if (null == socket || socket.isClosed() || !socket.isConnected()) {
            socket = new Socket();

            socket.setKeepAlive(endpoint.keepAlive);
            socket.setTcpNoDelay(endpoint.tcpNoDelay);
            if (null != endpoint.receiveBufferSize) {
                socket.setReceiveBufferSize(endpoint.receiveBufferSize);
            } else {
                endpoint.receiveBufferSize = socket.getReceiveBufferSize();
            }
            if (null != endpoint.sendBufferSize) {
                socket.setSendBufferSize(endpoint.sendBufferSize);
            } else {
                endpoint.sendBufferSize = socket.getSendBufferSize();
            }
            socket.setReuseAddress(endpoint.reuseAddress);
            socket.setSoLinger(false, -1);

            InetSocketAddress socketAddress;
            if (null == endpoint.getHostname()) {
                socketAddress = new InetSocketAddress(endpoint.getPort());
            } else {
                socketAddress = new InetSocketAddress(endpoint.getHostname(), endpoint.getPort());
            }

            log.debug("Connecting to socket on {}", socketAddress);
            socket.connect(socketAddress, endpoint.connectTimeout);

            log.debug("Creating MllpSocketReader and MllpSocketWriter");
            mllpSocketReader = new MllpSocketReader(socket, endpoint.receiveTimeout, endpoint.readTimeout, true);
            if (endpoint.bufferWrites) {
                mllpSocketWriter = new MllpBufferedSocketWriter(socket, false);
            } else {
                mllpSocketWriter = new MllpSocketWriter(socket, false);
            }
        }
        return;
    }

    @ManagedOperation(description = "Check client connection")
    public boolean managedCheckConnection() {
        boolean isValid = true;
        try {
            checkConnection();
        } catch (IOException ioEx) {
            isValid = false;
            log.debug("JMX check connection: {}", ioEx);
        }
        return isValid;
    }

}
