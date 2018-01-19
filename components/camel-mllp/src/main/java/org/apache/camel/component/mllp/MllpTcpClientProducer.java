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
import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mllp.internal.Hl7Util;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.apache.camel.impl.DefaultProducer;

/**
 * The MLLP producer.
 */
@ManagedResource(description = "MLLP Producer")
public class MllpTcpClientProducer extends DefaultProducer implements Runnable {
    Socket socket;

    final MllpSocketBuffer mllpBuffer;

    ScheduledExecutorService idleTimeoutExecutor;
    long lastProcessCallTicks = -1;

    private String cachedLocalAddress;
    private String cachedRemoteAddress;
    private String cachedCombinedAddress;

    public MllpTcpClientProducer(MllpEndpoint endpoint) throws SocketException {
        super(endpoint);
        log.trace("Constructing MllpTcpClientProducer for endpoint URI {}", endpoint.getEndpointUri());

        mllpBuffer = new MllpSocketBuffer(endpoint);
    }

    @ManagedAttribute(description = "Last activity time")
    public Date getLastActivityTime() {
        return new Date(lastProcessCallTicks);
    }

    @ManagedAttribute(description = "Connection")
    public String getConnectionAddress() {
        if (cachedCombinedAddress != null) {
            return cachedCombinedAddress;
        }

        return MllpSocketBuffer.formatAddressString(null, null);
    }

    @ManagedOperation(description = "Close Connection")
    public void closeConnection() {
        log.info("Close Connection for address {} called via JMX", getConnectionAddress());

        mllpBuffer.closeSocket(socket);
    }

    @ManagedOperation(description = "Reset Connection")
    public void resetConnection() {
        log.info("Reset Connection for address {} requested via JMX", getConnectionAddress());

        mllpBuffer.resetSocket(socket);
    }

