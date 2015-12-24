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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.mllp.impl.MllpUtil;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerationException;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerator;
import org.apache.camel.util.IOHelper;

import static org.apache.camel.component.mllp.MllpConstants.*;
import static org.apache.camel.component.mllp.MllpEndpoint.SEGMENT_DELIMITER;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

/**
 * The MLLP consumer.
 */
public class MllpTcpServerConsumer extends DefaultConsumer {
    ServerSocketThread serverSocketThread;

    List<ClientSocketThread> clientThreads = new LinkedList<>();

    private final MllpEndpoint endpoint;

    public MllpTcpServerConsumer(MllpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        log.trace("MllpTcpServerConsumer(endpoint, processor)");


        this.endpoint = endpoint;

    }

    @Override
    protected void doStart() throws Exception {
        log.debug("doStart() - creating acceptor thread");

        ServerSocket serverSocket = new ServerSocket();
        if (null != endpoint.receiveBufferSize) {
            serverSocket.setReceiveBufferSize(endpoint.receiveBufferSize);
        }

        serverSocket.setReuseAddress(endpoint.reuseAddress);

        // Accept Timeout
        serverSocket.setSoTimeout(endpoint.acceptTimeout);

        InetSocketAddress socketAddress = new InetSocketAddress(endpoint.getHostname(), endpoint.getPort());
        serverSocket.bind(socketAddress, endpoint.backlog);

        serverSocketThread = new ServerSocketThread(serverSocket);
        serverSocketThread.start();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("doStop()");

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

        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        log.debug("doSuspend()");

        super.doSuspend();
    }

    @Override
    protected void doResume() throws Exception {
        log.debug("doResume()");

        super.doSuspend();
    }

