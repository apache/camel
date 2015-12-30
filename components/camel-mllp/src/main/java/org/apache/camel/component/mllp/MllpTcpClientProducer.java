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
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mllp.impl.MllpUtil;
import org.apache.camel.impl.DefaultProducer;

import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT;
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
public class MllpTcpClientProducer extends DefaultProducer {
    MllpEndpoint endpoint;

    Socket socket;

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

        MllpUtil.closeConnection(socket);

        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        log.trace("process(exchange)");

        // Check BEFORE_SEND Properties
        if (exchange.getProperty(MLLP_RESET_CONNECTION_BEFORE_SEND, boolean.class)) {
            MllpUtil.resetConnection(socket);
            return;
        } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_BEFORE_SEND, boolean.class)) {
            MllpUtil.closeConnection(socket);
        }

        Exception connectionException = checkConnection();
        if (null != connectionException) {
            exchange.setException(connectionException);
            return;
        }

        Message message;
        if (exchange.hasOut()) {
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }

        byte[] hl7MessageBytes = message.getMandatoryBody(byte[].class);

        log.debug("Sending message to external system");
        try {
            MllpUtil.writeFramedPayload(socket, hl7MessageBytes);
        } catch (MllpException mllpEx) {
            exchange.setException(mllpEx);
            return;
        }

        log.debug("Reading acknowledgement from external system");
        byte[] acknowledgementBytes;
        try {
            MllpUtil.openFrame(socket);
            acknowledgementBytes = MllpUtil.closeFrame(socket);
        } catch (SocketTimeoutException timeoutEx) {
            exchange.setException(new MllpAcknowledgementTimoutException("Acknowledgement timout", timeoutEx));
            return;
        } catch (MllpException mllpEx) {
            exchange.setException(mllpEx);
            return;
        }

        log.debug("Populating the exchange with the acknowledgement from the external system");
        message.setHeader(MLLP_ACKNOWLEDGEMENT, acknowledgementBytes);

        message.setHeader(MLLP_LOCAL_ADDRESS, socket.getLocalAddress().toString());
        message.setHeader(MLLP_REMOTE_ADDRESS, socket.getRemoteSocketAddress());

        // Now, extract the acknowledgement type and check for a NACK
        byte fieldDelim = acknowledgementBytes[3];
        // First, find the beginning of the MSA segment - should be the second segment
        int msaStartIndex = -1;
        for (int i = 0; i < acknowledgementBytes.length; ++i) {
            if (SEGMENT_DELIMITER == acknowledgementBytes[i]) {
                final byte bM = 77;
                final byte bS = 83;
                final byte bA = 65;
                final byte bE = 69;
                final byte bR = 82;
                        /* We've found the start of a new segment - make sure peeking ahead
                           won't run off the end of the array - we need at least 7 more bytes
                         */
                if (acknowledgementBytes.length > i + 7) {
                    // We can safely peek ahead
                    if (bM == acknowledgementBytes[i + 1] && bS == acknowledgementBytes[i + 2] && bA == acknowledgementBytes[i + 3] && fieldDelim == acknowledgementBytes[i + 4]) {
                        // Found the beginning of the MSA - the next two bytes should be our acknowledgement code
                        msaStartIndex = i + 1;
                        if (bA != acknowledgementBytes[i + 5]) {
                            exchange.setException(new MllpInvalidAcknowledgementException(new String(acknowledgementBytes)));
                        } else {
                            switch (acknowledgementBytes[i + 6]) {
                            case bA:
                                // We have an AA - make sure that's the end of the field
                                if (fieldDelim != acknowledgementBytes[i + 7]) {
                                    exchange.setException(new MllpInvalidAcknowledgementException(new String(acknowledgementBytes)));
                                }
                                message.setHeader(MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
                                break;
                            case bE:
                                // We have an AE
                                exchange.setException(new MllpApplicationErrorAcknowledgementException(new String(acknowledgementBytes)));
                                message.setHeader(MLLP_ACKNOWLEDGEMENT_TYPE, "AE");
                                break;
                            case bR:
                                exchange.setException(new MllpApplicationRejectAcknowledgementException(new String(acknowledgementBytes)));
                                message.setHeader(MLLP_ACKNOWLEDGEMENT_TYPE, "AR");
                                break;
                            default:
                                exchange.setException(new MllpInvalidAcknowledgementException(new String(acknowledgementBytes)));
                            }
                        }

                        break;
                    }
                }
            }

        }
        if (-1 == msaStartIndex) {
            // Didn't find an MSA
            exchange.setException(new MllpInvalidAcknowledgementException(new String(acknowledgementBytes)));
        }
        // Check AFTER_SEND Properties
        if (exchange.getProperty(MLLP_RESET_CONNECTION_AFTER_SEND, boolean.class)) {
            MllpUtil.resetConnection(socket);
            return;
        } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_AFTER_SEND, boolean.class)) {
            MllpUtil.closeConnection(socket);
        }
    }

    /**
     * Validate the TCP Connection
     *
     * @return null if the connection is valid, otherwise the Exception encounted checking the connection
     */
    Exception checkConnection() {
        if (null == socket || socket.isClosed() || !socket.isConnected()) {
            socket = new Socket();

            try {
                socket.setKeepAlive(endpoint.keepAlive);
                socket.setTcpNoDelay(endpoint.tcpNoDelay);
                if (null != endpoint.receiveBufferSize) {
                    socket.setReceiveBufferSize(endpoint.receiveBufferSize);
                }
                if (null != endpoint.sendBufferSize) {
                    socket.setSendBufferSize(endpoint.sendBufferSize);
                }
                socket.setReuseAddress(endpoint.reuseAddress);
                socket.setSoLinger(false, -1);

                // Read Timeout
                socket.setSoTimeout(endpoint.responseTimeout);
            } catch (SocketException e) {
                return e;
            }


            SocketAddress address = new InetSocketAddress(endpoint.getHostname(), endpoint.getPort());
            log.debug("Connecting to socket on {}", address);
            try {
                socket.connect(address, endpoint.connectTimeout);
            } catch (SocketTimeoutException e) {
                return e;
            } catch (IOException e) {
                return e;
            }
        }

        return null;
    }

}
