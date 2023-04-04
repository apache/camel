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
package org.apache.camel.test.junit.rule.mllp;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MLLP Test Server packaged as a JUnit Rule
 *
 * The server can be configured to simulate a large number of error conditions.
 */
public class MllpServerResource implements BeforeEachCallback, AfterEachCallback {

    Logger log = LoggerFactory.getLogger(this.getClass());

    String listenHost;
    int listenPort;
    int backlog = 5;

    int counter = 1;

    boolean active = true;

    int delayBeforeStartOfBlock;
    int delayBeforeAcknowledgement;
    int delayDuringAcknowledgement;
    int delayAfterAcknowledgement;
    int delayAfterEndOfBlock;

    int excludeStartOfBlockModulus;
    int excludeEndOfBlockModulus;
    int excludeEndOfDataModulus;

    int excludeAcknowledgementModulus;

    int sendOutOfBandDataModulus;

    int closeSocketBeforeAcknowledgementModulus;
    int closeSocketAfterAcknowledgementModulus;

    int resetSocketBeforeAcknowledgementModulus;
    int resetSocketAfterAcknowledgementModulus;

    int sendApplicationRejectAcknowledgementModulus;
    int sendApplicationErrorAcknowledgementModulus;

    Pattern sendApplicationRejectAcknowledgementPattern;
    Pattern sendApplicationErrorAcknowledgementPattern;

    String acknowledgementString;

    AcceptSocketThread acceptSocketThread;

    public MllpServerResource() {
    }

    public MllpServerResource(int listenPort) {
        this.listenPort = listenPort;
    }

    public MllpServerResource(int listenPort, int backlog) {
        this.listenPort = listenPort;
        this.backlog = backlog;
    }

    public MllpServerResource(String listenHost, int listenPort) {
        this.listenHost = listenHost;
        this.listenPort = listenPort;
    }

    public MllpServerResource(String listenHost, int listenPort, int backlog) {
        this.listenHost = listenHost;
        this.listenPort = listenPort;
        this.backlog = backlog;
    }

    public String getListenHost() {
        return listenHost;
    }

    public void setListenHost(String listenHost) {
        this.listenHost = listenHost;
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

        if (null != listenHost) {
            acceptSocketThread = new AcceptSocketThread(listenHost, listenPort, backlog);
        } else {
            acceptSocketThread = new AcceptSocketThread(listenPort, backlog);
            listenHost = acceptSocketThread.getListenHost();
        }

        if (0 >= listenPort) {
            listenPort = acceptSocketThread.listenPort;
        }

        acceptSocketThread.setDaemon(true);
        acceptSocketThread.start();
    }

    public void shutdown() {
        this.active = false;
        if (acceptSocketThread != null) {
            acceptSocketThread.shutdown();
            acceptSocketThread = null;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        startup();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        shutdown();
    }

    public void interrupt() {
        acceptSocketThread.interrupt();
    }

    public int getDelayBeforeStartOfBlock() {
        return delayBeforeStartOfBlock;
    }

    public void setDelayBeforeStartOfBlock(int delayBeforeStartOfBlock) {
        this.delayBeforeStartOfBlock = delayBeforeStartOfBlock;
    }

    public int getDelayBeforeAcknowledgement() {
        return delayBeforeAcknowledgement;
    }

    public void setDelayBeforeAcknowledgement(int delayBeforeAcknowledgement) {
        this.delayBeforeAcknowledgement = delayBeforeAcknowledgement;
    }

    public int getDelayDuringAcknowledgement() {
        return delayDuringAcknowledgement;
    }

    public void setDelayDuringAcknowledgement(int delayDuringAcknowledgement) {
        this.delayDuringAcknowledgement = delayDuringAcknowledgement;
    }

    public int getDelayAfterAcknowledgement() {
        return delayAfterAcknowledgement;
    }

    public void setDelayAfterAcknowledgement(int delayAfterAcknowledgement) {
        this.delayAfterAcknowledgement = delayAfterAcknowledgement;
    }

    public int getDelayAfterEndOfBlock() {
        return delayAfterEndOfBlock;
    }

    public void setDelayAfterEndOfBlock(int delayAfterEndOfBlock) {
        this.delayAfterEndOfBlock = delayAfterEndOfBlock;
    }

    public boolean sendApplicationRejectAcknowledgement(String hl7Message) {
        return evaluatePattern(hl7Message, this.sendApplicationErrorAcknowledgementPattern);
    }

    public boolean sendApplicationErrorAcknowledgement(String hl7Message) {
        return evaluatePattern(hl7Message, this.sendApplicationRejectAcknowledgementPattern);
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

    public boolean closeSocketBeforeAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, closeSocketBeforeAcknowledgementModulus);
    }

