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
package org.apache.camel.component.mllp.internal;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import org.apache.camel.Route;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.component.mllp.MllpSocketException;
import org.apache.camel.component.mllp.MllpTcpServerConsumer;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Runnable to read the Socket
 */
public class TcpSocketConsumerRunnable implements Runnable {
    final Socket clientSocket;
    final MllpSocketBuffer mllpBuffer;

    Logger log = LoggerFactory.getLogger(this.getClass());
    MllpTcpServerConsumer consumer;
    boolean running;

    private final String localAddress;
    private final String remoteAddress;
    private final String combinedAddress;
    private final Hl7Util hl7Util;
    private final boolean logPhi;

    public TcpSocketConsumerRunnable(MllpTcpServerConsumer consumer, Socket clientSocket, MllpSocketBuffer mllpBuffer,
                                     Hl7Util hl7Util, boolean logPhi) {
        this.consumer = consumer;
        // this.setName(createThreadName(clientSocket));
        this.clientSocket = clientSocket;
        this.hl7Util = hl7Util;
        this.logPhi = logPhi;

        SocketAddress localSocketAddress = clientSocket.getLocalSocketAddress();
        if (localSocketAddress != null) {
            localAddress = localSocketAddress.toString();
        } else {
            localAddress = null;
        }

        SocketAddress remoteSocketAddress = clientSocket.getRemoteSocketAddress();
        if (remoteSocketAddress != null) {
            remoteAddress = remoteSocketAddress.toString();
        } else {
            remoteAddress = null;
        }

        combinedAddress = MllpSocketBuffer.formatAddressString(remoteSocketAddress, localSocketAddress);

        try {
            if (consumer.getConfiguration().hasKeepAlive()) {
                this.clientSocket.setKeepAlive(consumer.getConfiguration().getKeepAlive());
            }
            if (consumer.getConfiguration().hasTcpNoDelay()) {
                this.clientSocket.setTcpNoDelay(consumer.getConfiguration().getTcpNoDelay());
            }
            if (consumer.getConfiguration().hasReceiveBufferSize()) {
                this.clientSocket.setReceiveBufferSize(consumer.getConfiguration().getReceiveBufferSize());
            }
            if (consumer.getConfiguration().hasSendBufferSize()) {
                this.clientSocket.setSendBufferSize(consumer.getConfiguration().getSendBufferSize());
            }

            this.clientSocket.setSoLinger(false, -1);

            // Initial Read Timeout
            this.clientSocket.setSoTimeout(consumer.getConfiguration().getReceiveTimeout());
        } catch (IOException initializationException) {
            throw new IllegalStateException("Failed to initialize " + this.getClass().getSimpleName(), initializationException);
        }

        if (mllpBuffer == null) {
            this.mllpBuffer = new MllpSocketBuffer(consumer.getEndpoint());
        } else {
            this.mllpBuffer = mllpBuffer;
        }
    }

    /**
     * derive a thread name from the class name, the component URI and the connection information
     * <p/>
     * The String will in the format <class name>[endpoint key] - [local socket address] -> [remote socket address]
     *
     * @return the thread name
     */
    String createThreadName() {
        // Get the URI without options
        String fullEndpointKey = consumer.getEndpoint().getEndpointKey();
        String endpointKey = StringHelper.before(fullEndpointKey, "?", fullEndpointKey);

        // Now put it all together
        return String.format("%s[%s] - %s", this.getClass().getSimpleName(), endpointKey, combinedAddress);
    }

    @Override
    @SuppressWarnings("deprecation")
    // MDC deprecated since 4.19.0, use camel-mdc instead
    public void run() {
        running = true;
        String originalThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(createThreadName());
        MDC.put(UnitOfWork.MDC_CAMEL_CONTEXT_ID, consumer.getEndpoint().getCamelContext().getName());

        Route route = consumer.getRoute();
        if (route != null) {
            String routeId = route.getId();
            if (routeId != null) {
                MDC.put(UnitOfWork.MDC_ROUTE_ID, route.getId());
            }
        }

        log.debug("Starting {} for {}", this.getClass().getSimpleName(), combinedAddress);
        try {
            if (mllpBuffer.hasCompleteEnvelope()) {
                // Process all complete messages received during the validation read.
                processBufferedMessages();
            }

            while (running && null != clientSocket && clientSocket.isConnected() && !clientSocket.isClosed()) {
                log.debug("Checking for data ....");
                try {
                    mllpBuffer.readFrom(clientSocket);
                    if (mllpBuffer.hasCompleteEnvelope()) {
                        processBufferedMessages();
                    } else if (!mllpBuffer.hasStartOfBlock()) {
                        byte[] payload = mllpBuffer.toByteArray();
                        log.warn("Ignoring {} byte un-enveloped payload {}", payload.length,
                                hl7Util.convertToLoggableString(payload));
                        mllpBuffer.reset();
                    } else if (!mllpBuffer.isEmpty()) {
                        byte[] payload = mllpBuffer.toByteArray();
                        log.warn("Partial {} byte payload received {}", payload.length,
                                hl7Util.convertToLoggableString(payload));
                    }
                } catch (SocketTimeoutException timeoutEx) {
                    if (mllpBuffer.isEmpty()) {
                        if (consumer.getConfiguration().hasIdleTimeout()) {
                            long currentTicks = System.currentTimeMillis();
                            long lastReceivedMessageTicks = consumer.getConsumerRunnables().get(this);
                            long idleTime = currentTicks - lastReceivedMessageTicks;
                            if (idleTime >= consumer.getConfiguration().getIdleTimeout()) {
                                String resetMessage = String.format("Connection idle time %d exceeded idleTimeout %d", idleTime,
                                        consumer.getConfiguration().getIdleTimeout());
                                mllpBuffer.resetSocket(clientSocket, resetMessage);
                            }
                        }
                        log.debug("No data received - ignoring timeout");
                    } else {
                        mllpBuffer.resetSocket(clientSocket);
                        consumer.handleMessageTimeout("Timeout receiving complete message payload",
                                mllpBuffer.toByteArrayAndReset(), timeoutEx);
                    }
                } catch (MllpSocketException mllpSocketEx) {
                    mllpBuffer.resetSocket(clientSocket);
                    if (!mllpBuffer.isEmpty()) {
                        consumer.handleMessageException("Exception encountered reading payload",
                                mllpBuffer.toByteArrayAndReset(), mllpSocketEx);
                    } else {
                        log.debug("Ignoring exception encountered checking for data", mllpSocketEx);
                    }
                }
            }
        } catch (Exception unexpectedEx) {
            log.error("Unexpected exception encountered receiving messages", unexpectedEx);
        } finally {
            consumer.getConsumerRunnables().remove(this);
            log.debug("{} for {} completed", this.getClass().getSimpleName(), combinedAddress);

            Thread.currentThread().setName(originalThreadName);
            MDC.remove(UnitOfWork.MDC_ROUTE_ID);
            MDC.remove(UnitOfWork.MDC_CAMEL_CONTEXT_ID);

            mllpBuffer.resetSocket(clientSocket);
        }
    }

