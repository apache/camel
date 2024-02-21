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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MLLP Test Client packaged as a JUnit Rule
 *
 * The client can be configured to simulate a large number of error conditions.
 */
public class MllpClientResource implements BeforeEachCallback, AfterEachCallback {

    static final char START_OF_BLOCK = 0x0b;
    static final char END_OF_BLOCK = 0x1c;
    static final char END_OF_DATA = 0x0d;
    static final int END_OF_STREAM = -1;

    Logger log = LoggerFactory.getLogger(this.getClass());

    Socket clientSocket;
    InputStream inputStream;
    OutputStream outputStream;

    String mllpHost = "0.0.0.0";
    int mllpPort = -1;

    boolean sendStartOfBlock = true;
    boolean sendEndOfBlock = true;
    boolean sendEndOfData = true;

    int connectTimeout = 5000;
    int soTimeout = 5000;
    boolean reuseAddress;
    boolean tcpNoDelay = true;

    DisconnectMethod disconnectMethod = DisconnectMethod.CLOSE;

    /**
     * Use this constructor to avoid having the connection started by JUnit (since the port is still -1)
     */
    public MllpClientResource() {

    }

    public MllpClientResource(int port) {
        this.mllpPort = port;
    }

    public MllpClientResource(String host, int port) {
        this.mllpHost = host;
        this.mllpPort = port;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (0 < mllpPort) {
            this.connect();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        this.close();
    }

    public void close() {
        try {
            if (null != inputStream) {
                clientSocket.close();
            }
        } catch (IOException e) {
            log.warn(String.format("Exception encountered closing connection to %s:%s", mllpHost, mllpPort), e);
        } finally {
            inputStream = null;
            outputStream = null;
            clientSocket = null;
        }
        return;
    }

    public void connect() {
        this.connect(this.connectTimeout);
    }

    public void connect(int connectTimeout) {
        try {
            clientSocket = new Socket();

            clientSocket.connect(new InetSocketAddress(mllpHost, mllpPort), connectTimeout);

            clientSocket.setSoTimeout(soTimeout);
            clientSocket.setSoLinger(false, -1);
            clientSocket.setReuseAddress(reuseAddress);
            clientSocket.setTcpNoDelay(tcpNoDelay);

            inputStream = clientSocket.getInputStream();
            outputStream = new BufferedOutputStream(clientSocket.getOutputStream(), 2048);
        } catch (IOException e) {
            String errorMessage = String.format("Unable to establish connection to %s:%s", mllpHost, mllpPort);
            log.error(errorMessage, e);
            throw new MllpJUnitResourceException(errorMessage, e);
        }
    }

    public void reset() {
        try {
            clientSocket.setSoLinger(true, 0);
        } catch (SocketException socketEx) {
            log.warn("Exception encountered setting set SO_LINGER to force a TCP reset", socketEx);
        }
        try {
            if (null != inputStream) {
                clientSocket.close();
            }
        } catch (IOException e) {
            log.warn(String.format("Exception encountered resetting connection to %s:%s", mllpHost, mllpPort), e);
        } finally {
            inputStream = null;
            outputStream = null;
            clientSocket = null;
        }
        return;
    }

    public void disconnect() {
        if (DisconnectMethod.RESET == disconnectMethod) {
            reset();
        } else {
            close();
        }
    }

    public DisconnectMethod getDisconnectMethod() {
        return disconnectMethod;
    }

    public void setDisconnectMethod(DisconnectMethod disconnectMethod) {
        this.disconnectMethod = disconnectMethod;
    }

    public boolean isConnected() {
        return clientSocket.isConnected() && !clientSocket.isClosed();
    }

    public void checkConnection() {
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
            if (END_OF_STREAM == clientSocket.getInputStream().read()) {
                throw new MllpJUnitResourceException("checkConnection failed - read() returned END_OF_STREAM");
            }
        } catch (IOException ioEx) {
            throw new MllpJUnitResourceException("checkConnection failed - read() failure", ioEx);
        }
    }

    public void sendData(String data) {
        boolean disconnectAfterSend = false;
        this.sendData(data, disconnectAfterSend);
    }

    public void sendData(String data, boolean disconnectAfterSend) {
        byte[] payloadBytes = data.getBytes();

        try {
            outputStream.write(payloadBytes, 0, payloadBytes.length);
        } catch (IOException e) {
            log.error("Unable to send raw string", e);
            throw new MllpJUnitResourceException("Unable to send raw string", e);
        }

        if (disconnectAfterSend) {
            log.warn("Closing TCP connection");
            disconnect();
        }
    }

