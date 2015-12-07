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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.mllp.impl.MllpSocketUtil;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

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
        serverSocket.setReceiveBufferSize( endpoint.receiveBufferSize );
        serverSocket.setReuseAddress(endpoint.reuseAddress);

        // Read Timeout
        serverSocket.setSoTimeout(endpoint.responseTimeout);

        InetSocketAddress socketAddress = new InetSocketAddress( endpoint.getHostname(), endpoint.getPort());
        serverSocket.bind(socketAddress, endpoint.backlog);

        acceptThread = new AcceptThread( serverSocket );
        acceptThread.start();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        log.debug("doStop()");

        switch ( acceptThread.getState() ) {
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

        AcceptThread( ServerSocket serverSocket ) {
            log.info("Creating new AcceptThread");

            this.serverSocket = serverSocket;
        }

        public void run() {
            log.debug( "Starting acceptor thread for socket {}:{}", endpoint.getHostname(), endpoint.getPort());

            while (serverSocket.isBound() && ! serverSocket.isClosed()) {
                try {
                    /* ? set this here ? */
                    // serverSocket.setSoTimeout( 10000 );
                    // TODO: Need to check maxConnections and figure outputStream what to do when exceeded
                    ClientSocketThread clientThread = new ClientSocketThread(serverSocket.accept());
                    clientThreads.add(clientThread);
                    clientThread.start();
                } catch ( SocketTimeoutException timeoutEx ) {
                    // No new clients - check existing ones
                    // TODO:  Check existing clients

                } catch (Exception e) {
                    Exchange exchange = endpoint.createExchange( ExchangePattern.InOut );
                    exchange.setException(e);
                    try {
                        getProcessor().process(exchange);
                    } catch (Exception e1) {
                        // TODO: Figure outputStream what to do here
                        e1.printStackTrace();
                    }
                }
            }
        }

    }

    class ClientSocketThread extends Thread {
        Socket clientSocket;

        InputStream inputStream;
        BufferedOutputStream outputStream;

        ClientSocketThread(Socket clientSocket) throws IOException {
            log.info("Creating new ClientSocketThread");
            this.clientSocket = clientSocket;
            this.clientSocket.setKeepAlive(endpoint.keepAlive);
            this.clientSocket.setTcpNoDelay(endpoint.tcpNoDelay);
            this.clientSocket.setReceiveBufferSize( endpoint.receiveBufferSize );
            this.clientSocket.setSendBufferSize( endpoint.sendBufferSize );
            this.clientSocket.setReuseAddress(endpoint.reuseAddress);
            this.clientSocket.setSoLinger(false, -1);

            // Read Timeout
            this.clientSocket.setSoTimeout(endpoint.responseTimeout);

            this.inputStream = clientSocket.getInputStream();
            this.outputStream = new BufferedOutputStream(clientSocket.getOutputStream(), endpoint.sendBufferSize);
        }

        @Override
        public void run() {
            while (clientSocket.isConnected() && !clientSocket.isClosed()) {
                // create the exchange
                Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);

                // Send the message on for processing and wait for the response
                log.debug("Populating the exchange");
                String hl7Message = null;
                try {
                    hl7Message = MllpSocketUtil.readEnvelopedMessage(endpoint.charset, clientSocket, inputStream);
                } catch (MllpException mllpEx) {
                    log.error( "Exception encountered reading enveloped message", mllpEx);
                    exchange.setException( mllpEx );
                    return;
                }

                exchange.getIn().setBody(hl7Message, String.class);
                try {
                    getProcessor().process(exchange);
                    // Got the response - send the acknowledgement

                    // Find the acknowledgement body
                    String acknowledgementMessage;
                    if ( endpoint.autoAck ) {
                        acknowledgementMessage = generateAcknowledgementMessage(hl7Message);
                    } else {
                        Object exchangeBody = exchange.getOut().getBody();
                        if ( null == exchangeBody ) {
                            exchangeBody = exchange.getIn().getBody();
                        }
                        if ( null == exchangeBody ) {
                            // TODO:  Probably need to do more here
                            exchange.setException(new IllegalArgumentException( "Null Exchange Body sent for acknowledgement"));
                            return;
                        } else {
                            if ( exchangeBody instanceof String ) {
                                acknowledgementMessage = ((String)exchangeBody);
                            } else {
                                exchange.setException( new IllegalArgumentException( "Exchange Body must be String or byte[] for acknowledgement"));
                                return;
                            }
                        }
                    }

                    MllpSocketUtil.writeEnvelopedMessage(acknowledgementMessage, endpoint.charset, clientSocket, outputStream);
                } catch (Exception e) {
                    exchange.setException( e );
                }

            }
            log.info("ClientSocketThread exiting");

        }

        private String generateAcknowledgementMessage(String hl7Message) {
            return generateAcknowledgementMessage(hl7Message, "AA");
        }

        private String generateAcknowledgementMessage(String hl7Message, String acknowledgementCode) {
            final String DEFAULT_NACK_MESSAGE =
                    "MSH|^~\\&|||||||NACK||P|2.2" + MllpEndpoint.SEGMENT_DELIMITER
                            + "MSA|AR|" + MllpEndpoint.SEGMENT_DELIMITER
                            + MllpEndpoint.MESSAGE_TERMINATOR;

            if (hl7Message == null) {
                log.error("Invalid HL7 message for parsing operation. Please check your inputs");
                return DEFAULT_NACK_MESSAGE;
            }

            String messageControlId;

            int endOfMshSegment = hl7Message.indexOf(MllpEndpoint.SEGMENT_DELIMITER);
            if (-1 != endOfMshSegment) {
                String mshSegment = hl7Message.substring(0, endOfMshSegment);
                char fieldSeparator = mshSegment.charAt(3);
                String fieldSeparatorPattern = Pattern.quote("" + fieldSeparator);
                String[] mshFields = mshSegment.split(fieldSeparatorPattern);
                if (null == mshFields || 0 == mshFields.length) {
                    log.error("Failed to split MSH Segment into fields");
                } else {
                    StringBuilder ackBuilder = new StringBuilder(mshSegment.length() + 25);
                    // Build the MSH Segment
                    ackBuilder
                            .append(mshFields[0]).append(fieldSeparator)
                            .append(mshFields[1]).append(fieldSeparator)
                            .append(mshFields[4]).append(fieldSeparator)
                            .append(mshFields[5]).append(fieldSeparator)
                            .append(mshFields[2]).append(fieldSeparator)
                            .append(mshFields[3]).append(fieldSeparator)
                            .append(mshFields[6]).append(fieldSeparator)
                            .append(mshFields[7]).append(fieldSeparator)
                            .append("ACK").append(mshFields[8].substring(3))
                    ;
                    for (int i = 9; i < mshFields.length; ++i) {
                        ackBuilder.append(fieldSeparator).append(mshFields[i]);
                    }
                    // Empty fields at the end are not preserved by String.split, so preserve them
                    int emptyFieldIndex = mshSegment.length() - 1;
                    if (fieldSeparator == mshSegment.charAt(mshSegment.length() - 1)) {
                        ackBuilder.append(fieldSeparator);
                        while (emptyFieldIndex >= 1 && mshSegment.charAt(emptyFieldIndex) == mshSegment.charAt(emptyFieldIndex - 1)) {
                            ackBuilder.append(fieldSeparator);
                            --emptyFieldIndex;
                        }
                    }
                    ackBuilder.append(MllpEndpoint.SEGMENT_DELIMITER);

                    // Build the MSA Segment
                    ackBuilder
                            .append("MSA").append(fieldSeparator)
                            .append(acknowledgementCode).append(fieldSeparator)
                            .append(mshFields[9]).append(fieldSeparator)
                            .append(MllpEndpoint.SEGMENT_DELIMITER)
                    ;

                    // Terminate the message
                    ackBuilder.append(MllpEndpoint.MESSAGE_TERMINATOR);

                    return ackBuilder.toString();
                }
            } else {
                log.error("Failed to find the end of the  MSH Segment while attempting to generate response");
            }

            return null;
        }

    }
}

