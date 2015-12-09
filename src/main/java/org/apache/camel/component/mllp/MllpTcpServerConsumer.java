/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.mllp.impl.MllpConstants;
import org.apache.camel.component.mllp.impl.MllpSocketUtil;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.mllp.Hl7AcknowledgementGenerator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;

/**
 * The mllp consumer.
 */
public class MllpTcpServerConsumer extends DefaultConsumer {
    // Logger log = LoggerFactory.getLogger(this.getClass());


    private final MllpEndpoint endpoint;

    AcceptThread acceptThread;

    List<ClientSocketThread> clientThreads = new LinkedList<ClientSocketThread>();

    public MllpTcpServerConsumer(MllpEndpoint endpoint, Processor processor) throws IOException {
        super(endpoint, processor);
        log.trace("MllpTcpServerConsumer(endpoint, processor)");


        this.endpoint = endpoint;

    }

    @Override
    protected void doStart() throws Exception {
        log.debug("doStart() - creating acceptor thread");

        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReceiveBufferSize(endpoint.receiveBufferSize);
        serverSocket.setReuseAddress(endpoint.reuseAddress);

        // Read Timeout
        serverSocket.setSoTimeout(endpoint.responseTimeout);

        InetSocketAddress socketAddress = new InetSocketAddress(endpoint.getHostname(), endpoint.getPort());
        serverSocket.bind(socketAddress, endpoint.backlog);

        acceptThread = new AcceptThread(serverSocket);
        acceptThread.start();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("doStop()");

        switch (acceptThread.getState()) {
            case NEW:
            case RUNNABLE:
            case BLOCKED:
            case WAITING:
            case TIMED_WAITING:
                acceptThread.interrupt();
                break;
            case TERMINATED:
                // This is what we hope for
                break;
        }

        acceptThread = null;

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


    class AcceptThread extends Thread {
        // TODO:  Need to set thread name
        ServerSocket serverSocket;

        AcceptThread(ServerSocket serverSocket) {
            log.info("Creating new AcceptThread");
            this.setName(String.format("mllp://%s:%d - AcceptThread", endpoint.getHostname(), endpoint.getPort()));

            this.serverSocket = serverSocket;
        }

        public void run() {
            log.debug("Starting acceptor thread for socket {}:{}", endpoint.getHostname(), endpoint.getPort());

            while (serverSocket.isBound() && !serverSocket.isClosed()) {
                try {
                    /* ? set this here ? */
                    // serverSocket.setSoTimeout( 10000 );
                    // TODO: Need to check maxConnections and figure outputStream what to do when exceeded
                    Socket clientSocket = serverSocket.accept();
                    // Check and see if the socket is really there.  It could be a load balancer pinging the port
                    Thread.sleep(100);
                    if (clientSocket.isConnected() && !clientSocket.isClosed()) {
                        log.info("Socket appears to be there - get the input stream and see");
                        InputStream inputStream = clientSocket.getInputStream();
                        clientSocket.setSoTimeout(100);
                        try {
                            int tmpByte = inputStream.read();
                            if (-1 == tmpByte) {
                                log.debug("Socket closed before read - possible load balancer probe");
                            } else {
                                ClientSocketThread clientThread = new ClientSocketThread(clientSocket, tmpByte);
                                clientThreads.add(clientThread);
                                clientThread.start();
                            }
                        } catch (SocketTimeoutException timeoutEx) {
                            // No data, but the socket is there
                            log.debug("No Data - but the socket is there.  Starting ClientSocketThread");
                            ClientSocketThread clientThread = new ClientSocketThread(clientSocket);
                            clientThreads.add(clientThread);
                            clientThread.start();
                        }
                    }
                } catch (SocketTimeoutException timeoutEx) {
                    // No new clients - check existing ones
                    // TODO:  Check existing clients
                    // log.debug( "SocketTimeoutException waiting for new connections");
                } catch (Exception ex) {
                    log.error("Exception waiting for new connections", ex);
                }
            }
        }

    }

    class ClientSocketThread extends Thread {
        Socket clientSocket;
        Hl7AcknowledgementGenerator acknowledgementGenerator = new Hl7AcknowledgementGenerator();

        Integer initialByte = null;

        ClientSocketThread(Socket clientSocket) throws IOException {
            log.info("Creating new ClientSocketThread");
            this.setName(String.format("mllp://%s:%d - Client Socket Thread", endpoint.getHostname(), endpoint.getPort()));
            this.clientSocket = clientSocket;
            this.clientSocket.setKeepAlive(endpoint.keepAlive);
            this.clientSocket.setTcpNoDelay(endpoint.tcpNoDelay);
            this.clientSocket.setReceiveBufferSize(endpoint.receiveBufferSize);
            this.clientSocket.setSendBufferSize(endpoint.sendBufferSize);
            this.clientSocket.setReuseAddress(endpoint.reuseAddress);
            this.clientSocket.setSoLinger(false, -1);

            // Read Timeout
            this.clientSocket.setSoTimeout(endpoint.responseTimeout);

            acknowledgementGenerator.setCharset( endpoint.charset );
        }

        ClientSocketThread(Socket clientSocket, int initialByte) throws IOException {
            log.info("Creating new ClientSocketThread");
            this.initialByte = initialByte;
            this.setName(String.format("mllp://%s:%d - Client Socket Thread", endpoint.getHostname(), endpoint.getPort()));
            this.clientSocket = clientSocket;
            this.clientSocket.setKeepAlive(endpoint.keepAlive);
            this.clientSocket.setTcpNoDelay(endpoint.tcpNoDelay);
            this.clientSocket.setReceiveBufferSize(endpoint.receiveBufferSize);
            this.clientSocket.setSendBufferSize(endpoint.sendBufferSize);
            this.clientSocket.setReuseAddress(endpoint.reuseAddress);
            this.clientSocket.setSoLinger(false, -1);

            // Read Timeout
            this.clientSocket.setSoTimeout(endpoint.responseTimeout);

        }

        @Override
        public void run() {
            while (clientSocket.isConnected() && !clientSocket.isClosed()) {
                // create the exchange
                Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);

                byte[] hl7MessageBytes = null;
                // Send the message on for processing and wait for the response
                log.debug("Reading data ....");
                try {
                    if (null != initialByte && MllpConstants.START_OF_BLOCK == initialByte) {
                        initialByte = null;
                        hl7MessageBytes = MllpSocketUtil.readThroughEndOfBlock(MllpSocketUtil.getInputStream(clientSocket));
                    } else {
                        initialByte = null;
                        hl7MessageBytes = MllpSocketUtil.readEnvelopedMessageBytes(clientSocket);
                    }
                } catch (MllpException mllpEx) {
                    log.error("Exception encountered reading enveloped message", mllpEx);
                    exchange.setException(mllpEx);
                    // TODO:  Is this correct?
                    continue;
                }

                if (null != hl7MessageBytes) {
                    log.debug("Populating the exchange with received data");
                    if (endpoint.useString) {
                        String hl7Message = new String(hl7MessageBytes, endpoint.charset);
                        exchange.getIn().setBody(hl7Message, String.class);
                    } else {
                        exchange.getIn().setBody(hl7MessageBytes, byte[].class);
                    }
                } else {
                    continue;
                }

                log.debug("Calling processor");
                try {
                    getProcessor().process(exchange);
                    // Got the response - send the acknowledgement

                    // Find the acknowledgement body
                    byte[] acknowledgementMessageBytes;
                    if (endpoint.autoAck) {
                        if (null == exchange.getException()) {
                            acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationAcceptAcknowledgementMessage(hl7MessageBytes);
                        } else {
                            acknowledgementMessageBytes = acknowledgementGenerator.generateApplicationErrorAcknowledgementMessage(hl7MessageBytes);
                        }
                    } else {
                        Object exchangeBody;
                        if ( exchange.hasOut() ) {
                            exchangeBody = exchange.getOut().getBody();
                        } else {
                            exchangeBody = exchange.getIn().getBody();
                        }
                        if (null == exchangeBody) {
                            // TODO:  Probably need to do more here
                            exchange.setException(new IllegalArgumentException("Null Exchange Body sent for acknowledgement"));
                            continue;
                        } else {
                            if (exchangeBody instanceof byte[]) {
                                acknowledgementMessageBytes = (byte[]) exchangeBody;
                            } else if (exchangeBody instanceof String) {
                                acknowledgementMessageBytes = ((String) exchangeBody).getBytes(endpoint.charset);
                            } else {
                                exchange.setException(new IllegalArgumentException("Exchange Body must be String or byte[] for acknowledgement"));
                                continue;
                            }
                        }
                    }

                    MllpSocketUtil.writeEnvelopedMessageBytes(clientSocket, acknowledgementMessageBytes);
                } catch (Exception e) {
                    exchange.setException(e);
                }

            }

            log.info("ClientSocketThread exiting");

        }


    }
}