    public void sendFramedData(String hl7Message) {
        boolean disconnectAfterSend = false;
        this.sendFramedData(hl7Message, disconnectAfterSend);
    }

    public void sendFramedData(String hl7Message, boolean disconnectAfterSend) {
        if (null == clientSocket) {
            this.connect();
        }

        if (!clientSocket.isConnected()) {
            throw new MllpJUnitResourceException("Cannot send message - client is not connected");
        }
        if (null == outputStream) {
            throw new MllpJUnitResourceException("Cannot send message - output stream is null");
        }
        byte[] payloadBytes = hl7Message.getBytes();
        try {
            if (sendStartOfBlock) {
                outputStream.write(START_OF_BLOCK);
            } else {
                log.warn("Not sending START_OF_BLOCK");
            }
            outputStream.write(payloadBytes, 0, payloadBytes.length);
            if (sendEndOfBlock) {
                outputStream.write(END_OF_BLOCK);
            } else {
                log.warn("Not sending END_OF_BLOCK");
            }
            if (sendEndOfData) {
                outputStream.write(END_OF_DATA);
            } else {
                log.warn("Not sending END_OF_DATA");
            }
            outputStream.flush();
        } catch (IOException e) {
            log.error("Unable to send HL7 message", e);
            throw new MllpJUnitResourceException("Unable to send HL7 message", e);
        }

        if (disconnectAfterSend) {
            log.warn("Closing TCP connection");
            disconnect();
        }
    }

    public void sendFramedDataInMultiplePackets(String hl7Message, byte flushByte) {
        sendFramedDataInMultiplePackets(hl7Message, flushByte, false);
    }

    public void sendFramedDataInMultiplePackets(String hl7Message, byte flushByte, boolean disconnectAfterSend) {
        if (null == clientSocket) {
            this.connect();
        }

        if (!clientSocket.isConnected()) {
            throw new MllpJUnitResourceException("Cannot send message - client is not connected");
        }
        if (null == outputStream) {
            throw new MllpJUnitResourceException("Cannot send message - output stream is null");
        }
        byte[] payloadBytes = hl7Message.getBytes();
        try {
            if (sendStartOfBlock) {
                outputStream.write(START_OF_BLOCK);
            } else {
                log.warn("Not sending START_OF_BLOCK");
            }
            for (byte payloadByte : payloadBytes) {
                outputStream.write(payloadByte);
                if (flushByte == payloadByte) {
                    outputStream.flush();
                }
            }
            if (sendEndOfBlock) {
                outputStream.write(END_OF_BLOCK);
            } else {
                log.warn("Not sending END_OF_BLOCK");
            }
            if (sendEndOfData) {
                outputStream.write(END_OF_DATA);
            } else {
                log.warn("Not sending END_OF_DATA");
            }
            outputStream.flush();
        } catch (IOException e) {
            log.error("Unable to send HL7 message", e);
            throw new MllpJUnitResourceException("Unable to send HL7 message", e);
        }

        if (disconnectAfterSend) {
            log.warn("Closing TCP connection");
            disconnect();
        }
    }

    public String receiveFramedData() throws SocketException, SocketTimeoutException {
        return receiveFramedData(soTimeout);
    }