    /**
     * Process all complete MLLP envelopes currently in the buffer.
     * <p>
     * The buffer is reused to generate and send the acknowledgement. Preserve a trailing framed message before
     * processing the current one and restore it afterwards, so pipelined messages are not lost when the buffer is reset
     * for the acknowledgement.
     */
    private void processBufferedMessages() {
        do {
            byte[] hl7MessageBytes = mllpBuffer.toMllpPayload();
            if (log.isDebugEnabled()) {
                log.debug("Received {} byte message {}", hl7MessageBytes.length,
                        hl7Util.convertToLoggableString(hl7MessageBytes));
            }
            if (mllpBuffer.hasLeadingOutOfBandData()) {
                log.warn("Ignoring leading out-of-band data: {}",
                        hl7Util.convertToLoggableString(mllpBuffer.getLeadingOutOfBandData()));
            }

            byte[] trailingMessageData = extractTrailingMessageData();
            mllpBuffer.reset();
            consumer.processMessage(hl7MessageBytes, this);

            if (trailingMessageData != null) {
                mllpBuffer.reset();
                mllpBuffer.write(trailingMessageData);
            }
        } while (isSocketOpen() && mllpBuffer.hasCompleteEnvelope());

        if (!mllpBuffer.isEmpty() && !isSocketOpen()) {
            log.warn("Abandoning {} bytes of unprocessed pipelined data because the connection is closed",
                    mllpBuffer.size());
        }
    }

    /**
     * Return trailing data beginning with the next START_OF_BLOCK, if present. Data preceding it, and trailing data
     * with no START_OF_BLOCK, is out-of-band data.
     */
    private byte[] extractTrailingMessageData() {
        if (!mllpBuffer.hasTrailingOutOfBandData()) {
            return null;
        }

        byte[] trailingData = mllpBuffer.getTrailingOutOfBandData();
        int trailingDataOffset = mllpBuffer.size() - trailingData.length;
        int leadingLineBreakCount = 0;
        while (leadingLineBreakCount < trailingData.length && isLineBreak(trailingData[leadingLineBreakCount])) {
            leadingLineBreakCount++;
        }
        for (int i = 0; i < trailingData.length; i++) {
            if (trailingData[i] == MllpProtocolConstants.START_OF_BLOCK) {
                // A START_OF_BLOCK preceded only by line breaks (e.g. senders terminating frames with <FS><CR><LF>)
                // starts the next message, even if that message is not complete yet.
                if (i == leadingLineBreakCount || mllpBuffer.isCompleteEnvelopeAt(trailingDataOffset + i)) {
                    if (i > 0) {
                        byte[] outOfBandData = Arrays.copyOf(trailingData, i);
                        log.warn("Ignoring {} bytes of out-of-band data before next message: {}", i,
                                hl7Util.convertToLoggableString(outOfBandData));
                    }
                    return Arrays.copyOfRange(trailingData, i, trailingData.length);
                }

                // A START_OF_BLOCK embedded in junk must not be retained as a partial message: doing so would
                // turn a benign trailing-data warning into a receive-timeout error and reset the connection.
                continue;
            }
        }

        log.warn("Ignoring trailing out-of-band data: {}", hl7Util.convertToLoggableString(trailingData));
        return null;
    }

    private static boolean isLineBreak(byte b) {
        return b == '\r' || b == '\n';
    }

    private boolean isSocketOpen() {
        return running && clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }

    public Socket getSocket() {
        return clientSocket;
    }

    public MllpSocketBuffer getMllpBuffer() {
        return mllpBuffer;
    }

    public void closeSocket() {
        mllpBuffer.closeSocket(clientSocket);
    }

    public void closeSocket(String logMessage) {
        mllpBuffer.closeSocket(clientSocket, logMessage);
    }

    public void resetSocket() {
        mllpBuffer.resetSocket(clientSocket);
    }

    public void resetSocket(String logMessage) {
        mllpBuffer.resetSocket(clientSocket, logMessage);
    }

    public void stop() {
        running = false;
    }

    public boolean hasLocalAddress() {
        return localAddress != null && !localAddress.isEmpty();
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public boolean hasRemoteAddress() {
        return remoteAddress != null && !remoteAddress.isEmpty();
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public boolean hasCombinedAddress() {
        return combinedAddress != null && combinedAddress.isEmpty();
    }

    public String getCombinedAddress() {
        return combinedAddress;
    }
}
