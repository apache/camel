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
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.mllp.impl.Hl7Util;
import org.apache.camel.component.mllp.impl.MllpBufferedSocketWriter;
import org.apache.camel.component.mllp.impl.MllpSocketReader;
import org.apache.camel.component.mllp.impl.MllpSocketUtil;
import org.apache.camel.component.mllp.impl.MllpSocketWriter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerationException;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerator;
import org.apache.camel.util.IOHelper;
import org.slf4j.MDC;

import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_AUTO_ACKNOWLEDGE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CHARSET;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CLOSE_CONNECTION_AFTER_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CLOSE_CONNECTION_BEFORE_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_EVENT_TYPE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_LOCAL_ADDRESS;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_MESSAGE_CONTROL;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_MESSAGE_TYPE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_PROCESSING_ID;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RECEIVING_APPLICATION;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RECEIVING_FACILITY;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_REMOTE_ADDRESS;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RESET_CONNECTION_AFTER_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RESET_CONNECTION_BEFORE_SEND;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_SECURITY;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_SENDING_APPLICATION;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_SENDING_FACILITY;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_TIMESTAMP;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_TRIGGER_EVENT;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_VERSION_ID;
import static org.apache.camel.component.mllp.MllpEndpoint.SEGMENT_DELIMITER;

/**
 * The MLLP consumer.
 */
@ManagedResource(description = "MllpTcpServer Consumer")
public class MllpTcpServerConsumer extends DefaultConsumer {
    public static final int SOCKET_STARTUP_TEST_WAIT = 100;
    public static final int SOCKET_STARTUP_TEST_READ_TIMEOUT = 250;
    ServerSocketThread serverSocketThread;

    List<ClientSocketThread> clientThreads = new LinkedList<>();

    Hl7AcknowledgementGenerator acknowledgementGenerator = new Hl7AcknowledgementGenerator();

    private final MllpEndpoint endpoint;

    public MllpTcpServerConsumer(MllpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        log.trace("MllpTcpServerConsumer(endpoint, processor)");
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        log.debug("doStart() - creating acceptor thread");

        startMllpConsumer();

        super.doStart();
    }

    @ManagedOperation(description = "Check server connection")
    public boolean managedCheckConnection() {
        boolean isValid = true;
        try {
            InetSocketAddress socketAddress;
            if (null == endpoint.getHostname()) {
                socketAddress = new InetSocketAddress(endpoint.getPort());
            } else {
                socketAddress = new InetSocketAddress(endpoint.getHostname(), endpoint.getPort());
            }
            Socket checkSocket = new Socket();
            checkSocket.connect(socketAddress, 100);
            checkSocket.close();
        } catch (Exception e) {
            isValid = false;
            log.debug("JMX check connection: {}", e);
        }
        return isValid;
    }

    @ManagedOperation(description = "Starts serverSocket thread and waits for requests")
    public void startMllpConsumer() throws IOException, InterruptedException {
        ServerSocket serverSocket = new ServerSocket();
        if (null != endpoint.receiveBufferSize) {
            serverSocket.setReceiveBufferSize(endpoint.receiveBufferSize);
        }

        serverSocket.setReuseAddress(endpoint.reuseAddress);

        // Accept Timeout
        serverSocket.setSoTimeout(endpoint.acceptTimeout);

        InetSocketAddress socketAddress;
        if (null == endpoint.getHostname()) {
            socketAddress = new InetSocketAddress(endpoint.getPort());
        } else {
            socketAddress = new InetSocketAddress(endpoint.getHostname(), endpoint.getPort());
        }
        long startTicks = System.currentTimeMillis();

        do {
            try {
                serverSocket.bind(socketAddress, endpoint.backlog);
            } catch (BindException bindException) {
                if (System.currentTimeMillis() > startTicks + endpoint.getBindTimeout()) {
                    log.error("Failed to bind to address {} within timeout {}", socketAddress, endpoint.getBindTimeout());
                    throw bindException;
                } else {
                    log.warn("Failed to bind to address {} - retrying in {} milliseconds", socketAddress, endpoint.getBindRetryInterval());
                    Thread.sleep(endpoint.getBindRetryInterval());
                }
            }
        } while (!serverSocket.isBound());

        serverSocketThread = new ServerSocketThread(serverSocket);
        serverSocketThread.start();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("doStop()");

        stopMllpConsumer();

        super.doStop();
    }

