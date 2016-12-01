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
import org.apache.camel.component.mllp.impl.AcknowledgmentSynchronizationAdapter;
import org.apache.camel.component.mllp.impl.MllpUtil;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.IOHelper;

import static org.apache.camel.component.mllp.MllpConstants.MLLP_AUTO_ACKNOWLEDGE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_CHARSET;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_EVENT_TYPE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_LOCAL_ADDRESS;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_MESSAGE_CONTROL;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_MESSAGE_TYPE;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_PROCESSING_ID;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RECEIVING_APPLICATION;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_RECEIVING_FACILITY;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_REMOTE_ADDRESS;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_SECURITY;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_SENDING_APPLICATION;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_SENDING_FACILITY;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_TIMESTAMP;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_TRIGGER_EVENT;
import static org.apache.camel.component.mllp.MllpConstants.MLLP_VERSION_ID;
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

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("doStop()");

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

        super.doStop();
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

            try {
                while (!isInterrupted()  &&  null != serverSocket && serverSocket.isBound()  &&  !serverSocket.isClosed()) {
                    // TODO: Need to check maxConnections and figure out what to do when exceeded
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
                                socket.setSoTimeout(endpoint.receiveTimeout);
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
     * Nested Class read the Socket
     */
    class ClientSocketThread extends Thread {
        Socket clientSocket;

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
            this.clientSocket.setSoTimeout(endpoint.receiveTimeout);

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

            while (!isInterrupted()  &&  null != clientSocket  &&  clientSocket.isConnected()  &&  !clientSocket.isClosed()) {
                byte[] hl7MessageBytes = null;
                // Send the message on for processing and wait for the response
                log.debug("Reading data ....");
                try {
                    if (null != initialByte && START_OF_BLOCK == initialByte) {
                        hl7MessageBytes = MllpUtil.closeFrame(clientSocket, endpoint.receiveTimeout, endpoint.readTimeout);
                    } else {
                        try {
                            if (!MllpUtil.openFrame(clientSocket, endpoint.receiveTimeout, endpoint.readTimeout)) {
                                receiveTimeoutCounter = 0;
                                continue;
                            } else {
                                receiveTimeoutCounter = 0;
                            }
                        } catch (SocketTimeoutException timeoutEx) {
                            // When thrown by openFrame, it indicates that no data was available - but no error
                            if (endpoint.maxReceiveTimeouts > 0 && ++receiveTimeoutCounter >= endpoint.maxReceiveTimeouts) {
                                // TODO:  Enhance logging??
                                log.warn("Idle Client - resetting connection");
                                MllpUtil.resetConnection(clientSocket);
                            }
                            continue;
                        }
                        hl7MessageBytes = MllpUtil.closeFrame(clientSocket, endpoint.receiveTimeout, endpoint.readTimeout);
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
                message.setHeader(MLLP_AUTO_ACKNOWLEDGE, endpoint.autoAck);

                populateHl7DataHeaders(exchange, message, hl7MessageBytes);

                exchange.addOnCompletion(new AcknowledgmentSynchronizationAdapter(clientSocket, hl7MessageBytes));

                log.debug("Calling processor");
                try {
                    getProcessor().process(exchange);
                } catch (RuntimeException runtimeEx) {
                    throw runtimeEx;
                } catch (Exception ex) {
                    log.error("Unexpected exception processing exchange", ex);
                    throw new RuntimeException("Unexpected exception processing exchange", ex);
                }

            }

            log.debug("ClientSocketThread exiting");
        }

        private void populateHl7DataHeaders(Exchange exchange, Message message, byte[] hl7MessageBytes) {
            // Find the end of the MSH and indexes of the fields in the MSH to populate message headers
            final byte fieldSeparator = hl7MessageBytes[3];
            int endOfMSH = -1;
            List<Integer> fieldSeparatorIndexes = new ArrayList<>(10);  // We need at least 10 fields to create the acknowledgment

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
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    log.warn("Exception encoutered closing client Socket in interrupt", ex);
                }
            }
            super.interrupt();
        }
    }
}

