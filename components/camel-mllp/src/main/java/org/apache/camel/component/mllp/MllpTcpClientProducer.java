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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mllp.internal.Hl7Util;
import org.apache.camel.component.mllp.internal.MllpSocketBuffer;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MLLP producer.
 */
@ManagedResource(description = "MLLP Producer")
public class MllpTcpClientProducer extends DefaultProducer implements Runnable {
    final Logger log;
    final MllpSocketBuffer mllpBuffer;

    Socket socket;

    ScheduledExecutorService idleTimeoutExecutor;

    private String cachedLocalAddress;
    private String cachedRemoteAddress;
    private String cachedCombinedAddress;
    private final Charset charset;
    private final Hl7Util hl7Util;
    private final boolean logPhi;

    public MllpTcpClientProducer(MllpEndpoint endpoint) {
        super(endpoint);

        log = LoggerFactory
                .getLogger(String.format("%s.%s.%d", this.getClass().getName(), endpoint.getHostname(), endpoint.getPort()));

        log.trace("Constructing MllpTcpClientProducer for endpoint URI {}", endpoint.getEndpointUri());

        mllpBuffer = new MllpSocketBuffer(endpoint);
        charset = Charset.forName(endpoint.getConfiguration().getCharsetName());
        MllpComponent component = endpoint.getComponent();
        this.logPhi = component.getLogPhi();
        hl7Util = new Hl7Util(component.getLogPhiMaxBytes(), logPhi);
    }

    @ManagedAttribute(description = "Last activity time")
    public Date getLastActivityTime() {
        return getEndpoint().getLastConnectionActivityTime();
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
            String endpointKey = StringHelper.before(fullEndpointKey, "?", fullEndpointKey);

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
    public synchronized void process(Exchange exchange) throws MllpException {
        log.trace("process({}) [{}] - entering", exchange.getExchangeId(), socket);
        getEndpoint().updateLastConnectionActivityTicks();

        Message message = exchange.getMessage();

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
                String exceptionMessage
                        = String.format("process(%s) [%s] - message body is null", exchange.getExchangeId(), socket);
                exchange.setException(new MllpInvalidMessageException(exceptionMessage, hl7MessageBytes, logPhi));
                return;
            } else if (messageBody instanceof byte[]) {
                hl7MessageBytes = (byte[]) messageBody;
            } else if (messageBody instanceof String) {
                String stringBody = (String) messageBody;
                hl7MessageBytes = stringBody.getBytes(MllpCharsetHelper.getCharset(exchange, charset));
                if (getConfiguration().hasCharsetName()) {
                    exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, getConfiguration().getCharsetName());
                }
            }

            log.debug("process({}) [{}] - sending message to external system", exchange.getExchangeId(), socket);