    @ManagedOperation(description = "Stops client threads and serverSocket thread")
    public void stopMllpConsumer() {
        // Close any client sockets that are currently open
        for (ClientSocketThread clientSocketThread: clientThreads) {
            clientSocketThread.interrupt();
        }


        switch (serverSocketThread.getState()) {
        case TERMINATED:
            // This is what we hope for
            break;
        case NEW:
        case RUNNABLE:
        case BLOCKED:
        case WAITING:
        case TIMED_WAITING:
        default:
            serverSocketThread.interrupt();
            break;
        }

        serverSocketThread = null;
    }

    /**
     * Nested Class to handle the ServerSocket.accept requests
     */
    class ServerSocketThread extends Thread {
        ServerSocket serverSocket;

        ServerSocketThread(ServerSocket serverSocket) {
            this.setName(createThreadName(serverSocket));

            this.serverSocket = serverSocket;
        }

        /**
         * Derive a thread name from the class name, the component URI and the connection information.
         * <p/>
         * The String will in the format <class name>[endpoint key] - [local socket address]
         *
         * @return String for thread name
         */
        String createThreadName(ServerSocket serverSocket) {
            // Get the classname without the package.  This is a nested class, so we want the parent class name included
            String fullClassName = this.getClass().getName();
            String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);

            // Get the URI without options
            String fullEndpointKey = endpoint.getEndpointKey();
            String endpointKey;
            if (fullEndpointKey.contains("?")) {
                endpointKey = fullEndpointKey.substring(0, fullEndpointKey.indexOf('?'));
            } else {
                endpointKey = fullEndpointKey;
            }

            // Now put it all together
            return String.format("%s[%s] - %s", className, endpointKey, serverSocket.getLocalSocketAddress());
        }