    public boolean closeSocketAfterAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, closeSocketAfterAcknowledgementModulus);
    }

    public boolean resetSocketBeforeAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, resetSocketBeforeAcknowledgementModulus);
    }

    public boolean resetSocketAfterAcknowledgement(int messageCount) {
        return evaluateModulus(messageCount, resetSocketAfterAcknowledgementModulus);
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
                return messageCount % modulus == 0;
        }
    }

    private boolean evaluatePattern(String hl7Message, Pattern pattern) {
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
     * Set the modulus used to determine when to include the START_OF_BLOCK portion of the MLLP Envelope.
     * <p/>
     * If this value is less than or equal to 0, the START_OF_BLOCK portion of the MLLP Envelope will always be
     * included. If the value is 1, the START_OF_BLOCK portion of the MLLP Envelope will never be included. Otherwise,
     * if the result of evaluating message availableByteCount % value is greater than 0, the START_OF_BLOCK portion of
     * the MLLP Envelope will not be included. Effectively leaving the START_OF_BLOCK portion of the MLLP Envelope out
     * of every n-th message.
     *
     * @param excludeStartOfBlockModulus exclude on every n-th message 0 => Never excluded 1 => Always excluded
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

    public int getCloseSocketBeforeAcknowledgementModulus() {
        return closeSocketBeforeAcknowledgementModulus;
    }

    public void setCloseSocketBeforeAcknowledgementModulus(int closeSocketBeforeAcknowledgementModulus) {
        if (0 > closeSocketBeforeAcknowledgementModulus) {
            this.closeSocketBeforeAcknowledgementModulus = 0;
        } else {
            this.closeSocketBeforeAcknowledgementModulus = closeSocketBeforeAcknowledgementModulus;
        }
    }

    public int getCloseSocketAfterAcknowledgementModulus() {
        return closeSocketAfterAcknowledgementModulus;
    }

    public void setCloseSocketAfterAcknowledgementModulus(int closeSocketAfterAcknowledgementModulus) {
        if (0 > closeSocketAfterAcknowledgementModulus) {
            this.closeSocketAfterAcknowledgementModulus = 0;
        } else {
            this.closeSocketAfterAcknowledgementModulus = closeSocketAfterAcknowledgementModulus;
        }
    }

    public int getResetSocketBeforeAcknowledgementModulus() {
        return resetSocketBeforeAcknowledgementModulus;
    }

    public void setResetSocketBeforeAcknowledgementModulus(int resetSocketBeforeAcknowledgementModulus) {
        if (0 > resetSocketBeforeAcknowledgementModulus) {
            this.resetSocketBeforeAcknowledgementModulus = 0;
        } else {
            this.resetSocketBeforeAcknowledgementModulus = resetSocketBeforeAcknowledgementModulus;
        }
    }

    public int getResetSocketAfterAcknowledgementModulus() {
        return resetSocketAfterAcknowledgementModulus;
    }

    public void setResetSocketAfterAcknowledgementModulus(int resetSocketAfterAcknowledgementModulus) {
        if (0 > resetSocketAfterAcknowledgementModulus) {
            this.resetSocketAfterAcknowledgementModulus = 0;
        } else {
            this.resetSocketAfterAcknowledgementModulus = resetSocketAfterAcknowledgementModulus;
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

    public String getAcknowledgementString() {
        return acknowledgementString;
    }

    public void setAcknowledgementString(String acknowledgementString) {
        this.acknowledgementString = acknowledgementString;
    }

    public AcceptSocketThread getAcceptSocketThread() {
        return acceptSocketThread;
    }

    public void setAcceptSocketThread(AcceptSocketThread acceptSocketThread) {
        this.acceptSocketThread = acceptSocketThread;
    }

    public void checkClientConnections() {
        if (acceptSocketThread != null) {
            acceptSocketThread.checkClientConnections();
        }
    }

    public void closeClientConnections() {
        if (acceptSocketThread != null) {
            acceptSocketThread.closeClientConnections();
        }
    }

    public void resetClientConnections() {
        if (acceptSocketThread != null) {
            acceptSocketThread.resetClientConnections();
        }
    }

    /**
     * Generates a HL7 Application Acknowledgement
     *
     * @param  hl7Message          HL7 message that is being acknowledged
     * @param  acknowledgementCode AA, AE or AR
     *
     * @return                     a HL7 Application Acknowledgement
     */
    protected String generateAcknowledgement(String hl7Message, String acknowledgementCode) {
        final String defaulNackMessage = "MSH|^~\\&|||||||NACK||P|2.2" + MllpProtocolConstants.SEGMENT_DELIMITER
                                         + "MSA|AR|" + MllpProtocolConstants.SEGMENT_DELIMITER
                                         + MllpProtocolConstants.MESSAGE_TERMINATOR;

        if (hl7Message == null) {
            log.error("Invalid HL7 message for parsing operation. Please check your inputs");
            return defaulNackMessage;
        }

        if (!("AA".equals(acknowledgementCode) || "AE".equals(acknowledgementCode) || "AR".equals(acknowledgementCode))) {
            throw new IllegalArgumentException("Acknowledgemnt Code must be AA, AE or AR: " + acknowledgementCode);
        }

        int endOfMshSegment = hl7Message.indexOf(MllpProtocolConstants.SEGMENT_DELIMITER);
        if (-1 != endOfMshSegment) {
            String mshSegment = hl7Message.substring(0, endOfMshSegment);
            char fieldSeparator = mshSegment.charAt(3);
            String fieldSeparatorPattern = Pattern.quote(String.valueOf(fieldSeparator));
            String[] mshFields = mshSegment.split(fieldSeparatorPattern);
            if (mshFields.length == 0) {
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
                    while (emptyFieldIndex >= 1
                            && mshSegment.charAt(emptyFieldIndex) == mshSegment.charAt(emptyFieldIndex - 1)) {
                        ackBuilder.append(fieldSeparator);
                        --emptyFieldIndex;
                    }
                }
                ackBuilder.append(MllpProtocolConstants.SEGMENT_DELIMITER);

                // Build the MSA Segment
                ackBuilder
                        .append("MSA").append(fieldSeparator)
                        .append(acknowledgementCode).append(fieldSeparator)
                        .append(mshFields[9]).append(fieldSeparator)
                        .append(MllpProtocolConstants.SEGMENT_DELIMITER);

                // Terminate the message
                ackBuilder.append(MllpProtocolConstants.MESSAGE_TERMINATOR);

                return ackBuilder.toString();
            }
        } else {
            log.error("Failed to find the end of the  MSH Segment");
        }

        return null;
    }

    /**
     * Nested class to accept TCP connections
     */
    class AcceptSocketThread extends Thread {
        final long bindTimeout = 30000;
        final long bindRetryDelay = 1000;
        Logger log = LoggerFactory.getLogger(this.getClass());
        ServerSocket serverSocket;
        List<ClientSocketThread> clientSocketThreads = new LinkedList<>();

        String listenHost;
        int listenPort;
        int backlog = 5;

        int acceptTimeout = 5000;

        boolean raiseExceptionOnAcceptTimeout;

        AcceptSocketThread() throws IOException {
            bind();
        }

        AcceptSocketThread(int listenPort) throws IOException {
            this.listenPort = listenPort;
            bind();
        }

        AcceptSocketThread(int listenPort, int backlog) throws IOException {
            this.listenPort = listenPort;
            this.backlog = backlog;
            bind();
        }

        AcceptSocketThread(String listenHost, int listenPort, int backlog) throws IOException {
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
            serverSocket.setReuseAddress(true);

            InetSocketAddress listenAddress;
            if (null != this.listenHost) {
                listenAddress = new InetSocketAddress(this.listenHost, this.listenPort);
            } else {
                listenAddress = new InetSocketAddress(this.listenPort);
            }

            long startTicks = System.currentTimeMillis();
            while (!serverSocket.isBound()) {
                try {
                    serverSocket.bind(listenAddress, backlog);
                } catch (BindException bindEx) {
                    if (System.currentTimeMillis() < startTicks + bindTimeout) {
                        log.warn("Unable to bind to {} - retrying in {} milliseconds", listenAddress, bindRetryDelay);
                        try {
                            Thread.sleep(bindRetryDelay);
                        } catch (InterruptedException interruptedEx) {
                            log.error("Wait for bind retry was interrupted - rethrowing BindException");
                            throw bindEx;
                        }
                    }
                }
            }

            if (0 >= this.listenPort) {
                this.listenPort = serverSocket.getLocalPort();
            }

            log.info("Opened TCP Listener on port {}", serverSocket.getLocalPort());
        }

        void checkClientConnections() {
            if (clientSocketThreads != null) {
                for (ClientSocketThread clientSocketThread : clientSocketThreads) {
                    clientSocketThread.checkConnection();
                }
            }
        }

        void closeClientConnections() {
            if (clientSocketThreads != null) {
                for (ClientSocketThread clientSocketThread : clientSocketThreads) {
                    clientSocketThread.closeConnection();
                }
            }
        }

        void resetClientConnections() {
            if (clientSocketThreads != null) {
                for (ClientSocketThread clientSocketThread : clientSocketThreads) {
                    clientSocketThread.resetConnection();
                }
            }
        }

        /**
         * Accept TCP connections and create ClientSocketThreads for them
         */
        @Override
        public void run() {
            log.info("Accepting connections on port {}", serverSocket.getLocalPort());
            this.setName("MllpServerResource$AcceptSocketThread - " + serverSocket.getLocalSocketAddress().toString());
            while (!isInterrupted() && serverSocket.isBound() && !serverSocket.isClosed()) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                } catch (SocketTimeoutException timeoutEx) {
                    if (raiseExceptionOnAcceptTimeout) {
                        throw new MllpJUnitResourceTimeoutException("Timeout Accepting client connection", timeoutEx);
                    }
                    log.warn("Timeout waiting for client connection");
                } catch (SocketException socketEx) {
                    log.debug("SocketException encountered accepting client connection - ignoring", socketEx);
                    if (null == clientSocket) {
                        continue;
                    } else if (!clientSocket.isClosed()) {
                        try {
                            clientSocket.setSoLinger(true, 0);
                        } catch (SocketException soLingerEx) {
                            log.warn(
                                    "Ignoring SocketException encountered when setting SO_LINGER in preparation of resetting client Socket",
                                    soLingerEx);
                        }
                        try {
                            clientSocket.close();
                        } catch (IOException ioEx) {
                            log.warn("Ignoring IOException encountered when resetting client Socket", ioEx);
                        }
                        continue;
                    } else {
                        throw new MllpJUnitResourceException(
                                "Unexpected SocketException encountered accepting client connection", socketEx);
                    }
                } catch (Exception ex) {
                    throw new MllpJUnitResourceException("Unexpected exception encountered accepting client connection", ex);
                }
                if (null != clientSocket) {
                    try {
                        clientSocket.setKeepAlive(true);
                        clientSocket.setTcpNoDelay(false);
                        clientSocket.setSoLinger(false, -1);
                        clientSocket.setSoTimeout(5000);
                        ClientSocketThread clientSocketThread = new ClientSocketThread(clientSocket);
                        clientSocketThread.setDaemon(true);
                        clientSocketThread.start();
                        clientSocketThreads.add(clientSocketThread);
                    } catch (Exception unexpectedEx) {
                        log.warn("Unexpected exception encountered configuring client socket");
                        try {
                            clientSocket.close();
                        } catch (IOException ingoreEx) {
                            log.warn("Exceptiong encountered closing client socket after attempting to accept connection",
                                    ingoreEx);
                        }
                        throw new MllpJUnitResourceException(
                                "Unexpected exception encountered configuring client socket", unexpectedEx);
                    }
                }
            }
            log.info("No longer accepting connections - closing TCP Listener on port {}", serverSocket.getLocalPort());
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("I/O exception closing the server socket: {}", e.getMessage(), e);
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
         * non-zero timeout, the AcceptSocketThread will block for only this amount of time while waiting for a tcp
         * connection. If the timeout expires and raiseExceptionOnAcceptTimeout is set to true, a
         * MllpJUnitResourceTimeoutException is raised. Otherwise, the AcceptSocketThread will continue to poll for new
         * TCP connections.
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
         * Enable/Disable the generation of MllpJUnitResourceTimeoutException if the ServerSocket.accept() call raises a
         * SocketTimeoutException.
         *
         * @param raiseExceptionOnAcceptTimeout true enables exceptions on an accept timeout
         */
        public void setRaiseExceptionOnAcceptTimeout(boolean raiseExceptionOnAcceptTimeout) {
            this.raiseExceptionOnAcceptTimeout = raiseExceptionOnAcceptTimeout;
        }

        public void close() {

        }

        @Override
        public void interrupt() {
            for (ClientSocketThread clientSocketThread : clientSocketThreads) {
                clientSocketThread.interrupt();
            }

            if (serverSocket != null && serverSocket.isBound() && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (Exception ex) {
                    log.warn("Exception encountered closing server socket on interrupt", ex);
                }
            }
            super.interrupt();
        }

    }

    /**
     * Nested class that handles the established TCP connections
     */
    class ClientSocketThread extends Thread {
        Logger log = LoggerFactory.getLogger(this.getClass());

        Socket clientSocket;

        int messageCounter;

        ClientSocketThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        void checkConnection() {
            if (clientSocket == null) {
                throw new MllpJUnitResourceException("checkConnection failed - clientSocket is null");
            }

            if (clientSocket.isClosed()) {
                throw new MllpJUnitResourceException("checkConnection failed - clientSocket is closed");
            }

            if (!clientSocket.isConnected()) {
                throw new MllpJUnitResourceException("checkConnection failed - clientSocket is not connected");
            }

            try {
                if (MllpProtocolConstants.END_OF_STREAM == clientSocket.getInputStream().read()) {
                    throw new MllpJUnitResourceException("checkConnection failed - read() returned END_OF_STREAM");
                }
            } catch (IOException ioEx) {
                throw new MllpJUnitResourceException("checkConnection failed - read() failure", ioEx);
            }
        }

        void closeConnection() {
            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                } catch (IOException ioEx) {
                    log.warn("Ignoring IOException encountered when closing client Socket", ioEx);
                } finally {
                    clientSocket = null;
                }
            }
        }

        void resetConnection() {
            if (clientSocket != null && !clientSocket.isClosed()) {
                try {
                    clientSocket.setSoLinger(true, 0);
                } catch (SocketException socketEx) {
                    log.warn(
                            "Ignoring SocketException encountered when setting SO_LINGER in preparation of resetting client Socket",
                            socketEx);
                }
                try {
                    clientSocket.close();
                } catch (IOException ioEx) {
                    log.warn("Ignoring IOException encountered when resetting client Socket", ioEx);
                } finally {
                    clientSocket = null;
                }
            }
        }

        /**
         * Read a MLLP-Framed message
         *
         * @param  anInputStream source input stream
         *
         * @return               the MLLP payload
         *
         * @throws IOException   when the underlying Java Socket calls raise these exceptions
         */
        public String getMessage(InputStream anInputStream) throws IOException {
            try {
                boolean waitingForStartOfBlock = true;
                while (waitingForStartOfBlock) {
                    int potentialStartCharacter = anInputStream.read();
                    switch (potentialStartCharacter) {
                        case MllpProtocolConstants.END_OF_STREAM:
                            return null;
                        case MllpProtocolConstants.START_OF_BLOCK:
                            waitingForStartOfBlock = false;
                            break;
                        default:
                            log.warn("START_OF_BLOCK character has not been received.  Out-of-band character received: {}",
                                    potentialStartCharacter);
                    }
                }
            } catch (SocketException socketEx) {
                if (clientSocket != null) {
                    if (clientSocket.isClosed()) {
                        log.info("Client socket closed while waiting for START_OF_BLOCK");
                    } else if (clientSocket.isConnected()) {
                        log.info("SocketException encountered while waiting for START_OF_BLOCK");
                        resetConnection();
                    } else {
                        log.error("Unable to read from socket stream when expected START_OF_BLOCK - resetting connection ",
                                socketEx);
                        resetConnection();
                    }
                }
                return null;
            }

            boolean endOfMessage = false;
            StringBuilder parsedMessage = new StringBuilder(anInputStream.available() + 10);
            while (!endOfMessage) {
                int characterReceived = anInputStream.read();

                switch (characterReceived) {
                    case MllpProtocolConstants.START_OF_BLOCK:
                        log.error("Received START_OF_BLOCK before END_OF_DATA.  Discarding data: {}", parsedMessage);
                        return null;
                    case MllpProtocolConstants.END_OF_STREAM:
                        log.error("Received END_OF_STREAM without END_OF_DATA.  Discarding data: {}", parsedMessage);
                        return null;
                    case MllpProtocolConstants.END_OF_BLOCK:
                        characterReceived = anInputStream.read();
                        if (characterReceived != MllpProtocolConstants.END_OF_DATA) {
                            log.error(
                                    "Received {} when expecting END_OF_DATA after END_OF_BLOCK.  Discarding Hl7TestMessageGenerator: {}",
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
         * @param  hl7Message HL7 message that is being acknowledged
         *
         * @return            a HL7 Application Accept Acknowlegdement
         */
        private String generateAcknowledgementMessage(String hl7Message) {
            return generateAcknowledgementMessage(hl7Message, "AA");
        }

        /**
         * Generates a HL7 Application Acknowledgement
         *
         * @param  hl7Message          HL7 message that is being acknowledged
         * @param  acknowledgementCode AA, AE or AR
         *
         * @return                     a HL7 Application Acknowledgement
         */
        private String generateAcknowledgementMessage(String hl7Message, String acknowledgementCode) {
            return generateAcknowledgement(hl7Message, acknowledgementCode);
        }

        private void uncheckedSleep(long milliseconds) {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException e) {
                log.warn("Sleep interrupted", e);
            }

        }

        /**
         * Receives HL7 messages and replies with HL7 Acknowledgements.
         *
         * The exact behaviour of this method is very configurable, allowing simulation of varies error conditions.
         */
        @Override
        public void run() {
            String localAddress = clientSocket.getLocalAddress().toString();
            String remoteAddress = clientSocket.getRemoteSocketAddress().toString();

            log.info("Handling Connection: {} -> {}", localAddress, remoteAddress);

            try {
                while (!isInterrupted() && null != clientSocket && clientSocket.isConnected() && !clientSocket.isClosed()) {
                    InputStream instream;
                    try {
                        instream = clientSocket.getInputStream();
                    } catch (IOException ioEx) {
                        if (clientSocket.isClosed()) {
                            log.debug("Client socket was closed - ignoring exception");
                            break;
                        } else {
                            throw new MllpJUnitResourceException("Unexpected IOException encounted getting input stream", ioEx);
                        }
                    } catch (Exception unexpectedEx) {
                        throw new MllpJUnitResourceException(
                                "Unexpected exception encounted getting input stream", unexpectedEx);
                    }
                    String parsedHL7Message;
                    try {
                        parsedHL7Message = getMessage(instream);
                    } catch (SocketTimeoutException timeoutEx) {
                        log.info("Waiting for message from client");
                        continue;
                    }

                    if (null != parsedHL7Message && parsedHL7Message.length() > 0) {
                        ++messageCounter;
                        if (closeSocketBeforeAcknowledgement(messageCounter)) {
                            log.warn("Closing socket before sending acknowledgement");
                            clientSocket.shutdownInput();
                            clientSocket.shutdownOutput();
                            clientSocket.close();
                            break;
                        }
                        if (resetSocketBeforeAcknowledgement(messageCounter)) {
                            log.warn("Resetting socket before sending acknowledgement");
                            try {
                                clientSocket.setSoLinger(true, 0);
                            } catch (IOException ioEx) {
                                log.warn("Ignoring IOException encountered setting SO_LINGER when prepareing to reset socket",
                                        ioEx);
                            }
                            clientSocket.shutdownInput();
                            clientSocket.shutdownOutput();
                            clientSocket.close();
                            break;
                        }

                        String acknowledgmentMessage;

                        if (acknowledgementString == null) {
                            if (sendApplicationErrorAcknowledgement(messageCounter)
                                    || sendApplicationErrorAcknowledgement(parsedHL7Message)) {
                                acknowledgmentMessage = generateAcknowledgementMessage(parsedHL7Message, "AE");
                            } else if (sendApplicationRejectAcknowledgement(messageCounter)
                                    || sendApplicationRejectAcknowledgement(parsedHL7Message)) {
                                acknowledgmentMessage = generateAcknowledgementMessage(parsedHL7Message, "AR");
                            } else {
                                acknowledgmentMessage = generateAcknowledgementMessage(parsedHL7Message);
                            }
                        } else {
                            acknowledgmentMessage = acknowledgementString;
                        }

                        BufferedOutputStream outstream = new BufferedOutputStream(clientSocket.getOutputStream());

                        if (sendOutOfBandData(messageCounter)) {
                            byte[] outOfBandDataBytes = "Out Of Band Hl7TestMessageGenerator".getBytes();
                            outstream.write(outOfBandDataBytes, 0, outOfBandDataBytes.length);
                        }

                        if (excludeStartOfBlock(messageCounter)) {
                            log.warn("NOT sending START_OF_BLOCK");
                        } else {
                            outstream.write(MllpProtocolConstants.START_OF_BLOCK);
                            if (delayBeforeStartOfBlock > 0) {
                                uncheckedSleep(delayBeforeStartOfBlock);
                                uncheckedFlush(outstream);
                            }
                        }

                        if (excludeAcknowledgement(messageCounter)) {
                            log.info("NOT sending Acknowledgement body");
                        } else {
                            if (delayBeforeAcknowledgement > 0) {
                                uncheckedSleep(delayBeforeAcknowledgement);
                            }
                            log.debug("Buffering Acknowledgement\n\t{}", acknowledgmentMessage.replace('\r', '\n'));
                            byte[] ackBytes = acknowledgmentMessage.getBytes();
                            if (delayDuringAcknowledgement > 0) {
                                int firstHalf = ackBytes.length / 2;
                                outstream.write(ackBytes, 0, firstHalf);
                                uncheckedFlush(outstream);
                                uncheckedSleep(delayDuringAcknowledgement);
                                outstream.write(ackBytes, firstHalf, ackBytes.length - firstHalf);
                                uncheckedFlush(outstream);
                            } else {
                                outstream.write(ackBytes, 0, ackBytes.length);
                            }
                            if (delayAfterAcknowledgement > 0) {
                                uncheckedFlush(outstream);
                                uncheckedSleep(delayAfterAcknowledgement);
                            }
                        }

                        if (excludeEndOfBlock(messageCounter)) {
                            log.warn("NOT sending END_OF_BLOCK");
                        } else {
                            outstream.write(MllpProtocolConstants.END_OF_BLOCK);
                            if (delayAfterEndOfBlock > 0) {
                                uncheckedFlush(outstream);
                                uncheckedSleep(delayAfterEndOfBlock);
                            }
                        }

                        if (excludeEndOfData(messageCounter)) {
                            log.warn("NOT sending END_OF_DATA");
                        } else {
                            outstream.write(MllpProtocolConstants.END_OF_DATA);
                        }

                        log.debug("Writing Acknowledgement\n\t{}", acknowledgmentMessage.replace('\r', '\n'));
                        uncheckedFlush(outstream);

                        if (closeSocketAfterAcknowledgement(messageCounter)) {
                            log.info("Closing Client");
                            clientSocket.shutdownInput();
                            clientSocket.shutdownOutput();
                            clientSocket.close();
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                String errorMessage = "Error while reading and writing from clientSocket";
                log.error(errorMessage, e);
                throw new MllpJUnitResourceException(errorMessage, e);
            } finally {
                if (clientSocket != null) {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        String errorMessage = "Error while attempting to close to client Socket";
                        log.error(errorMessage, e);
                        throw new MllpJUnitResourceException(errorMessage, e);
                    }
                }
            }

            log.debug("Client Connection Finished: {} -> {}", localAddress, remoteAddress);
        }

        private void uncheckedFlush(OutputStream outputStream) {
            try {
                outputStream.flush();
            } catch (IOException e) {
                log.warn("Ignoring exception caught while flushing output stream", e);
            }
        }

        @Override
        public void interrupt() {
            if (clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed()) {
                try {
                    clientSocket.close();
                } catch (Exception ex) {
                    log.warn("Exception encountered closing client socket on interrput", ex);
                }
            }
            super.interrupt();
        }

    }

}