    @Override
    protected void doStart() throws Exception {
        if (getConfiguration().hasIdleTimeout()) {
            // Get the URI without options
            String fullEndpointKey = getEndpoint().getEndpointKey();
            String endpointKey;
            if (fullEndpointKey.contains("?")) {
                endpointKey = fullEndpointKey.substring(0, fullEndpointKey.indexOf('?'));
            } else {
                endpointKey = fullEndpointKey;
            }

            idleTimeoutExecutor = Executors.newSingleThreadScheduledExecutor(new IdleTimeoutThreadFactory(endpointKey));
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (idleTimeoutExecutor != null) {
            idleTimeoutExecutor.shutdown();
            idleTimeoutExecutor = null;
        }

        mllpBuffer.resetSocket(socket);

        super.doStop();
    }

    @Override
    public synchronized void process(Exchange exchange) throws Exception {
        log.trace("Processing Exchange {}", exchange.getExchangeId());

        Message message = exchange.hasOut() ? exchange.getOut() : exchange.getIn();

        getEndpoint().checkBeforeSendProperties(exchange, socket, log);

        // Establish a connection if needed
        try {
            checkConnection();

            if (cachedLocalAddress != null) {
                message.setHeader(MllpConstants.MLLP_LOCAL_ADDRESS, cachedLocalAddress);
            }

            if (cachedRemoteAddress != null) {
                message.setHeader(MllpConstants.MLLP_REMOTE_ADDRESS, cachedRemoteAddress);
            }

            // Send the message to the external system
            byte[] hl7MessageBytes = null;
            Object messageBody = message.getBody();
            if (messageBody == null) {
                exchange.setException(new MllpInvalidMessageException("message body is null", hl7MessageBytes));
                return;
            } else if (messageBody instanceof byte[]) {
                hl7MessageBytes = (byte[]) messageBody;
            } else if (messageBody instanceof String) {
                String stringBody = (String) messageBody;
                byte[] tmpHl7MessageBytes = stringBody.getBytes(MllpProtocolConstants.DEFAULT_CHARSET);
                Charset tmpCharset = getEndpoint().determineCharset(tmpHl7MessageBytes, null);
                exchange.setProperty(Exchange.CHARSET_NAME, tmpCharset.name());
                if (tmpCharset != null && tmpCharset != MllpProtocolConstants.DEFAULT_CHARSET) {
                    hl7MessageBytes = stringBody.getBytes(tmpCharset);
                    exchange.setProperty(Exchange.CHARSET_NAME, tmpCharset.name());
                } else {
                    hl7MessageBytes = tmpHl7MessageBytes;
                    exchange.setProperty(Exchange.CHARSET_NAME, MllpProtocolConstants.DEFAULT_CHARSET.name());
                }
            }

            log.debug("Sending message to external system");
            getEndpoint().updateLastConnectionEstablishedTicks();

            try {
                mllpBuffer.setEnvelopedMessage(hl7MessageBytes);
                mllpBuffer.writeTo(socket);
            } catch (MllpSocketException writeEx) {
                // Connection may have been reset - try one more time
                log.debug("Exception encountered reading acknowledgement - attempting reconnect", writeEx);
                try {
                    checkConnection();
                    log.trace("Reconnected succeeded - resending payload");
                    try {
                        mllpBuffer.writeTo(socket);
                    } catch (MllpSocketException retryWriteEx) {
                        exchange.setException(retryWriteEx);
                    }
                } catch (IOException reconnectEx) {
                    log.debug("Reconnected failed - sending exception to exchange", reconnectEx);
                    exchange.setException(reconnectEx);
                }

            }

            if (exchange.getException() == null) {
                log.debug("Reading acknowledgement from external system");
                try {
                    mllpBuffer.reset();
                    mllpBuffer.readFrom(socket);
                } catch (MllpSocketException receiveAckEx) {
                    // Connection may have been reset - try one more time
                    log.debug("Exception encountered reading acknowledgement - attempting reconnect", receiveAckEx);
                    try {
                        checkConnection();
                    } catch (IOException reconnectEx) {
                        log.debug("Reconnected failed - sending original exception to exchange", reconnectEx);
                        exchange.setException(new MllpAcknowledgementReceiveException("Exception encountered reading acknowledgement", hl7MessageBytes, receiveAckEx));
                    }

                    if (exchange.getException() == null) {
                        log.trace("Reconnected succeeded - resending payload");
                        try {
                            mllpBuffer.setEnvelopedMessage(hl7MessageBytes);
                            mllpBuffer.writeTo(socket);
                        } catch (MllpSocketException writeRetryEx) {
                            exchange.setException(new MllpWriteException("Failed to write HL7 message to socket", hl7MessageBytes, writeRetryEx));
                        }

                        if (exchange.getException() == null) {
                            log.trace("Resend succeeded - reading acknowledgement");
                            try {
                                mllpBuffer.reset();
                                mllpBuffer.readFrom(socket);
                            } catch (MllpSocketException secondReceiveEx) {
                                if (mllpBuffer.isEmpty()) {
                                    Exception exchangeEx = new MllpAcknowledgementReceiveException("Exception encountered receiving Acknowledgement", hl7MessageBytes, secondReceiveEx);
                                    exchange.setException(exchangeEx);
                                } else {
                                    byte[] partialAcknowledgment = mllpBuffer.toByteArray();
                                    mllpBuffer.reset();
                                    Exception exchangeEx = new MllpAcknowledgementReceiveException("Exception encountered receiving complete Acknowledgement",
                                        hl7MessageBytes, partialAcknowledgment, secondReceiveEx);
                                    exchange.setException(exchangeEx);
                                }
                            }
                        }
                    }
                } catch (SocketTimeoutException timeoutEx) {
                    if (mllpBuffer.isEmpty()) {
                        exchange.setException(new MllpAcknowledgementTimeoutException("Timeout receiving HL7 Acknowledgement", hl7MessageBytes, timeoutEx));
                    } else {
                        exchange.setException(new MllpAcknowledgementTimeoutException("Timeout receiving complete HL7 Acknowledgement", hl7MessageBytes, mllpBuffer.toByteArray(), timeoutEx));
                        mllpBuffer.reset();
                    }
                }

                if (exchange.getException() == null) {
                    if (mllpBuffer.hasCompleteEnvelope()) {
                        byte[] acknowledgementBytes = mllpBuffer.toMllpPayload();

                        log.debug("Populating message headers with the acknowledgement from the external system");
                        message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT, acknowledgementBytes);
                        if (acknowledgementBytes != null && acknowledgementBytes.length > 0) {
                            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING,
                                getEndpoint().createNewString(acknowledgementBytes, message.getHeader(MllpConstants.MLLP_CHARSET, String.class)));
                        } else {
                            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, "");
                        }

                        if (getConfiguration().isValidatePayload()) {
                            String exceptionMessage = Hl7Util.generateInvalidPayloadExceptionMessage(acknowledgementBytes);
                            if (exceptionMessage != null) {
                                exchange.setException(new MllpInvalidAcknowledgementException(exceptionMessage, hl7MessageBytes, acknowledgementBytes));
                            }
                        }

                        if (exchange.getException() == null) {
                            log.debug("Processing the acknowledgement from the external system");
                            try {
                                message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, processAcknowledgment(hl7MessageBytes, acknowledgementBytes));
                            } catch (MllpNegativeAcknowledgementException nackEx) {
                                message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, nackEx.getAcknowledgmentType());
                                exchange.setException(nackEx);
                            }

                            getEndpoint().checkAfterSendProperties(exchange, socket, log);
                        }
                    } else {
                        exchange.setException(new MllpInvalidAcknowledgementException("Invalid acknowledgement received", hl7MessageBytes, mllpBuffer.toByteArray()));
                    }
                }
            }

        } catch (IOException ioEx) {
            exchange.setException(ioEx);
        } finally {
            mllpBuffer.reset();
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
                if (MllpProtocolConstants.SEGMENT_DELIMITER == hl7AcknowledgementBytes[i]) {
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
            if (-1 == msaStartIndex && getConfiguration().isValidatePayload()) {
                // Didn't find an MSA
                throw new MllpInvalidAcknowledgementException("MSA Not found in acknowledgement", hl7MessageBytes, hl7AcknowledgementBytes);
            }
        }

        return acknowledgementType;
    }

    /**
     * Validate the TCP Connection
     *
     * @return null if the connection is valid, otherwise the Exception encounted checking the connection
     */
    void checkConnection() throws IOException {
        if (null == socket || socket.isClosed() || !socket.isConnected()) {
            socket = new Socket();

            if (getConfiguration().hasKeepAlive()) {
                socket.setKeepAlive(getConfiguration().getKeepAlive());
            }
            if (getConfiguration().hasTcpNoDelay()) {
                socket.setTcpNoDelay(getConfiguration().getTcpNoDelay());
            }

            if (getConfiguration().hasReceiveBufferSize()) {
                socket.setReceiveBufferSize(getConfiguration().getReceiveBufferSize());
            }
            if (getConfiguration().hasSendBufferSize()) {
                socket.setSendBufferSize(getConfiguration().getSendBufferSize());
            }
            if (getConfiguration().hasReuseAddress()) {
                socket.setReuseAddress(getConfiguration().getReuseAddress());
            }

            socket.setSoLinger(false, -1);

            InetSocketAddress socketAddress;
            if (null == getEndpoint().getHostname()) {
                socketAddress = new InetSocketAddress(getEndpoint().getPort());
            } else {
                socketAddress = new InetSocketAddress(getEndpoint().getHostname(), getEndpoint().getPort());
            }

            socket.connect(socketAddress, getConfiguration().getConnectTimeout());
            SocketAddress localSocketAddress = socket.getLocalSocketAddress();
            if (localSocketAddress != null) {
                cachedLocalAddress = localSocketAddress.toString();
            }
            SocketAddress remoteSocketAddress = socket.getRemoteSocketAddress();
            if (remoteSocketAddress != null) {
                cachedRemoteAddress = remoteSocketAddress.toString();
            }
            cachedCombinedAddress = MllpSocketBuffer.formatAddressString(localSocketAddress, remoteSocketAddress);

            log.info("checkConnection() - established new connection {}", cachedCombinedAddress);
            getEndpoint().updateLastConnectionEstablishedTicks();

            if (getConfiguration().hasIdleTimeout()) {
                idleTimeoutExecutor.schedule(this, getConfiguration().getIdleTimeout(), TimeUnit.MILLISECONDS);
            }
        } else {
            log.debug("checkConnection() - Connection is still valid - no new connection required");
        }
    }

    /**
     * Check for idle connection
     */
    @Override
    public synchronized void run() {
        if (getConfiguration().hasIdleTimeout()) {
            if (null != socket && !socket.isClosed() && socket.isConnected()) {
                if (lastProcessCallTicks > 0) {
                    long idleTime = System.currentTimeMillis() - lastProcessCallTicks;
                    if (log.isDebugEnabled()) {
                        log.debug("Checking {} for idle connection: {} - {}", getConnectionAddress(), idleTime, getConfiguration().getIdleTimeout());
                    }
                    if (idleTime >= getConfiguration().getIdleTimeout()) {
                        log.info("MLLP Connection idle time of '{}' milliseconds met or exceeded the idle producer timeout of '{}' milliseconds - resetting conection",
                            idleTime, getConfiguration().getIdleTimeout());
                        mllpBuffer.resetSocket(socket);
                    } else {
                        long minDelay = 100;
                        long delay = Long.min(Long.max(minDelay, getConfiguration().getIdleTimeout() - idleTime), getConfiguration().getIdleTimeout());
                        if (log.isDebugEnabled()) {
                            log.debug("Scheduling idle producer connection check of {} in {} milliseconds", getConnectionAddress(), delay);
                        }
                        idleTimeoutExecutor.schedule(this, delay, TimeUnit.MILLISECONDS);
                    }
                } else {
                    log.debug("Scheduling idle producer connection check in {} milliseconds", getConfiguration().getIdleTimeout());
                    idleTimeoutExecutor.schedule(this, getConfiguration().getIdleTimeout(), TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    static class IdleTimeoutThreadFactory implements ThreadFactory {
        final String endpointKey;

        IdleTimeoutThreadFactory(String endpointKey) {
            this.endpointKey = endpointKey;
        }

        public Thread newThread(Runnable r) {
            Thread timeoutThread = Executors.defaultThreadFactory().newThread(r);

            timeoutThread.setName(String.format("%s[%s]-idle-timeout-thread", MllpTcpClientProducer.class.getSimpleName(), endpointKey));

            return timeoutThread;
        }
    }

    @Override
    public MllpEndpoint getEndpoint() {
        return (MllpEndpoint) super.getEndpoint();
    }

    public MllpConfiguration getConfiguration() {
        return ((MllpEndpoint)this.getEndpoint()).getConfiguration();
    }
}