        /**
         * The main ServerSocket.accept() loop
         * <p/>
         * NOTE:  When a connection is received, the Socket is checked after a brief delay in an attempt to determine
         * if this is a load-balancer probe.  The test is done before the ClientSocketThread is created to avoid creating
         * a large number of short lived threads, which is what can occur if the load balancer polling interval is very
         * short.
         */
        public void run() {
            MDC.put("camel.contextId", endpoint.getCamelContext().getName());

            try {
                while (!isInterrupted()  &&  null != serverSocket && serverSocket.isBound()  &&  !serverSocket.isClosed()) {
                    Socket socket = null;
                    try {
                        socket = serverSocket.accept();
                    } catch (SocketTimeoutException timeoutEx) {
                        // Didn't get a new connection - keep waiting for one
                        log.debug("Timeout waiting for client connection - keep listening");
                        continue;
                    } catch (SocketException socketEx) {
                        // This should happen if the component is closed while the accept call is blocking
                        if (serverSocket.isBound()) {
                            try {
                                serverSocket.close();
                            } catch (Exception ex) {
                                log.debug("Exception encountered closing ServerSocket after SocketException on accept() - ignoring", ex);
                            }
                        }
                        continue;
                    } catch (IOException ioEx) {
                        log.error("Exception encountered accepting connection - closing ServerSocket", ioEx);
                        if (serverSocket.isBound()) {
                            try {
                                serverSocket.close();
                            } catch (Exception ex) {
                                log.debug("Exception encountered closing ServerSocket after exception on accept() - ignoring", ex);
                            }
                        }
                        continue;
                    }

                    try {
                    /* Wait a bit and then check and see if the socket is really there - it could be a load balancer
                     pinging the port
                      */
                        if (socket.isConnected() && !socket.isClosed()) {
                            log.debug("Socket appears to be there - checking for available data in {} milliseconds", SOCKET_STARTUP_TEST_WAIT);
                            Thread.sleep(SOCKET_STARTUP_TEST_WAIT);

                            InputStream inputStream;
                            try {
                                inputStream = socket.getInputStream();
                            } catch (IOException ioEx) {
                                MllpSocketUtil.reset(socket, log, "Failed to retrieve the InputStream for socket after the initial connection was accepted");
                                continue;
                            }

                            if (0 < inputStream.available()) {
                                // Something is there - start the client thread
                                ClientSocketThread clientThread = new ClientSocketThread(socket, null);
                                clientThreads.add(clientThread);
                                clientThread.start();
                                continue;
                            }

                            // The easy check failed - so trigger a blocking read
                            MllpSocketUtil.setSoTimeout(socket, SOCKET_STARTUP_TEST_READ_TIMEOUT, log, "Preparing to check for available data on component startup");
                            try {
                                int tmpByte = inputStream.read();
                                if (-1 == tmpByte) {
                                    log.debug("Check for available data failed - Socket.read() returned END_OF_STREAM");
                                    MllpSocketUtil.close(socket, null, null);
                                } else {
                                    ClientSocketThread clientThread = new ClientSocketThread(socket, tmpByte);
                                    clientThreads.add(clientThread);
                                    clientThread.start();
                                }
                            } catch (SocketTimeoutException timeoutEx) {
                                // No data, but the socket is there
                                String logMessageFormat =
                                    "Check for available data failed - Socket.read() timed-out after {} milliseconds."
                                        + "  No Data - but the socket is there.  Starting ClientSocketThread";
                                log.debug(logMessageFormat, SOCKET_STARTUP_TEST_READ_TIMEOUT);
                                ClientSocketThread clientThread = new ClientSocketThread(socket, null);
                                clientThreads.add(clientThread);
                                clientThread.start();
                            } catch (IOException ioEx) {
                                log.debug("Ignoring IOException encountered when attempting to read a byte - connection was reset");
                                try {
                                    socket.close();
                                } catch (IOException closeEx) {
                                    log.debug("Ignoring IOException encountered when attempting to close the connection after the connection reset was detected", closeEx);
                                }
                            }
                        }
                    } catch (SocketTimeoutException timeoutEx) {
                        // No new clients
                        log.trace("SocketTimeoutException waiting for new connections - no new connections");

                        for (int i = clientThreads.size() - 1; i >= 0; --i) {
                            ClientSocketThread thread = clientThreads.get(i);
                            if (!thread.isAlive()) {
                                clientThreads.remove(i);
                            }
                        }
                    } catch (InterruptedException interruptEx) {
                        log.debug("accept loop interrupted - closing ServerSocket");
                        try {
                            serverSocket.close();
                        } catch (Exception ex) {
                            log.debug("Exception encountered closing ServerSocket after InterruptedException - ignoring", ex);
                        }
                    } catch (Exception ex) {
                        log.error("Exception accepting new connection - retrying", ex);
                    }
                }
            } finally {
                log.debug("ServerSocket.accept loop finished - closing listener");
                if (null != serverSocket && serverSocket.isBound() && !serverSocket.isClosed()) {
                    try {
                        serverSocket.close();
                    } catch (Exception ex) {
                        log.debug("Exception encountered closing ServerSocket after accept loop had exited - ignoring", ex);
                    }
                }
            }
        }