            try {
                mllpBuffer.setEnvelopedMessage(hl7MessageBytes);
                mllpBuffer.writeTo(socket);
            } catch (MllpSocketException writeEx) {
                // Connection may have been reset - try one more time
                log.debug("process({}) [{}] - exception encountered writing payload - attempting reconnect",
                        exchange.getExchangeId(), socket, writeEx);
                try {
                    checkConnection();
                    log.trace("process({}) [{}] - reconnected succeeded - resending payload", exchange.getExchangeId(), socket);
                    try {
                        mllpBuffer.writeTo(socket);
                    } catch (MllpSocketException retryWriteEx) {
                        String exceptionMessage = String.format(
                                "process(%s) [%s] - exception encountered attempting to write payload after reconnect",
                                exchange.getExchangeId(), socket);
                        log.warn(exceptionMessage, retryWriteEx);
                        exchange.setException(
                                new MllpWriteException(
                                        exceptionMessage, mllpBuffer.toByteArrayAndReset(), retryWriteEx, logPhi));
                    }
                } catch (IOException reconnectEx) {
                    String exceptionMessage = String.format("process(%s) [%s] - exception encountered attempting to reconnect",
                            exchange.getExchangeId(), socket);
                    log.warn(exceptionMessage, reconnectEx);
                    exchange.setException(
                            new MllpWriteException(exceptionMessage, mllpBuffer.toByteArrayAndReset(), writeEx, logPhi));
                    mllpBuffer.resetSocket(socket);
                }
            }
            if (getConfiguration().getExchangePattern() == ExchangePattern.InOnly) {
                log.debug("process({}) [{}] - not checking acknowledgement from external system",
                        exchange.getExchangeId(), socket);
                return;
            }
            if (exchange.getException() == null) {
                log.debug("process({}) [{}] - reading acknowledgement from external system", exchange.getExchangeId(), socket);
                try {
                    mllpBuffer.reset();
                    mllpBuffer.readFrom(socket);
                } catch (MllpSocketException receiveAckEx) {
                    // Connection may have been reset - try one more time
                    log.debug("process({}) [{}] - exception encountered reading acknowledgement - attempting reconnect",
                            exchange.getExchangeId(), socket, receiveAckEx);
                    try {
                        checkConnection();
                    } catch (IOException reconnectEx) {
                        String exceptionMessage = String.format(
                                "process(%s) [%s] - exception encountered attempting to reconnect after acknowledgement read failure",
                                exchange.getExchangeId(), socket);
                        log.warn(exceptionMessage, reconnectEx);
                        exchange.setException(
                                new MllpAcknowledgementReceiveException(
                                        exceptionMessage, hl7MessageBytes, receiveAckEx, logPhi));
                        mllpBuffer.resetSocket(socket);
                    }

                    if (exchange.getException() == null) {
                        log.trace("process({}) [{}] - resending payload after successful reconnect", exchange.getExchangeId(),
                                socket);
                        try {
                            mllpBuffer.setEnvelopedMessage(hl7MessageBytes);
                            mllpBuffer.writeTo(socket);
                        } catch (MllpSocketException writeRetryEx) {
                            String exceptionMessage = String.format(
                                    "process(%s) [%s] - exception encountered attempting to write payload after read failure and successful reconnect",
                                    exchange.getExchangeId(), socket);
                            log.warn(exceptionMessage, writeRetryEx);
                            exchange.setException(
                                    new MllpWriteException(exceptionMessage, hl7MessageBytes, receiveAckEx, logPhi));
                        }

                        if (exchange.getException() == null) {
                            log.trace("process({}) [{}] - resend succeeded - reading acknowledgement from external system",
                                    exchange.getExchangeId(), socket);
                            try {
                                mllpBuffer.reset();
                                mllpBuffer.readFrom(socket);
                            } catch (MllpSocketException secondReceiveEx) {
                                String exceptionMessageFormat = mllpBuffer.isEmpty()
                                        ? "process(%s) [%s] - exception encountered reading MLLP Acknowledgement after successful reconnect and resend"
                                        : "process(%s) [%s] - exception encountered reading complete MLLP Acknowledgement after successful reconnect and resend";
                                String exceptionMessage
                                        = String.format(exceptionMessageFormat, exchange.getExchangeId(), socket);
                                log.warn(exceptionMessage, secondReceiveEx);
                                // Send the original exception to the exchange
                                exchange.setException(new MllpAcknowledgementReceiveException(
                                        exceptionMessage, hl7MessageBytes, mllpBuffer.toByteArrayAndReset(), receiveAckEx,
                                        logPhi));
                            } catch (SocketTimeoutException secondReadTimeoutEx) {
                                String exceptionMessageFormat = mllpBuffer.isEmpty()
                                        ? "process(%s) [%s] - timeout receiving MLLP Acknowledgment after successful reconnect and resend"
                                        : "process(%s) [%s] - timeout receiving complete MLLP Acknowledgment after successful reconnect and resend";
                                String exceptionMessage
                                        = String.format(exceptionMessageFormat, exchange.getExchangeId(), socket);
                                log.warn(exceptionMessage, secondReadTimeoutEx);
                                // Send the original exception to the exchange
                                exchange.setException(new MllpAcknowledgementTimeoutException(
                                        exceptionMessage, hl7MessageBytes, mllpBuffer.toByteArrayAndReset(), receiveAckEx,
                                        logPhi));
                                mllpBuffer.resetSocket(socket);
                            }
                        }
                    }
                } catch (SocketTimeoutException timeoutEx) {
                    String exceptionMessageFormat = mllpBuffer.isEmpty()
                            ? "process(%s) [%s] - timeout receiving MLLP Acknowledgment"
                            : "process(%s) [%s] - timeout receiving complete MLLP Acknowledgment";
                    String exceptionMessage = String.format(exceptionMessageFormat, exchange.getExchangeId(), socket);
                    log.warn(exceptionMessage, timeoutEx);
                    exchange.setException(new MllpAcknowledgementTimeoutException(
                            exceptionMessage, hl7MessageBytes, mllpBuffer.toByteArrayAndReset(), timeoutEx, logPhi));
                    mllpBuffer.resetSocket(socket);
                }

                if (exchange.getException() == null) {
                    if (mllpBuffer.hasCompleteEnvelope()) {
                        byte[] acknowledgementBytes = mllpBuffer.toMllpPayload();

                        log.debug(
                                "process({}) [{}] - populating message headers with the acknowledgement from the external system",
                                exchange.getExchangeId(), socket);
                        message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT, acknowledgementBytes);
                        if (acknowledgementBytes != null && acknowledgementBytes.length > 0) {
                            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, new String(
                                    acknowledgementBytes,
                                    MllpCharsetHelper.getCharset(exchange, acknowledgementBytes, hl7Util, charset)));
                        } else {
                            message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, "");
                        }

