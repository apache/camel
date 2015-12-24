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
package org.apache.camel.test.junit.rule.mllp;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_DATA;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_STREAM;
import static org.apache.camel.component.mllp.MllpEndpoint.MESSAGE_TERMINATOR;
import static org.apache.camel.component.mllp.MllpEndpoint.SEGMENT_DELIMITER;
import static  org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;
/**
 * MLLP Test Server packaged as a JUnit Rule
 *
 * The server can be configured to simulate a large number
 * of error conditions.
 *
 * TODO:  This needs to be looked at - it may be orphaning threads
 */
public class MllpServerResource extends ExternalResource {
    Logger log = LoggerFactory.getLogger(this.getClass());

    int listenPort;
    int backlog = 5;

    int counter = 1;

    boolean active = true;

    int excludeStartOfBlockModulus;
    int excludeEndOfBlockModulus;
    int excludeEndOfDataModulus;

    int excludeAcknowledgementModulus;

    int sendOutOfBandDataModulus;

    int disconnectBeforeAcknowledgementModulus;
    int disconnectAfterAcknowledgementModulus;

    int sendApplicationRejectAcknowledgementModulus;
    int sendApplicationErrorAcknowledgementModulus;

    Pattern sendApplicationRejectAcknowledgementPattern;
    Pattern sendApplicationErrorAcknowledgementPattern;

    ServerSocketThread serverSocketThread;

    public MllpServerResource() {
    }

    public MllpServerResource(int listenPort) {
        this.listenPort = listenPort;
    }