        @Override
        public void interrupt() {
            super.interrupt();
            if (null != serverSocket) {
                if (serverSocket.isBound()) {
                    try {
                        serverSocket.close();
                    } catch (IOException ioEx) {
                        log.warn("Exception encountered closing ServerSocket in interrupt() method - ignoring", ioEx);
                    }
                }
            }
        }
    }

    /**
     * Nested Class reads the Socket
     */
    class ClientSocketThread extends Thread {
        final Socket clientSocket;
        final MllpSocketReader mllpSocketReader;
        final MllpSocketWriter mllpSocketWriter;

        Integer initialByte;

        ClientSocketThread(Socket clientSocket, Integer initialByte) throws IOException {
            this.initialByte = initialByte;
            this.setName(createThreadName(clientSocket));
            this.clientSocket = clientSocket;
            this.clientSocket.setKeepAlive(endpoint.keepAlive);
            this.clientSocket.setTcpNoDelay(endpoint.tcpNoDelay);
            if (null != endpoint.receiveBufferSize) {
                this.clientSocket.setReceiveBufferSize(endpoint.receiveBufferSize);
            }
            if (null != endpoint.sendBufferSize) {
                this.clientSocket.setSendBufferSize(endpoint.sendBufferSize);
            }
            this.clientSocket.setReuseAddress(endpoint.reuseAddress);
            this.clientSocket.setSoLinger(false, -1);

            // Initial Read Timeout
            MllpSocketUtil.setSoTimeout(clientSocket, endpoint.receiveTimeout, log, "Constructing ClientSocketThread");

            mllpSocketReader = new MllpSocketReader(this.clientSocket, endpoint.receiveTimeout, endpoint.readTimeout, false);
            if (endpoint.bufferWrites) {
                mllpSocketWriter = new MllpBufferedSocketWriter(this.clientSocket, true);
            } else {
                mllpSocketWriter = new MllpSocketWriter(this.clientSocket, true);
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
            // Get the classname without the package.  This is a nested class, so we want the parent class name included
            String fullClassName = this.getClass().getName();
            String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);

            // Get the URI without options
            String fullEndpointKey = endpoint.getEndpointKey();
            String endpointKey;
            if (fullEndpointKey.contains("?")) {
                endpointKey = fullEndpointKey.substring(0, fullEndpointKey.indexOf('?'));
            } else {
                endpointKey = fullEndpointKey;
            }

            // Now put it all together
            return String.format("%s[%s] - %s -> %s", className, endpointKey, socket.getLocalSocketAddress(), socket.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            int receiveTimeoutCounter = 0;
            MDC.put("camel.contextId", endpoint.getCamelContext().getName());

            while (!isInterrupted()  &&  null != clientSocket  &&  clientSocket.isConnected()  &&  !clientSocket.isClosed()) {
                byte[] hl7MessageBytes = null;

                log.debug("Checking for data ....");
                try {
                    hl7MessageBytes = mllpSocketReader.readEnvelopedPayload(initialByte);
                    if (hl7MessageBytes == null) {
                        // No data received - check for max timeouts
                        if (endpoint.maxReceiveTimeouts > 0 && ++receiveTimeoutCounter >= endpoint.maxReceiveTimeouts) {
                            String reasonMessage = String.format("Idle Client after %d receive timeouts [%d-milliseconds] - resetting connection", receiveTimeoutCounter, endpoint.receiveTimeout);
                            MllpSocketUtil.reset(clientSocket, log, reasonMessage);
                        }
                        continue;
                    }
                } catch (MllpException mllpEx) {
                    Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
                    exchange.setException(mllpEx);
                    log.warn("Exception encountered reading payload - sending exception to route", mllpEx);
                    try {
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        log.error("Exception encountered processing exchange with exception encounter reading payload", e);
                    }
                    continue;
                } finally {
                    initialByte = null;
                }

                // Send the message on for processing and wait for the response
                log.debug("Populating the exchange with received message");
                Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
                try {
                    createUoW(exchange);
                    Message message = exchange.getIn();
                    message.setBody(hl7MessageBytes, byte[].class);

                    message.setHeader(MLLP_LOCAL_ADDRESS, clientSocket.getLocalAddress().toString());
                    message.setHeader(MLLP_REMOTE_ADDRESS, clientSocket.getRemoteSocketAddress());
                    message.setHeader(MLLP_AUTO_ACKNOWLEDGE, endpoint.autoAck);

                    if (endpoint.validatePayload) {
                        String exceptionMessage = Hl7Util.generateInvalidPayloadExceptionMessage(hl7MessageBytes);
                        if (exceptionMessage != null) {
                            exchange.setException(new MllpInvalidMessageException(exceptionMessage, hl7MessageBytes));
                        }
                    }
                    populateHl7DataHeaders(exchange, message, hl7MessageBytes);

                    log.debug("Calling processor");
                    try {
                        getProcessor().process(exchange);
                        sendAcknowledgement(hl7MessageBytes, exchange);
                    } catch (RuntimeException runtimeEx) {
                        throw runtimeEx;
                    } catch (Exception ex) {
                        log.error("Unexpected exception processing exchange", ex);
                    }
                } catch (Exception uowEx) {
                    // TODO:  Handle this correctly
                    exchange.setException(uowEx);
                    log.warn("Exception encountered creating Unit of Work - sending exception to route", uowEx);
                    try {
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        log.error("Exception encountered processing exchange with exception encountered createing Unit of Work", e);
                    }
                    continue;
                } finally {
                    if (exchange != null) {
                        doneUoW(exchange);
                    }
                }


            }

            log.debug("ClientSocketThread exiting");
        }

        private void sendAcknowledgement(byte[] originalHl7MessageBytes, Exchange exchange) {
            log.info("sendAcknowledgement");

            // Check BEFORE_SEND Properties
            if (exchange.getProperty(MLLP_RESET_CONNECTION_BEFORE_SEND, boolean.class)) {
                String reasonMessage = String.format("Exchange property %s is %b", MLLP_RESET_CONNECTION_BEFORE_SEND,  exchange.getProperty(MLLP_RESET_CONNECTION_BEFORE_SEND, boolean.class));
                MllpSocketUtil.reset(clientSocket, log, reasonMessage);
                return;
            } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_BEFORE_SEND, boolean.class)) {
                String reasonMessage = String.format("Exchange property %s is %b", MLLP_CLOSE_CONNECTION_BEFORE_SEND,  exchange.getProperty(MLLP_CLOSE_CONNECTION_BEFORE_SEND, boolean.class));
                MllpSocketUtil.close(clientSocket, log, reasonMessage);
                return;
            }

            // Find the acknowledgement body
            // TODO:  Enhance this to say whether or not the acknowledgment is missing or just of an un-convertible type
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
            // TODO:  Use the charset of the exchange
            message.setHeader(MLLP_ACKNOWLEDGEMENT_STRING, new String(acknowledgementMessageBytes));
            message.setHeader(MLLP_ACKNOWLEDGEMENT_TYPE, acknowledgementMessageType);

            // Send the acknowledgement
            log.debug("Sending Acknowledgement: {}", MllpComponent.covertBytesToPrintFriendlyString(acknowledgementMessageBytes));
            try {
                mllpSocketWriter.writeEnvelopedPayload(originalHl7MessageBytes, acknowledgementMessageBytes);
            } catch (MllpException mllpEx) {
                log.error("MLLP Acknowledgement failure: {}", mllpEx);
                MllpAcknowledgementDeliveryException deliveryException = new MllpAcknowledgementDeliveryException(originalHl7MessageBytes, acknowledgementMessageBytes, mllpEx);
                exchange.setProperty(MLLP_ACKNOWLEDGEMENT_EXCEPTION, deliveryException);
                exchange.setException(deliveryException);
            }

            // Check AFTER_SEND Properties
            if (exchange.getProperty(MLLP_RESET_CONNECTION_AFTER_SEND, boolean.class)) {
                String reasonMessage = String.format("Exchange property %s is %b", MLLP_RESET_CONNECTION_AFTER_SEND,  exchange.getProperty(MLLP_RESET_CONNECTION_AFTER_SEND, boolean.class));
                MllpSocketUtil.reset(clientSocket, log, reasonMessage);
                return;
            } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_AFTER_SEND, boolean.class)) {
                String reasonMessage = String.format("Exchange property %s is %b", MLLP_CLOSE_CONNECTION_AFTER_SEND,  exchange.getProperty(MLLP_CLOSE_CONNECTION_AFTER_SEND, boolean.class));
                MllpSocketUtil.reset(clientSocket, log, reasonMessage);
            }
        }

        private void populateHl7DataHeaders(Exchange exchange, Message message, byte[] hl7MessageBytes) {
            if (hl7MessageBytes == null ||  hl7MessageBytes.length < 8) {
                // Not enough data to populate anything - just return
                return;
            }
            // Find the end of the MSH and indexes of the fields in the MSH to populate message headers
            final byte fieldSeparator = hl7MessageBytes[3];
            int endOfMSH = -1;
            List<Integer> fieldSeparatorIndexes = new ArrayList<>(10);  // We should have at least 10 fields

            for (int i = 0; i < hl7MessageBytes.length; ++i) {
                if (fieldSeparator == hl7MessageBytes[i]) {
                    fieldSeparatorIndexes.add(i);
                } else if (SEGMENT_DELIMITER == hl7MessageBytes[i]) {
                    // If the MSH Segment doesn't have a trailing field separator, add one so the field can be extracted into a header
                    if (fieldSeparator != hl7MessageBytes[i - 1]) {
                        fieldSeparatorIndexes.add(i);
                    }
                    endOfMSH = i;
                    break;
                }
            }

            String messageBodyForDebugging = new String(hl7MessageBytes);
            if (-1 == endOfMSH) {
                // TODO:  May want to throw some sort of an Exception here
                log.error("Population of message headers failed - unable to find the end of the MSH segment");
            } else if (endpoint.hl7Headers) {
                log.debug("Populating the HL7 message headers");
                Charset charset = Charset.forName(IOHelper.getCharsetName(exchange));

                for (int i = 2; i < fieldSeparatorIndexes.size(); ++i) {
                    int startingFieldSeparatorIndex = fieldSeparatorIndexes.get(i - 1);
                    int endingFieldSeparatorIndex = fieldSeparatorIndexes.get(i);

                    // Only populate the header if there's data in the HL7 field
                    if (endingFieldSeparatorIndex - startingFieldSeparatorIndex > 1) {
                        String headerName = null;
                        switch (i) {
                        case 2: // MSH-3
                            headerName = MLLP_SENDING_APPLICATION;
                            break;
                        case 3: // MSH-4
                            headerName = MLLP_SENDING_FACILITY;
                            break;
                        case 4: // MSH-5
                            headerName = MLLP_RECEIVING_APPLICATION;
                            break;
                        case 5: // MSH-6
                            headerName = MLLP_RECEIVING_FACILITY;
                            break;
                        case 6: // MSH-7
                            headerName = MLLP_TIMESTAMP;
                            break;
                        case 7: // MSH-8
                            headerName = MLLP_SECURITY;
                            break;
                        case 8: // MSH-9
                            headerName = MLLP_MESSAGE_TYPE;
                            break;
                        case 9: // MSH-10
                            headerName = MLLP_MESSAGE_CONTROL;
                            break;
                        case 10: // MSH-11
                            headerName = MLLP_PROCESSING_ID;
                            break;
                        case 11: // MSH-12
                            headerName = MLLP_VERSION_ID;
                            break;
                        case 17: // MSH-18
                            headerName = MLLP_CHARSET;
                            break;
                        default:
                            // Not processing this field
                            continue;
                        }

                        String headerValue = new String(hl7MessageBytes, startingFieldSeparatorIndex + 1,
                                endingFieldSeparatorIndex - startingFieldSeparatorIndex - 1,
                                charset);
                        message.setHeader(headerName, headerValue);

                        // For MSH-9, set a couple more headers
                        if (i == 8) {
                            // final byte componentSeparator = hl7MessageBytes[4];
                            String componentSeparator = new String(hl7MessageBytes, 4, 1, charset);
                            String[] components = headerValue.split(String.format("\\Q%s\\E", componentSeparator), 3);
                            message.setHeader(MLLP_EVENT_TYPE, components[0]);
                            if (2 <= components.length) {
                                message.setHeader(MLLP_TRIGGER_EVENT, components[1]);
                            }
                        }
                    }
                }
            } else {
                log.trace("HL7 Message headers disabled");
            }

        }

        @Override
        public void interrupt() {
            if (null != clientSocket  &&  clientSocket.isConnected()  && !clientSocket.isClosed()) {
                MllpSocketUtil.close(clientSocket, log, this.getClass().getSimpleName() + " interrupted");
            }
            super.interrupt();
        }
    }
}