                        if (getConfiguration().isValidatePayload()) {
                            String exceptionMessage = hl7Util.generateInvalidPayloadExceptionMessage(acknowledgementBytes);
                            if (exceptionMessage != null) {
                                exchange.setException(new MllpInvalidAcknowledgementException(
                                        exceptionMessage, hl7MessageBytes, acknowledgementBytes, logPhi));
                            }
                        }

                        if (exchange.getException() == null) {
                            log.debug("process({}) [{}] - processing the acknowledgement from the external system",
                                    exchange.getExchangeId(), socket);
                            try {
                                message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE,
                                        processAcknowledgment(hl7MessageBytes, acknowledgementBytes));
                            } catch (MllpNegativeAcknowledgementException nackEx) {
                                message.setHeader(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, nackEx.getAcknowledgmentType());
                                exchange.setException(nackEx);
                            }

                            getEndpoint().checkAfterSendProperties(exchange, socket, log);
                        }
                    } else {
                        String exceptionMessage = String.format("process(%s) [%s] - invalid acknowledgement received",
                                exchange.getExchangeId(), socket);
                        exchange.setException(new MllpInvalidAcknowledgementException(
                                exceptionMessage, hl7MessageBytes, mllpBuffer.toByteArrayAndReset(), logPhi));
                    }
                }
            }

        } catch (IOException ioEx) {
            log.debug("process({}) [{}] - IOException encountered checking connection", exchange.getExchangeId(), socket, ioEx);
            exchange.setException(ioEx);
            mllpBuffer.resetSocket(socket);
        } finally {
            mllpBuffer.reset();
        }

        log.trace("process({}) [{}] - exiting", exchange.getExchangeId(), socket);
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
                        if (bM == hl7AcknowledgementBytes[i + 1] && bS == hl7AcknowledgementBytes[i + 2]
                                && bA == hl7AcknowledgementBytes[i + 3] && fieldDelim == hl7AcknowledgementBytes[i + 4]) {
                            // Found the beginning of the MSA - the next two bytes should be our acknowledgement code
                            msaStartIndex = i + 1;
                            if (bA != hl7AcknowledgementBytes[i + 5] && bC != hl7AcknowledgementBytes[i + 5]) {
                                String errorMessage = String.format(
                                        "processAcknowledgment(hl7MessageBytes[%d], hl7AcknowledgementBytes[%d]) - unsupported acknowledgement type: '%s'",
                                        hl7MessageBytes == null ? -1 : hl7MessageBytes.length, hl7AcknowledgementBytes.length,
                                        new String(hl7AcknowledgementBytes, i + 5, 2));
                                throw new MllpInvalidAcknowledgementException(
                                        errorMessage, hl7MessageBytes, hl7AcknowledgementBytes, logPhi);
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
                                            throw new MllpApplicationErrorAcknowledgementException(
                                                    hl7MessageBytes, hl7AcknowledgementBytes, logPhi);
                                        } else {
                                            throw new MllpCommitErrorAcknowledgementException(
                                                    hl7MessageBytes, hl7AcknowledgementBytes, logPhi);
                                        }
                                    case bR:
                                        // We have an AR or CR
                                        if (bA == hl7AcknowledgementBytes[i + 5]) {
                                            throw new MllpApplicationRejectAcknowledgementException(
                                                    hl7MessageBytes, hl7AcknowledgementBytes, logPhi);
                                        } else {
                                            throw new MllpCommitRejectAcknowledgementException(
                                                    hl7MessageBytes, hl7AcknowledgementBytes, logPhi);
                                        }
                                    default:
                                        String errorMessage = "Unsupported acknowledgement type: "
                                                              + new String(hl7AcknowledgementBytes, i + 5, 2);
                                        throw new MllpInvalidAcknowledgementException(
                                                errorMessage, hl7MessageBytes, hl7AcknowledgementBytes, logPhi);
                                }
                            }

                            break;
                        }
                    }
                }

            }
            if (-1 == msaStartIndex && getConfiguration().isValidatePayload()) {
                // Didn't find an MSA
                throw new MllpInvalidAcknowledgementException(
                        "MSA Not found in acknowledgement", hl7MessageBytes, hl7AcknowledgementBytes, logPhi);
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
            logCurrentSocketState();

            // The socket will be closed by close connection, resetConnection, etc
            Socket newSocket = createNewSocket();

            InetSocketAddress socketAddress = configureSocketAddress();

            newSocket.connect(socketAddress, getConfiguration().getConnectTimeout());
            log.info("checkConnection() - established new connection {}", newSocket);
            getEndpoint().updateLastConnectionEstablishedTicks();

            socket = newSocket;

            cacheAddresses();

            if (getConfiguration().hasIdleTimeout()) {
                log.debug("Scheduling initial idle producer connection check of {} in {} milliseconds", getConnectionAddress(),
                        getConfiguration().getIdleTimeout());
                idleTimeoutExecutor.schedule(this, getConfiguration().getIdleTimeout(), TimeUnit.MILLISECONDS);
            }
        } else {
            log.debug("checkConnection() - Connection {} is still valid - no new connection required", socket);
        }
    }

    private void logCurrentSocketState() {
        if (socket == null) {
            log.debug("checkConnection() - Socket is null - attempting to establish connection");
        } else if (socket.isClosed()) {
            log.info("checkConnection() - Socket {} is closed - attempting to establish new connection", socket);
        } else if (!socket.isConnected()) {
            log.info("checkConnection() - Socket {} is not connected - attempting to establish new connection", socket);
        }
    }

    private void cacheAddresses() {
        SocketAddress localSocketAddress = socket.getLocalSocketAddress();
        if (localSocketAddress != null) {
            cachedLocalAddress = localSocketAddress.toString();
        }
        SocketAddress remoteSocketAddress = socket.getRemoteSocketAddress();
        if (remoteSocketAddress != null) {
            cachedRemoteAddress = remoteSocketAddress.toString();
        }
        cachedCombinedAddress = MllpSocketBuffer.formatAddressString(localSocketAddress, remoteSocketAddress);
    }

    private InetSocketAddress configureSocketAddress() {
        InetSocketAddress socketAddress;
        if (null == getEndpoint().getHostname()) {
            socketAddress = new InetSocketAddress(getEndpoint().getPort());
        } else {
            socketAddress = new InetSocketAddress(getEndpoint().getHostname(), getEndpoint().getPort());
        }
        return socketAddress;
    }

    private Socket createNewSocket() throws SocketException {
        Socket newSocket = new Socket();

        if (getConfiguration().hasKeepAlive()) {
            newSocket.setKeepAlive(getConfiguration().getKeepAlive());
        }
        if (getConfiguration().hasTcpNoDelay()) {
            newSocket.setTcpNoDelay(getConfiguration().getTcpNoDelay());
        }

        if (getConfiguration().hasReceiveBufferSize()) {
            newSocket.setReceiveBufferSize(getConfiguration().getReceiveBufferSize());
        }
        if (getConfiguration().hasSendBufferSize()) {
            newSocket.setSendBufferSize(getConfiguration().getSendBufferSize());
        }
        if (getConfiguration().hasReuseAddress()) {
            newSocket.setReuseAddress(getConfiguration().getReuseAddress());
        }

        newSocket.setSoLinger(false, -1);
        return newSocket;
    }

    /**
     * Check for idle connection
     */
    @Override
    public synchronized void run() {
        if (getConfiguration().hasIdleTimeout()) {
            if (null != socket && !socket.isClosed() && socket.isConnected()) {
                if (getEndpoint().hasLastConnectionActivityTicks()) {
                    long idleTime = System.currentTimeMillis() - getEndpoint().getLastConnectionActivityTicks();
                    if (log.isDebugEnabled()) {
                        log.debug("Checking {} for idle connection: {} - {}", getConnectionAddress(), idleTime,
                                getConfiguration().getIdleTimeout());
                    }
                    if (idleTime >= getConfiguration().getIdleTimeout()) {
                        if (MllpIdleTimeoutStrategy.CLOSE == getConfiguration().getIdleTimeoutStrategy()) {
                            log.info(
                                    "MLLP Connection idle time of '{}' milliseconds met or exceeded the idle producer timeout of '{}' milliseconds - closing connection",
                                    idleTime, getConfiguration().getIdleTimeout());
                            mllpBuffer.closeSocket(socket);
                        } else {
                            log.info(
                                    "MLLP Connection idle time of '{}' milliseconds met or exceeded the idle producer timeout of '{}' milliseconds - resetting connection",
                                    idleTime, getConfiguration().getIdleTimeout());
                            mllpBuffer.resetSocket(socket);
                        }
                    } else {
                        long minDelay = 100;
                        long delay = Long.min(Long.max(minDelay, getConfiguration().getIdleTimeout() - idleTime),
                                getConfiguration().getIdleTimeout());
                        if (log.isDebugEnabled()) {
                            log.debug("Scheduling idle producer connection check of {} in {} milliseconds",
                                    getConnectionAddress(), delay);
                        }
                        idleTimeoutExecutor.schedule(this, delay, TimeUnit.MILLISECONDS);
                    }
                } else {
                    log.debug(
                            "No activity detected since initial connection - scheduling idle producer connection check in {} milliseconds",
                            getConfiguration().getIdleTimeout());
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

        @Override
        public Thread newThread(Runnable r) {
            Thread timeoutThread = Executors.defaultThreadFactory().newThread(r);

            timeoutThread.setName(
                    String.format("%s[%s]-idle-timeout-thread", MllpTcpClientProducer.class.getSimpleName(), endpointKey));

            return timeoutThread;
        }
    }

    @Override
    public MllpEndpoint getEndpoint() {
        return (MllpEndpoint) super.getEndpoint();
    }

    public MllpConfiguration getConfiguration() {
        return this.getEndpoint().getConfiguration();
    }
}
