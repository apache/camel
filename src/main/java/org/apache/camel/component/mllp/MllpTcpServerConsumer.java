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

                // Read the HL7 Message
                StringBuilder hl7MessageBuilder = new StringBuilder();

                try {
                    int inByte = inputStream.read();
                    if (inByte != MllpEndpoint.START_OF_BLOCK) {
                        // We have out-of-band data
                        StringBuilder outOfBandData = new StringBuilder();
                        do {
                            if ( -1 == inByte ) {
                                log.warn("End of buffer reached before START_OF_BLOCK Found", outOfBandData.toString());
                                return;
                            } else {
                                outOfBandData.append((char) inByte);
                                inByte = inputStream.read();
                            }
                        } while (MllpEndpoint.START_OF_BLOCK != inByte );
                        log.warn("Eating out-of-band data: {}", outOfBandData.toString());

                    }


                    if (MllpEndpoint.START_OF_BLOCK != inByte) {
                        exchange.setException(new MllpEnvelopeException("Message did not start with START_OF_BLOCK"));
                        return;
                    }

                    boolean readingMessage = true;
                    while (readingMessage) {
                        int nextByte = inputStream.read();
                        switch (nextByte) {
                            case -1:
                                exchange.setException(new MllpEnvelopeException("Reached end of stream before END_OF_BLOCK"));
                                return;
                            case MllpEndpoint.START_OF_BLOCK:
                                exchange.setException(new MllpEnvelopeException("Received START_OF_BLOCK before END_OF_BLOCK"));
                                return;
                            case MllpEndpoint.END_OF_BLOCK:
                                if (MllpEndpoint.END_OF_DATA != inputStream.read()) {
                                    exchange.setException(new MllpEnvelopeException("END_OF_BLOCK was not followed by END_OF_DATA"));
                                    return;
                                }
                                readingMessage = false;
                                break;
                            default:
                                hl7MessageBuilder.append((char) nextByte);
                        }
                    }
                } catch ( SocketTimeoutException timeoutEx ) {
                    exchange.setException( new MllpRequestTimeoutException("Timeout reading message", timeoutEx));
                    if ( hl7MessageBuilder.length() > 0 ) {
                        log.error( "Timeout reading message after receiveing partial payload:\n{}", hl7MessageBuilder.toString().replace('\r', '\n'));
                    } else {
                        log.error( "Timout reading message - no data received");
                    }

                } catch (IOException e) {
                    log.error("Unable to read HL7 message", e);
                    exchange.setException( new MllpException("Unable to read HL7 message", e) );
                    return;
                }

                // Send the message on for processing and wait for the response
                log.debug("Populating the exchange");
                String hl7Message = hl7MessageBuilder.toString();

                exchange.getIn().setBody(hl7Message, String.class);
                try {
                    getProcessor().process(exchange);
                    // Got the response - send the acknowledgement

                    // Find the acknowledgement body
                    byte[] acknowledgementBytes;
                    if ( endpoint.autoAck ) {
                        acknowledgementBytes = generateAcknowledgementMessage(hl7Message).getBytes(endpoint.charset);
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
                            if ( exchangeBody instanceof byte[] ) {
                                acknowledgementBytes = (byte[])exchangeBody;
                            } else if ( exchangeBody instanceof String ) {
                                acknowledgementBytes = ((String)exchangeBody).getBytes(endpoint.charset);
                            } else {
                                exchange.setException( new IllegalArgumentException( "Exchange Body must be String or byte[] for acknowledgement"));
                                return;
                            }
                        }
                    }

                    // Now we have the acknowledgement, send the response
                    outputStream.write( MllpEndpoint.START_OF_BLOCK );
                    outputStream.write( acknowledgementBytes, 0, acknowledgementBytes.length );
                    outputStream.write( MllpEndpoint.END_OF_BLOCK );
                    outputStream.write( MllpEndpoint.END_OF_DATA );
                    outputStream.flush();

                } catch (Exception e) {
                    exchange.setException( e );
                }

            }
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