    @Override
    protected void doShutdown() throws Exception {
        log.debug("doShutdown()");

        super.doShutdown();
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
            log.debug("Starting acceptor thread");

            while (null != serverSocket && serverSocket.isBound() && !serverSocket.isClosed()) {
                try {
                    /* ? set this here ? */
                    // serverSocket.setSoTimeout( 10000 );
                    // TODO: Need to check maxConnections and figure out what to do when exceeded
                    Socket socket = serverSocket.accept();

                    /* Wait a bit and then check and see if the socket is really there - it could be a load balancer
                     pinging the port
                      */
                    Thread.sleep(100);
                    if (socket.isConnected() && !socket.isClosed()) {
                        log.debug("Socket appears to be there - check for available data");
                        InputStream inputStream;
                        try {
                            inputStream = socket.getInputStream();
                        } catch (IOException ioEx) {
                            // Bad Socket -
                            log.warn("Failed to retrieve the InputStream for socket after the initial connection was accepted");
                            MllpUtil.resetConnection(socket);
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
                        socket.setSoTimeout(100);
                        try {
                            int tmpByte = inputStream.read();
                            socket.setSoTimeout(endpoint.responseTimeout);
                            if (-1 == tmpByte) {
                                log.debug("Socket.read() returned END_OF_STREAM - resetting connection");
                                MllpUtil.resetConnection(socket);
                            } else {
                                ClientSocketThread clientThread = new ClientSocketThread(socket, tmpByte);
                                clientThreads.add(clientThread);
                                clientThread.start();
                            }
                        } catch (SocketTimeoutException timeoutEx) {
                            // No data, but the socket is there
                            log.debug("No Data - but the socket is there.  Starting ClientSocketThread");
                            ClientSocketThread clientThread = new ClientSocketThread(socket, null);
                            clientThreads.add(clientThread);
                            clientThread.start();
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
                    log.info("accept loop interrupted - closing ServerSocket");
                    try {
                        serverSocket.close();
                    } catch (Exception ex) {
                        log.warn("Exception encountered closing ServerSocket after InterruptedException", ex);
                    }
                } catch (Exception ex) {
                    log.error("Exception accepting new connection", ex);
                }
            }
        }

    }

    class ClientSocketThread extends Thread {
        Socket clientSocket;
        Hl7AcknowledgementGenerator acknowledgementGenerator = new Hl7AcknowledgementGenerator();

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

            // Read Timeout
            this.clientSocket.setSoTimeout(endpoint.responseTimeout);

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

            while (null != clientSocket && clientSocket.isConnected() && !clientSocket.isClosed()) {
                byte[] hl7MessageBytes = null;
                // Send the message on for processing and wait for the response
                log.debug("Reading data ....");
                try {
                    if (null != initialByte && START_OF_BLOCK == initialByte) {
                        hl7MessageBytes = MllpUtil.closeFrame(clientSocket);
                    } else {
                        try {
                            MllpUtil.openFrame(clientSocket);
                        } catch (SocketTimeoutException timeoutEx) {
                            // When thrown by openFrame, it indicates that no data was available - but no error
                            continue;
                        }
                        hl7MessageBytes = MllpUtil.closeFrame(clientSocket);
                    }
                } catch (MllpException mllpEx) {
                    Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
                    exchange.setException(mllpEx);
                    return;
                } finally {
                    initialByte = null;
                }

                if (null == hl7MessageBytes) {
                    continue;
                }

                log.debug("Populating the exchange with received message");
                Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
                Message message = exchange.getIn();
                message.setBody(hl7MessageBytes, byte[].class);

                message.setHeader(MLLP_LOCAL_ADDRESS, clientSocket.getLocalAddress().toString());
                message.setHeader(MLLP_REMOTE_ADDRESS, clientSocket.getRemoteSocketAddress());

                populateHl7DataHeaders(exchange, message, hl7MessageBytes);


                log.debug("Calling processor");
                try {
                    getProcessor().process(exchange);
                    // processed the message - send the acknowledgement

                    // Check BEFORE_SEND Properties
                    if (exchange.getProperty(MLLP_RESET_CONNECTION_BEFORE_SEND, boolean.class)) {
                        MllpUtil.resetConnection(clientSocket);
                        return;
                    } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_BEFORE_SEND, boolean.class)) {
                        MllpUtil.closeConnection(clientSocket);
                    }

                    // Find the acknowledgement body
                    byte[] acknowledgementMessageBytes = exchange.getProperty(MLLP_ACKNOWLEDGEMENT, byte[].class);
                    String acknowledgementMessageType = null;
                    if (null == acknowledgementMessageBytes) {
                        if (!endpoint.autoAck) {
                            exchange.setException(new MllpInvalidAcknowledgementException("Automatic Acknowledgement is disabled and the "
                                    + MLLP_ACKNOWLEDGEMENT + " exchange property is null or cannot be converted to byte[]"));
                            return;
                        }

                        String acknowledgmentTypeProperty = exchange.getProperty(MLLP_ACKNOWLEDGEMENT_TYPE, String.class);
                        try {
                            if (null == acknowledgmentTypeProperty) {
                                if (null == exchange.getException()) {
                                    acknowledgementMessageType = "AA";
                                    acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationAcceptAcknowledgementMessage(hl7MessageBytes);
                                } else {
                                    acknowledgementMessageType = "AE";
                                    acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationErrorAcknowledgementMessage(hl7MessageBytes);
                                }
                            } else {
                                switch (acknowledgmentTypeProperty) {
                                case "AA":
                                    acknowledgementMessageType = "AA";
                                    acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationAcceptAcknowledgementMessage(hl7MessageBytes);
                                    break;
                                case "AE":
                                    acknowledgementMessageType = "AE";
                                    acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationErrorAcknowledgementMessage(hl7MessageBytes);
                                    break;
                                case "AR":
                                    acknowledgementMessageType = "AR";
                                    acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationRejectAcknowledgementMessage(hl7MessageBytes);
                                    break;
                                default:
                                    exchange.setException(new Hl7AcknowledgementGenerationException("Unsupported acknowledgment type: " + acknowledgmentTypeProperty));
                                    return;
                                }
                            }
                        } catch (Hl7AcknowledgementGenerationException ackGenerationException) {
                            exchange.setException(ackGenerationException);
                        }
                    } else {
                        final byte bM = 77;
                        final byte bS = 83;
                        final byte bA = 65;
                        final byte bE = 69;
                        final byte bR = 82;

                        final byte fieldSeparator = hl7MessageBytes[3];
                        // Acknowledgment is specified in exchange property - determine the acknowledgement type
                        for (int i = 0; i < hl7MessageBytes.length; ++i) {
                            if (SEGMENT_DELIMITER == i) {
                                if (i + 7 < hl7MessageBytes.length // Make sure we don't run off the end of the message
                                        && bM == hl7MessageBytes[i + 1] && bS == hl7MessageBytes[i + 2] && bA == hl7MessageBytes[i + 3] && fieldSeparator == hl7MessageBytes[i + 4]) {
                                    if (fieldSeparator != hl7MessageBytes[i + 7]) {
                                        log.warn("MSA-1 is longer than 2-bytes - ignoring trailing bytes");
                                    }
                                    // Found MSA - pull acknowledgement bytes
                                    byte[] acknowledgmentTypeBytes = new byte[2];
                                    acknowledgmentTypeBytes[0] = hl7MessageBytes[i + 5];
                                    acknowledgmentTypeBytes[1] = hl7MessageBytes[i + 6];
                                    acknowledgementMessageType = IOConverter.toString(acknowledgmentTypeBytes, exchange);

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

                    // Send the acknowledgement
                    log.debug("Writing Acknowledgement");
                    MllpUtil.writeFramedPayload(clientSocket, acknowledgementMessageBytes);
                    exchange.getIn().setHeader(MLLP_ACKNOWLEDGEMENT, acknowledgementMessageBytes);
                    exchange.getIn().setHeader(MLLP_ACKNOWLEDGEMENT_TYPE, acknowledgementMessageType);

                    // Check AFTER_SEND Properties
                    if (exchange.getProperty(MLLP_RESET_CONNECTION_AFTER_SEND, boolean.class)) {
                        MllpUtil.resetConnection(clientSocket);
                        return;
                    } else if (exchange.getProperty(MLLP_CLOSE_CONNECTION_AFTER_SEND, boolean.class)) {
                        MllpUtil.closeConnection(clientSocket);
                    }

                } catch (Exception e) {
                    exchange.setException(e);
                }

            }

            log.info("ClientSocketThread exiting");

        }

        private void populateHl7DataHeaders(Exchange exchange, Message message, byte[] hl7MessageBytes) {
            // Find the end of the MSH and indexes of the fields in the MSH to populate message headers
            final byte fieldSeparator = hl7MessageBytes[3];
            final byte componentSeparator = hl7MessageBytes[4];
            int endOfMSH = -1;
            List<Integer> fieldSeparatorIndexes = new ArrayList<>(10);  // We need at least 10 fields to create the acknowledgment

            for (int i = 0; i < hl7MessageBytes.length; ++i) {
                if (fieldSeparator == hl7MessageBytes[i]) {
                    fieldSeparatorIndexes.add(i);
                } else if (SEGMENT_DELIMITER == hl7MessageBytes[i]) {
                    endOfMSH = i;
                    break;
                }
            }

            if (-1 == endOfMSH) {
                // TODO:  May want to throw some sort of an Exception here
                log.error("Population of message headers failed - unable to find the end of the MSH segment");
            } else {
                log.debug("Populating the message headers");
                Charset charset = Charset.forName(IOHelper.getCharsetName(exchange));

                // MSH-3
                message.setHeader(MLLP_SENDING_APPLICATION, new String(hl7MessageBytes, fieldSeparatorIndexes.get(1) + 1,
                        fieldSeparatorIndexes.get(2) - fieldSeparatorIndexes.get(1) - 1, charset));
                // MSH-4
                message.setHeader(MLLP_SENDING_FACILITY, new String(hl7MessageBytes, fieldSeparatorIndexes.get(2) + 1,
                        fieldSeparatorIndexes.get(3) - fieldSeparatorIndexes.get(2) - 1, charset));
                // MSH-5
                message.setHeader(MLLP_RECEIVING_APPLICATION, new String(hl7MessageBytes, fieldSeparatorIndexes.get(3) + 1,
                        fieldSeparatorIndexes.get(4) - fieldSeparatorIndexes.get(3) - 1,
                        charset));
                // MSH-6
                message.setHeader(MLLP_RECEIVING_FACILITY, new String(hl7MessageBytes, fieldSeparatorIndexes.get(4) + 1,
                        fieldSeparatorIndexes.get(5) - fieldSeparatorIndexes.get(4) - 1,
                        charset));
                // MSH-7
                message.setHeader(MLLP_TIMESTAMP, new String(hl7MessageBytes, fieldSeparatorIndexes.get(5) + 1,
                        fieldSeparatorIndexes.get(6) - fieldSeparatorIndexes.get(5) - 1, charset));
                // MSH-8
                message.setHeader(MLLP_SECURITY, new String(hl7MessageBytes, fieldSeparatorIndexes.get(6) + 1,
                        fieldSeparatorIndexes.get(7) - fieldSeparatorIndexes.get(6) - 1, charset));
                // MSH-9
                message.setHeader(MLLP_MESSAGE_TYPE, new String(hl7MessageBytes, fieldSeparatorIndexes.get(7) + 1,
                        fieldSeparatorIndexes.get(8) - fieldSeparatorIndexes.get(7) - 1, charset));
                // MSH-10
                message.setHeader(MLLP_MESSAGE_CONTROL, new String(hl7MessageBytes, fieldSeparatorIndexes.get(8) + 1,
                        fieldSeparatorIndexes.get(9) - fieldSeparatorIndexes.get(8) - 1, charset));
                // MSH-11
                message.setHeader(MLLP_PROCESSING_ID, new String(hl7MessageBytes, fieldSeparatorIndexes.get(9) + 1,
                        fieldSeparatorIndexes.get(10) - fieldSeparatorIndexes.get(9) - 1, charset));
                // MSH-12
                message.setHeader(MLLP_VERSION_ID, new String(hl7MessageBytes, fieldSeparatorIndexes.get(10) + 1,
                        fieldSeparatorIndexes.get(11) - fieldSeparatorIndexes.get(10) - 1, charset));
                // MSH-18
                message.setHeader(MLLP_CHARSET, new String(hl7MessageBytes, fieldSeparatorIndexes.get(16) + 1,
                        fieldSeparatorIndexes.get(17) - fieldSeparatorIndexes.get(16) - 1, charset));

                for (int i = fieldSeparatorIndexes.get(7) + 1; i < fieldSeparatorIndexes.get(8); ++i) {
                    if (componentSeparator == hl7MessageBytes[i]) {
                        // MSH-9.1
                        message.setHeader(MLLP_EVENT_TYPE, new String(hl7MessageBytes, fieldSeparatorIndexes.get(7) + 1,
                                i - fieldSeparatorIndexes.get(7) - 1, charset));
                        // MSH-9.2
                        message.setHeader(MLLP_TRIGGER_EVENT, new String(hl7MessageBytes, i + 1,
                                fieldSeparatorIndexes.get(8) - i - 1, charset));
                        break;
                    }
                }
            }
        }


    }
}

