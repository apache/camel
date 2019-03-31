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

import org.apache.camel.Route;
import org.apache.camel.component.mllp.MllpSocketException;
import org.apache.camel.component.mllp.MllpTcpServerConsumer;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Runnable to handle the ServerSocket.accept requests
 */
public class TcpServerConsumerValidationRunnable implements Runnable {
    final Socket clientSocket;
    final MllpSocketBuffer mllpBuffer;

    Logger log = LoggerFactory.getLogger(this.getClass());
    MllpTcpServerConsumer consumer;

    private final String localAddress;
    private final String remoteAddress;
    private final String combinedAddress;

    public TcpServerConsumerValidationRunnable(MllpTcpServerConsumer consumer, Socket clientSocket, MllpSocketBuffer mllpBuffer) {
        this.consumer = consumer;
        // this.setName(createThreadName(clientSocket));
        this.clientSocket = clientSocket;

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
    String createThreadName(Socket socket) {
        // Get the URI without options
        String fullEndpointKey = consumer.getEndpoint().getEndpointKey();
        String endpointKey;
        if (fullEndpointKey.contains("?")) {
            endpointKey = fullEndpointKey.substring(0, fullEndpointKey.indexOf('?'));
        } else {
            endpointKey = fullEndpointKey;
        }

        // Now put it all together
        return String.format("%s[%s] - %s", this.getClass().getSimpleName(), endpointKey, combinedAddress);
    }

    /**
     * Do the initial read on the Socket and try to determine if it has HL7 data, junk, or nothing.
     */
    @Override
    public void run() {
        String originalThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(createThreadName(clientSocket));
        MDC.put(UnitOfWork.MDC_CAMEL_CONTEXT_ID, consumer.getEndpoint().getCamelContext().getName());

        Route route = consumer.getRoute();
        if (route != null) {
            String routeId = route.getId();
            if (routeId != null) {
                MDC.put(UnitOfWork.MDC_ROUTE_ID, route.getId());
            }
        }

        log.debug("Checking {} for data", combinedAddress);

        try {
            mllpBuffer.readFrom(clientSocket, Math.min(500, consumer.getConfiguration().getReceiveTimeout()), consumer.getConfiguration().getReadTimeout());
            if (mllpBuffer.hasCompleteEnvelope()  || mllpBuffer.hasStartOfBlock()) {
                consumer.startConsumer(clientSocket, mllpBuffer);
            } else if (!mllpBuffer.isEmpty()) {
                // We have some leading out-of-band data but no START_OF_BLOCK
                log.info("Ignoring out-of-band data on initial read [{} bytes]: {}", mllpBuffer.size(), mllpBuffer.toPrintFriendlyStringAndReset());
                mllpBuffer.resetSocket(clientSocket);
            }
        } catch (MllpSocketException socketEx) {
            // TODO:  The socket is invalid for some reason
            if (!mllpBuffer.isEmpty()) {
                log.warn("Exception encountered receiving complete initial message [{} bytes]: {}", mllpBuffer.size(), mllpBuffer.toPrintFriendlyStringAndReset());
            }
            mllpBuffer.resetSocket(clientSocket);
        } catch (SocketTimeoutException timeoutEx) {
            if (mllpBuffer.isEmpty()) {
                log.debug("Initial read timed-out but no data was read - starting consumer");
                consumer.startConsumer(clientSocket, mllpBuffer);
            } else {
                log.warn("Timeout receiving complete initial message on read [{} bytes]: {}", mllpBuffer.size(), mllpBuffer.toPrintFriendlyStringAndReset());
                mllpBuffer.resetSocket(clientSocket);
            }
        } finally {
            Thread.currentThread().setName(originalThreadName);
        }
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

    public String getLocalAddress() {
        return localAddress;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getCombinedAddress() {
        return combinedAddress;
    }

}