    public MllpServerResource(int listenPort, int backlog) {
        this.listenPort = listenPort;
        this.backlog = backlog;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public void startup() throws IOException {
        this.active = true;
        serverSocketThread = new ServerSocketThread(listenPort, backlog);
        if (0 >= listenPort) {
            listenPort = serverSocketThread.listenPort;
        }
        serverSocketThread.setDaemon(true);
        serverSocketThread.start();
    }

    public void shutdown() {
        this.active = false;
        serverSocketThread.shutdown();
        serverSocketThread = null;
    }


    @Override
    protected void before() throws Throwable {
        startup();
        super.before();
    }

    @Override
    protected void after() {
        super.after();
        shutdown();
    }

    public void interrupt() {
        serverSocketThread.interrupt();
    }

    public boolean sendApplicationRejectAcknowledgement(String hl7Message) {
        return evaluatePatten(hl7Message, this.sendApplicationErrorAcknowledgementPattern);
    }

    public boolean sendApplicationErrorAcknowledgement(String hl7Message) {
        return evaluatePatten(hl7Message, this.sendApplicationRejectAcknowledgementPattern);
    }

    public boolean sendApplicationRejectAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, this.sendApplicationRejectAcknowledgementModulus);
    }

    public boolean sendApplicationErrorAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, this.sendApplicationErrorAcknowledgementModulus);
    }


    public boolean excludeStartOfBlock(int messageCount) {
        return evaluateModulus(messageCount, excludeStartOfBlockModulus);
    }

    public boolean excludeAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, excludeAcknowledgementModulus);
    }

    public boolean excludeEndOfBlock(int messageCount) {
        return evaluateModulus(messageCount, excludeEndOfBlockModulus);
    }

    public boolean excludeEndOfData(int messageCount) {
        return evaluateModulus(messageCount, excludeEndOfDataModulus);
    }

    public boolean disconnectBeforeAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, disconnectBeforeAcknowledgementModulus);
    }

    public boolean disconnectAfterAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, disconnectAfterAcknowledgementModulus);
    }

    public boolean sendOutOfBandData(int messageCount) {
        return evaluateModulus(messageCount, sendOutOfBandDataModulus);
    }

    private boolean evaluateModulus(int messageCount, int modulus) {
        switch (modulus) {
        case 0:
            return false;
        case 1:
            return true;
        default:
            return (messageCount % modulus == 0) ? true : false;

        }
    }

    private boolean evaluatePatten(String hl7Message, Pattern pattern) {
        boolean retValue = false;

        if (null != pattern && pattern.matcher(hl7Message).matches()) {
            retValue = true;
        }

        return retValue;
    }


    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getExcludeStartOfBlockModulus() {
        return excludeStartOfBlockModulus;
    }

    /**
     * Set the modulus used to determine when to include the bMLLP_ENVELOPE_START_OF_BLOCK
     * portion of the MLLP Envelope.
     * <p/>
     * If this value is less than or equal to 0, the bMLLP_ENVELOPE_START_OF_BLOCK portion
     * of the MLLP Envelope will always be included.
     * If the value is 1, the bMLLP_ENVELOPE_START_OF_BLOCK portion of the MLLP Envelope will
     * never be included.
     * Otherwise, if the result of evaluating message count % value is greater
     * than 0, the bMLLP_ENVELOPE_START_OF_BLOCK portion of the MLLP Envelope will not be
     * included.  Effectively leaving the bMLLP_ENVELOPE_START_OF_BLOCK portion of the MLLP Envelope
     * out of every n-th message.
     *
     * @param excludeStartOfBlockModulus exclude on every n-th message
     *                                   0 => Never excluded
     *                                   1 => Always excluded
     */
    public void setExcludeStartOfBlockModulus(int excludeStartOfBlockModulus) {
        if (0 > excludeStartOfBlockModulus) {
            this.excludeStartOfBlockModulus = 0;
        } else {
            this.excludeStartOfBlockModulus = excludeStartOfBlockModulus;
        }
    }

    public void enableMllpEnvelope() {
        this.setExcludeStartOfBlockModulus(0);
        this.setExcludeEndOfBlockModulus(0);
        this.setExcludeEndOfDataModulus(0);
    }

    public void disableMllpEnvelopeStart() {
        this.disableMllpEnvelopeStart(1);
    }

    public void disableMllpEnvelopeStart(int mllpEnvelopeModulus) {
        this.setExcludeStartOfBlockModulus(mllpEnvelopeModulus);
    }

    public void disableMllpEnvelopeEnd() {
        this.disableMllpEnvelope(1);
    }

    public void disableMllpEnvelopeEnd(int mllpEnvelopeModulus) {
        this.setExcludeEndOfBlockModulus(mllpEnvelopeModulus);
        this.setExcludeEndOfDataModulus(mllpEnvelopeModulus);
    }

    public void disableMllpEnvelope() {
        this.disableMllpEnvelope(1);
    }

    public void disableMllpEnvelope(int mllpEnvelopeModulus) {
        this.setExcludeStartOfBlockModulus(mllpEnvelopeModulus);
        this.setExcludeEndOfBlockModulus(mllpEnvelopeModulus);
        this.setExcludeEndOfDataModulus(mllpEnvelopeModulus);
    }

    public void enableResponse() {
        this.setExcludeStartOfBlockModulus(0);
        this.setExcludeAcknowledgementModulus(0);
        this.setExcludeEndOfBlockModulus(0);
        this.setExcludeEndOfDataModulus(0);
    }

    public void disableResponse() {
        this.disableResponse(1);
    }

    public void disableResponse(int mllpResponseModulus) {
        this.setExcludeStartOfBlockModulus(mllpResponseModulus);
        this.setExcludeAcknowledgementModulus(mllpResponseModulus);
        this.setExcludeEndOfBlockModulus(mllpResponseModulus);
        this.setExcludeEndOfDataModulus(mllpResponseModulus);
    }

    public int getExcludeEndOfBlockModulus() {
        return excludeEndOfBlockModulus;
    }

    public void setExcludeEndOfBlockModulus(int excludeEndOfBlockModulus) {
        if (0 > excludeEndOfBlockModulus) {
            this.excludeEndOfBlockModulus = 0;
        } else {
            this.excludeEndOfBlockModulus = excludeEndOfBlockModulus;
        }
    }

    public int getExcludeEndOfDataModulus() {
        return excludeEndOfDataModulus;
    }

    public void setExcludeEndOfDataModulus(int excludeEndOfDataModulus) {
        if (0 > excludeEndOfDataModulus) {
            this.excludeEndOfDataModulus = 0;
        } else {
            this.excludeEndOfDataModulus = excludeEndOfDataModulus;
        }
    }

    public int getExcludeAcknowledgementModulus() {
        return excludeAcknowledgementModulus;
    }

    public void setExcludeAcknowledgementModulus(int excludeAcknowledgementModulus) {
        if (0 > excludeAcknowledgementModulus) {
            this.excludeAcknowledgementModulus = 0;
        } else {
            this.excludeAcknowledgementModulus = excludeAcknowledgementModulus;
        }
    }


    public int getSendOutOfBandDataModulus() {
        return sendOutOfBandDataModulus;
    }

    public void setSendOutOfBandDataModulus(int sendOutOfBandDataModulus) {
        if (0 > sendOutOfBandDataModulus) {
            this.sendOutOfBandDataModulus = 0;
        } else {
            this.sendOutOfBandDataModulus = sendOutOfBandDataModulus;
        }
    }

    public int getDisconnectBeforeAcknowledgementModulus() {
        return disconnectBeforeAcknowledgementModulus;
    }

    public void setDisconnectBeforeAcknowledgementModulus(int disconnectBeforeAcknowledgementModulus) {
        if (0 > disconnectBeforeAcknowledgementModulus) {
            this.disconnectBeforeAcknowledgementModulus = 0;
        } else {
            this.disconnectBeforeAcknowledgementModulus = disconnectBeforeAcknowledgementModulus;
        }
    }

    public int getDisconnectAfterAcknowledgementModulus() {
        return disconnectAfterAcknowledgementModulus;
    }

    public void setDisconnectAfterAcknowledgementModulus(int disconnectAfterAcknowledgementModulus) {
        if (0 > disconnectAfterAcknowledgementModulus) {
            this.disconnectAfterAcknowledgementModulus = 0;
        } else {
            this.disconnectAfterAcknowledgementModulus = disconnectAfterAcknowledgementModulus;
        }
    }

    public int getSendApplicationRejectAcknowledgementModulus() {
        return sendApplicationRejectAcknowledgementModulus;
    }

    public void setSendApplicationRejectAcknowledgementModulus(int sendApplicationRejectAcknowledgementModulus) {
        if (0 > sendApplicationRejectAcknowledgementModulus) {
            this.sendApplicationRejectAcknowledgementModulus = 0;
        } else {
            this.sendApplicationRejectAcknowledgementModulus = sendApplicationRejectAcknowledgementModulus;
        }
    }

    public int getSendApplicationErrorAcknowledgementModulus() {
        return sendApplicationErrorAcknowledgementModulus;
    }

    public void setSendApplicationErrorAcknowledgementModulus(int sendApplicationErrorAcknowledgementModulus) {
        if (0 > sendApplicationErrorAcknowledgementModulus) {
            this.sendApplicationErrorAcknowledgementModulus = 0;
        } else {
            this.sendApplicationErrorAcknowledgementModulus = sendApplicationErrorAcknowledgementModulus;
        }
    }

    public Pattern getSendApplicationRejectAcknowledgementPattern() {
        return sendApplicationRejectAcknowledgementPattern;
    }

    public void setSendApplicationRejectAcknowledgementPattern(Pattern sendApplicationRejectAcknowledgementPattern) {
        this.sendApplicationRejectAcknowledgementPattern = sendApplicationRejectAcknowledgementPattern;
    }

    public Pattern getSendApplicationErrorAcknowledgementPattern() {
        return sendApplicationErrorAcknowledgementPattern;
    }

    public void setSendApplicationErrorAcknowledgementPattern(Pattern sendApplicationErrorAcknowledgementPattern) {
        this.sendApplicationErrorAcknowledgementPattern = sendApplicationErrorAcknowledgementPattern;
    }

    public ServerSocketThread getServerSocketThread() {
        return serverSocketThread;
    }

    public void setServerSocketThread(ServerSocketThread serverSocketThread) {
        this.serverSocketThread = serverSocketThread;
    }

    void closeConnection(Socket socket) {
        if (null != socket) {
            if (!socket.isClosed()) {
                try {
                    socket.shutdownInput();
                } catch (Exception ex) {
                    log.warn("Exception encountered shutting down the input stream on the client socket", ex);
                }

                try {
                    socket.shutdownOutput();
                } catch (Exception ex) {
                    log.warn("Exception encountered shutting down the output stream on the client socket", ex);
                }

                try {
                    socket.close();
                } catch (Exception ex) {
                    log.warn("Exception encountered closing the client socket", ex);
                }
            }
        }
    }

    void resetConnection(Socket socket) {
        if (null != socket) {
            try {
                socket.setSoLinger(true, 0);
            } catch (Exception ex) {
                log.warn("Exception encountered setting SO_LINGER to 0 on the socket to force a reset", ex);
            } finally {
                closeConnection(socket);
            }
        }

    }

    /**
     * Nested class to accept TCP connections
     */
    class ServerSocketThread extends Thread {
        Logger log = LoggerFactory.getLogger(this.getClass());

        ServerSocket serverSocket;

        String listenHost = "0.0.0.0";
        int listenPort;
        int backlog = 5;

        int acceptTimeout = 5000;

        boolean raiseExceptionOnAcceptTimeout;

        public ServerSocketThread() throws IOException {
            bind();
        }

        public ServerSocketThread(int listenPort) throws IOException {
            this.listenPort = listenPort;
            bind();
        }

        public ServerSocketThread(int listenPort, int backlog) throws IOException {
            this.listenPort = listenPort;
            this.backlog = backlog;
            bind();
        }

        public ServerSocketThread(String listenHost, int listenPort, int backlog) throws IOException {
            this.listenHost = listenHost;
            this.listenPort = listenPort;
            this.backlog = backlog;
            bind();
        }

        /**
         * Open the TCP Listener
         *
         * @throws IOException
         */
        private void bind() throws IOException {
            this.setDaemon(true);
            serverSocket = new ServerSocket();

            // Set TCP Parameters
            serverSocket.setSoTimeout(acceptTimeout);
            serverSocket.setReuseAddress(false);

            if (0 >= listenPort) {
                serverSocket.bind(null, backlog);
            } else {
                serverSocket.bind(new InetSocketAddress(this.listenHost, this.listenPort), backlog);
            }

            if (0 >= this.listenPort) {
                this.listenPort = serverSocket.getLocalPort();
            }


            log.info("Opened TCP Listener on port {}", serverSocket.getLocalPort());
        }

        /**
         * Accept TCP connections and create ClientSocketThreads for them
         */
        public void run() {
            log.info("Accepting connections on port {}", serverSocket.getLocalPort());
            this.setName("MllpServerResource$ServerSocketThread - " + serverSocket.getLocalSocketAddress().toString());
            while (isActive() && serverSocket.isBound()) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    clientSocket.setKeepAlive(true);
                    clientSocket.setTcpNoDelay(false);
                    clientSocket.setSoLinger(false, -1);
                    clientSocket.setSoTimeout(5000);
                    ClientSocketThread clientSocketThread = new ClientSocketThread(clientSocket);
                    clientSocketThread.setDaemon(true);
                    clientSocketThread.start();
                } catch (SocketTimeoutException timeoutEx) {
                    if (raiseExceptionOnAcceptTimeout) {
                        throw new MllpJUnitResourceTimeoutException("Timeout Accepting client connection", timeoutEx);
                    }
                    continue;
                } catch (IOException e) {
                    log.warn("IOException creating Client Socket");
                    try {
                        clientSocket.close();
                    } catch (IOException e1) {
                        log.warn("Exceptiong encountered closing client socket after attempting to accept connection");
                    }
                    throw new MllpJUnitResourceException("IOException creating Socket", e);
                }
            }
            log.info("No longer accepting connections - closing TCP Listener on port {}", serverSocket.getLocalPort());
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("Closed TCP Listener on port {}", serverSocket.getLocalPort());
        }

        public void shutdown() {
            this.interrupt();
        }

        public String getListenHost() {
            return listenHost;
        }

        public int getListenPort() {
            return listenPort;
        }

        public int getBacklog() {
            return backlog;
        }

        public int getAcceptTimeout() {
            return acceptTimeout;
        }

        /**
         * Enable/disable a timeout while waiting for a TCP connection, in milliseconds. With this option set to a
         * non-zero timeout, the ServerSocketThread will block for only this amount of time while waiting for a tcp
         * connection. If the timeout expires and raiseExceptionOnAcceptTimeout is set to true, a MllpJUnitResourceTimeoutException
         * is raised. Otherwise, the ServerSocketThread will continue to poll for new TCP connections.
         *
         * @param acceptTimeout the timeout in milliseconds - zero is interpreted as an infinite timeout
         */
        public void setAcceptTimeout(int acceptTimeout) {
            this.acceptTimeout = acceptTimeout;
        }

        public boolean isRaiseExceptionOnAcceptTimeout() {
            return raiseExceptionOnAcceptTimeout;
        }

        /**
         * Enable/Disable the generation of MllpJUnitResourceTimeoutException if the ServerSocket.accept()
         * call raises a SocketTimeoutException.
         *
         * @param raiseExceptionOnAcceptTimeout true enables exceptions on an accept timeout
         */
        public void setRaiseExceptionOnAcceptTimeout(boolean raiseExceptionOnAcceptTimeout) {
            this.raiseExceptionOnAcceptTimeout = raiseExceptionOnAcceptTimeout;
        }
    }

    /**
     * Nested class that handles the established TCP connections
     */
    class ClientSocketThread extends Thread {
        /*
        final char cCARRIAGE_RETURN = 13;
        final char cLINE_FEED = 10;
        final char cSEGMENT_DELIMITER = cCARRIAGE_RETURN;
        final String sMESSAGE_TERMINATOR = "" + cCARRIAGE_RETURN + cLINE_FEED;
        final byte bMLLP_ENVELOPE_START_OF_BLOCK = 0x0b;
        final byte bMLLP_ENVELOPE_END_OF_BLOCK = 0x1c;
        final byte bMLLP_ENVELOPE_END_OF_DATA = 0x0d;
        final int iEND_OF_TRANSMISSION = -1;
        */
        Logger log = LoggerFactory.getLogger(this.getClass());

        Socket clientSocket;

        int messageCounter;

        ClientSocketThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        /**
         * Receives HL7 messages and replies with HL7 Acknowledgements.
         *
         * The exact behaviour of this method is very configurable, allowing simulation of varies
         * error conditions.
         */
        public void run() {
            String localAddress = clientSocket.getLocalAddress().toString();
            String remoteAddress = clientSocket.getRemoteSocketAddress().toString();

            log.info("Handling Connection: {} -> {}", localAddress, remoteAddress);

            try {
                while (null != clientSocket && clientSocket.isConnected() && !clientSocket.isClosed()) {
                    InputStream instream = clientSocket.getInputStream();
                    String parsedHL7Message = getMessage(instream);

                    if (null != parsedHL7Message && parsedHL7Message.length() > 0) {
                        ++messageCounter;
                        if (disconnectBeforeAcknowledgement(messageCounter)) {
                            log.warn("Disconnecting before sending acknowledgement");
                            clientSocket.shutdownInput();
                            clientSocket.shutdownOutput();
                            clientSocket.close();
                            break;
                        }

                        String acknowledgmentMessage;

                        if (sendApplicationErrorAcknowledgement(messageCounter) || sendApplicationErrorAcknowledgement(parsedHL7Message)) {
                            acknowledgmentMessage = generateAcknowledgementMessage(parsedHL7Message, "AE");
                        } else if (sendApplicationRejectAcknowledgement(messageCounter) || sendApplicationRejectAcknowledgement(parsedHL7Message)) {
                            acknowledgmentMessage = generateAcknowledgementMessage(parsedHL7Message, "AR");
                        } else {
                            acknowledgmentMessage = generateAcknowledgementMessage(parsedHL7Message);

                        }
                        BufferedOutputStream outstream = new BufferedOutputStream(clientSocket.getOutputStream());

                        if (sendOutOfBandData(messageCounter)) {
                            byte[] outOfBandDataBytes = "Out Of Band Hl7MessageGenerator".getBytes();
                            outstream.write(outOfBandDataBytes, 0, outOfBandDataBytes.length);

                        }
                        if (excludeStartOfBlock(messageCounter)) {
                            log.warn("NOT sending bMLLP_ENVELOPE_START_OF_BLOCK");
                        } else {
                            outstream.write(START_OF_BLOCK);
                        }

                        if (excludeAcknowledgement(messageCounter)) {
                            log.info("NOT sending Acknowledgement body");
                        } else {
                            log.debug("Buffering Acknowledgement\n\t{}", acknowledgmentMessage.replace('\r', '\n'));
                            byte[] ackBytes = acknowledgmentMessage.getBytes();
                            outstream.write(ackBytes, 0, ackBytes.length);
                        }

                        if (excludeEndOfBlock(messageCounter)) {
                            log.warn("NOT sending bMLLP_ENVELOPE_END_OF_BLOCK");
                        } else {
                            outstream.write(END_OF_BLOCK);
                        }

                        if (excludeEndOfData(messageCounter)) {
                            log.warn("NOT sending bMLLP_ENVELOPE_END_OF_DATA");
                        } else {
                            outstream.write(END_OF_DATA);
                        }

                        log.debug("Writing Acknowledgement\n\t{}", acknowledgmentMessage.replace('\r', '\n'));
                        outstream.flush();

                        if (disconnectAfterAcknowledgement(messageCounter)) {
                            log.info("Closing Client");
                            clientSocket.shutdownInput();
                            clientSocket.shutdownOutput();
                            clientSocket.close();
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                String errorMessage = "Error whiling reading and writing to clientSocket";
                log.error(errorMessage, e);
                throw new MllpJUnitResourceException(errorMessage, e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    String errorMessage = "Error whiling attempting to close to client Socket";
                    log.error(errorMessage, e);
                    throw new MllpJUnitResourceException(errorMessage, e);
                }
            }

            log.info("Connection Finished: {} -> {}", localAddress, remoteAddress);
        }

        /**
         * Read a MLLP-Framed message
         *
         * @param anInputStream source input stream
         * @return the MLLP payload
         * @throws IOException when the underlying Java Socket calls raise these exceptions
         */
        // TODO:  Enhance this to detect non-HL7 data (i.e. look for MSH after START_OF_BLOCK)
        public String getMessage(InputStream anInputStream) throws IOException {
            try {
                // TODO:  Enhance this to read a bunch of characters and log, rather than log them one at a time
                boolean waitingForStartOfBlock = true;
                while (waitingForStartOfBlock) {
                    int potentialStartCharacter = anInputStream.read();
                    switch (potentialStartCharacter) {
                    case END_OF_STREAM:
                        return null;
                    case START_OF_BLOCK:
                        waitingForStartOfBlock = false;
                        break;
                    default:
                        log.warn("START_OF_BLOCK character has not been received.  Out-of-band character received: {}", potentialStartCharacter);
                    }
                }
            } catch (SocketException socketEx) {
                log.error("Unable to read from socket stream when expected bMLLP_ENVELOPE_START_OF_BLOCK - resetting connection ", socketEx);
                resetConnection(clientSocket);
                return null;
            }

            boolean endOfMessage = false;
            StringBuilder parsedMessage = new StringBuilder(anInputStream.available() + 10);
            while (!endOfMessage) {
                int characterReceived = anInputStream.read();

                switch (characterReceived) {
                case START_OF_BLOCK:
                    log.error("Received START_OF_BLOCK before END_OF_DATA.  Discarding data: {}", parsedMessage.toString());
                    return null;
                case END_OF_STREAM:
                    log.error("Received END_OF_STREAM without END_OF_DATA.  Discarding data: {}", parsedMessage.toString());
                    return null;
                case END_OF_BLOCK:
                    characterReceived = anInputStream.read();
                    if (characterReceived != END_OF_DATA) {
                        log.error("Received {} when expecting END_OF_DATA after END_OF_BLOCK.  Discarding Hl7MessageGenerator: {}",
                                characterReceived, parsedMessage.toString());
                        return null;
                    }
                    endOfMessage = true;
                    break;
                default:
                    parsedMessage.append((char) characterReceived);
                }

            }

            return parsedMessage.toString();
        }

        /**
         * Generates a HL7 Application Accept Acknowledgement
         *
         * @param hl7Message HL7 message that is being acknowledged
         * @return a HL7 Application Accept Acknowlegdement
         */
        private String generateAcknowledgementMessage(String hl7Message) {
            return generateAcknowledgementMessage(hl7Message, "AA");
        }

        /**
         * Generates a HL7 Application Acknowledgement
         *
         * @param hl7Message          HL7 message that is being acknowledged
         * @param acknowledgementCode AA, AE or AR
         * @return a HL7 Application Acknowledgement
         */
        private String generateAcknowledgementMessage(String hl7Message, String acknowledgementCode) {
            final String defaulNackMessage =
                    "MSH|^~\\&|||||||NACK||P|2.2" + SEGMENT_DELIMITER
                            + "MSA|AR|" + SEGMENT_DELIMITER
                            + MESSAGE_TERMINATOR;

            if (hl7Message == null) {
                log.error("Invalid HL7 message for parsing operation. Please check your inputs");
                return defaulNackMessage;
            }

            if (!("AA".equals(acknowledgementCode) || "AE".equals(acknowledgementCode) || "AR".equals(acknowledgementCode))) {
                throw new IllegalArgumentException("Acknowledgemnt Code must be AA, AE or AR: " + acknowledgementCode);
            }

            String messageControlId;

            int endOfMshSegment = hl7Message.indexOf(SEGMENT_DELIMITER);
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
                            .append("ACK")
                            .append(mshFields[8].substring(3));
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
                    ackBuilder.append(SEGMENT_DELIMITER);

                    // Build the MSA Segment
                    ackBuilder
                            .append("MSA").append(fieldSeparator)
                            .append(acknowledgementCode).append(fieldSeparator)
                            .append(mshFields[9]).append(fieldSeparator)
                            .append(SEGMENT_DELIMITER);

                    // Terminate the message
                    ackBuilder.append(MESSAGE_TERMINATOR);

                    return ackBuilder.toString();
                }
            } else {
                log.error("Failed to find the end of the  MSH Segment");
            }

            return null;
        }
    }


}