    public String receiveFramedData(int timout) throws SocketException {
        if (!isConnected()) {
            throw new MllpJUnitResourceException("Cannot receive acknowledgement - client is not connected");
        }
        if (null == outputStream) {
            throw new MllpJUnitResourceException("Cannot receive acknowledgement - output stream is null");
        }

        clientSocket.setSoTimeout(timout);
        StringBuilder acknowledgement = new StringBuilder();
        try {
            int firstByte = inputStream.read();
            if (START_OF_BLOCK != firstByte) {
                if (isConnected()) {
                    if (END_OF_STREAM == firstByte) {
                        log.warn("END_OF_STREAM reached while waiting for START_OF_BLOCK - closing socket");
                        try {
                            clientSocket.close();
                        } catch (Exception ex) {
                            log.warn(
                                    "Exception encountered closing socket after receiving END_OF_STREAM while waiting for START_OF_BLOCK");
                        }
                        return "";
                    } else {
                        log.error("Acknowledgement did not start with START_OF_BLOCK: {}", firstByte);
                        throw new MllpJUnitResourceCorruptFrameException("Message did not start with START_OF_BLOCK");
                    }
                } else {
                    throw new MllpJUnitResourceException("Connection terminated");
                }
            }
            boolean readingMessage = true;
            while (readingMessage) {
                int nextByte = inputStream.read();
                switch (nextByte) {
                    case -1:
                        throw new MllpJUnitResourceCorruptFrameException("Reached end of stream before END_OF_BLOCK");
                    case START_OF_BLOCK:
                        throw new MllpJUnitResourceCorruptFrameException("Received START_OF_BLOCK before END_OF_BLOCK");
                    case END_OF_BLOCK:
                        if (END_OF_DATA != inputStream.read()) {
                            throw new MllpJUnitResourceCorruptFrameException("END_OF_BLOCK was not followed by END_OF_DATA");
                        }
                        readingMessage = false;
                        break;
                    default:
                        acknowledgement.append((char) nextByte);
                }
            }
        } catch (SocketTimeoutException timeoutEx) {
            if (0 < acknowledgement.length()) {
                log.error("Timeout waiting for acknowledgement", timeoutEx);
            } else {
                log.error("Timeout while reading acknowledgement\n{}", acknowledgement.toString().replace('\r', '\n'),
                        timeoutEx);
            }
            throw new MllpJUnitResourceTimeoutException("Timeout while reading acknowledgement", timeoutEx);
        } catch (IOException e) {
            log.error("Unable to read HL7 acknowledgement", e);
            throw new MllpJUnitResourceException("Unable to read HL7 acknowledgement", e);
        }

        return acknowledgement.toString();
    }

    public String receiveData() throws SocketException, SocketTimeoutException {
        return receiveData(soTimeout);
    }

    public String receiveData(int timeout) throws SocketException {
        clientSocket.setSoTimeout(timeout);
        StringBuilder availableInput = new StringBuilder();

        try {
            do {
                availableInput.append((char) inputStream.read());
            } while (0 < inputStream.available());
        } catch (SocketTimeoutException timeoutEx) {
            log.error("Timeout while receiving available input", timeoutEx);
            throw new MllpJUnitResourceTimeoutException("Timeout while receiving available input", timeoutEx);
        } catch (IOException e) {
            log.warn("Exception encountered eating available input", e);
            throw new MllpJUnitResourceException("Exception encountered eating available input", e);
        }

        return availableInput.toString();
    }

    public String eatData() throws SocketException {
        return eatData(soTimeout);
    }

    public String eatData(int timeout) throws SocketException {
        clientSocket.setSoTimeout(timeout);

        StringBuilder availableInput = new StringBuilder();
        try {
            while (0 < inputStream.available()) {
                availableInput.append((char) inputStream.read());
            }
        } catch (IOException e) {
            log.warn("Exception encountered eating available input", e);
            throw new MllpJUnitResourceException("Exception encountered eating available input", e);
        }

        return availableInput.toString();
    }

    public String sendMessageAndWaitForAcknowledgement(String hl7Data) throws SocketException, SocketTimeoutException {
        sendFramedData(hl7Data);
        return receiveFramedData();
    }

    public String sendMessageAndWaitForAcknowledgement(String hl7Data, int acknwoledgementTimeout)
            throws SocketException, SocketTimeoutException {
        sendFramedData(hl7Data);
        return receiveFramedData(acknwoledgementTimeout);
    }

    public String getMllpHost() {
        return mllpHost;
    }

    public void setMllpHost(String mllpHost) {
        this.mllpHost = mllpHost;
    }

    public int getMllpPort() {
        return mllpPort;
    }

    public void setMllpPort(int mllpPort) {
        this.mllpPort = mllpPort;
    }

    public boolean isSendStartOfBlock() {
        return sendStartOfBlock;
    }

    public void setSendStartOfBlock(boolean sendStartOfBlock) {
        this.sendStartOfBlock = sendStartOfBlock;
    }

    public boolean isSendEndOfBlock() {
        return sendEndOfBlock;
    }

    public void setSendEndOfBlock(boolean sendEndOfBlock) {
        this.sendEndOfBlock = sendEndOfBlock;
    }

    public boolean isSendEndOfData() {
        return sendEndOfData;
    }

    public void setSendEndOfData(boolean sendEndOfData) {
        this.sendEndOfData = sendEndOfData;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public enum DisconnectMethod {
        CLOSE,
        RESET
    }
}
