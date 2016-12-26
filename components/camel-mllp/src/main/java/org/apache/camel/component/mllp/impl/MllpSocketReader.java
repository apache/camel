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
package org.apache.camel.component.mllp.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.camel.component.mllp.MllpAcknowledgementTimeoutException;
import org.apache.camel.component.mllp.MllpComponent;
import org.apache.camel.component.mllp.MllpException;
import org.apache.camel.component.mllp.MllpReceiveAcknowledgementException;
import org.apache.camel.component.mllp.MllpReceiveException;
import org.apache.camel.component.mllp.MllpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

public class MllpSocketReader {

    final Socket socket;
    final int receiveTimeout;
    final int readTimeout;
    final boolean acknowledgementReader;
    Logger log = LoggerFactory.getLogger(this.getClass());
    byte[] receiveBuffer;
    ByteArrayOutputStream readAdditionalStream;

    public MllpSocketReader(Socket socket, int receiveTimeout, int readTimeout, boolean acknowledgementReader) {
        this.socket = socket;
        this.receiveTimeout = receiveTimeout;
        this.readTimeout = readTimeout;
        this.acknowledgementReader = acknowledgementReader;
        try {
            receiveBuffer = new byte[socket.getReceiveBufferSize()];
        } catch (SocketException socketEx) {
            throw new IllegalStateException("Cannot retrieve the value of SO_RCVBUF from the Socket", socketEx);
        }
    }

    public byte[] readEnvelopedPayload() throws MllpException {
        return readEnvelopedPayload(null, null);
    }

    public byte[] readEnvelopedPayload(byte[] hl7MessageBytes) throws MllpException {
        return readEnvelopedPayload(null, hl7MessageBytes);
    }

    public byte[] readEnvelopedPayload(Integer initialByte) throws MllpException {
        return readEnvelopedPayload(initialByte, null);
    }

    protected byte[] readEnvelopedPayload(Integer initialByte, byte[] hl7MessageBytes) throws MllpException {
        byte[] answer = null;

        MllpSocketUtil.setSoTimeout(socket, receiveTimeout, log, "Preparing to receive payload");

        InputStream socketInputStream = null;
        try {
            socketInputStream = socket.getInputStream();
        } catch (IOException ioEx) {
            final String errorMessage = "Failed to retrieve the InputStream from the Socket";
            resetConnection(errorMessage);
            throw isAcknowledgementReader()
                    ? new MllpReceiveAcknowledgementException(errorMessage, hl7MessageBytes, ioEx)
                    : new MllpReceiveException(errorMessage, ioEx);
        }

        // Read the acknowledgment - hopefully in one shot
        int readCount;
        int startPosition = (initialByte != null && initialByte == START_OF_BLOCK) ? 0 : -1;
        do { // Read from the socket until the beginning of a MLLP payload is found or a timeout occurs
            try {
                readCount = socketInputStream.read(receiveBuffer);
                if (readCount == -1) {
                    String errorMessage = "END_OF_STREAM encountered while attempting to receive payload - was Socket closed?";
                    resetConnection(errorMessage);
                    throw isAcknowledgementReader()
                            ? new MllpReceiveAcknowledgementException(errorMessage, hl7MessageBytes)
                            : new MllpReceiveException(errorMessage);
                } else if (log.isTraceEnabled()) {
                    log.trace("Received bytes: {}", MllpComponent.covertBytesToPrintFriendlyString(receiveBuffer, 0, readCount));
                }
            } catch (SocketTimeoutException timeoutEx) {
                if (isAcknowledgementReader()) {
                    throw new MllpAcknowledgementTimeoutException(hl7MessageBytes, timeoutEx);
                } else {
                    if (initialByte != null && initialByte == START_OF_BLOCK) {
                        answer = new byte[1];
                        answer[0] = initialByte.byteValue();
                        throw new MllpTimeoutException(answer, timeoutEx);
                    }

                    return null;
                }
            } catch (IOException ioEx) {
                String errorMessage = "Error receiving payload";
                log.error(errorMessage, ioEx);
                resetConnection(errorMessage);
                throw isAcknowledgementReader()
                        ? new MllpReceiveAcknowledgementException(errorMessage, hl7MessageBytes, ioEx)
                        : new MllpReceiveException(errorMessage, ioEx);
            }

            if (readCount > 0) {  // If some data was read, make sure we found the beginning of the message
                if (initialByte != null && initialByte == START_OF_BLOCK) {
                    startPosition = 0;
                } else {
                    int startOfBlock = MllpSocketUtil.findStartOfBlock(receiveBuffer, readCount);
                    startPosition = (startOfBlock == -1) ? -1 : startOfBlock + 1;
                }
                if (startPosition > 1) {
                    // Some out-of-band data was received - log it
                    final String format = "Ignoring {} out-of-band bytes received before the beginning of the payload";
                    int length = readCount - startPosition - 1;
                    if (MllpComponent.isLogPhi()) {
                        log.warn(format + ": {}", length, MllpComponent.covertBytesToPrintFriendlyString(receiveBuffer, 0, length));
                    } else {
                        log.warn(format, length);
                    }
                }
            }
        } while (startPosition == -1);

        // Check to see if the payload is complete
        int endPosition = MllpSocketUtil.findEndOfMessage(receiveBuffer, readCount);

        if (endPosition != -1) {
            // We have a complete payload - build the result without delimiters
            if (endPosition < readCount - 3) {
                // Some out-of-band data was received - log it
                final String format = "Ignoring {} out-of-band bytes received after the end of the payload";
                int length = readCount - endPosition - 2;
                if (MllpComponent.isLogPhi()) {
                    log.warn(format + ": {}", length, MllpComponent.covertBytesToPrintFriendlyString(receiveBuffer, endPosition + 1, length));
                } else {
                    log.warn(format, length);
                }
            }

            // Build the answer
            int length = endPosition - startPosition;
            answer = new byte[length];
            System.arraycopy(receiveBuffer, startPosition, answer, 0, length);
        } else {
            // The payload is incomplete - read it all before returning

            // Write the data already received to the overflow stream, without the beginning delimiters
            getReadAdditionalStream().reset();
            readAdditionalStream.write(receiveBuffer, startPosition, readCount - startPosition);

            // We've already received some data, so switch to the read timeout
            MllpSocketUtil.setSoTimeout(socket, readTimeout, log, "Preparing to continue reading payload");

            // Now the current data is in the overflow stream, continue reading until the end of the payload is found or a timeout occurs
            endPosition = -1;
            do { // Read from the socket until the end of the MLLP payload is found or a timeout occurs
                try {
                    readCount = socketInputStream.read(receiveBuffer);
                    if (readCount == -1) {
                        String errorMessage = "END_OF_STREAM encountered while attempting to read the end of the payload - Socket was closed or reset";
                        resetConnection(errorMessage);
                        byte[] partialPayload = (readAdditionalStream.size() > 0) ? readAdditionalStream.toByteArray() : null;
                        throw isAcknowledgementReader()
                                ? new MllpReceiveAcknowledgementException(errorMessage, hl7MessageBytes, partialPayload)
                                : new MllpReceiveException(errorMessage, partialPayload);
                    } else if (log.isTraceEnabled()) {
                        log.trace("Read additional bytes: {}", MllpComponent.covertBytesToPrintFriendlyString(receiveBuffer, 0, readCount));
                    }
                } catch (SocketTimeoutException timeoutEx) {
                    String errorMessage = "Timeout reading the end of the payload";
                    resetConnection(errorMessage);
                    byte[] partialPayload = (readAdditionalStream.size() > 0) ? readAdditionalStream.toByteArray() : null;
                    throw isAcknowledgementReader()
                            ? new MllpAcknowledgementTimeoutException(errorMessage, hl7MessageBytes, partialPayload, timeoutEx)
                            : new MllpTimeoutException(errorMessage, partialPayload, timeoutEx);
                } catch (IOException ioEx) {
                    String errorMessage = "Error reading  the end of the payload";
                    resetConnection(errorMessage);
                    log.error(errorMessage);
                    byte[] partialPayload = (readAdditionalStream.size() > 0) ? readAdditionalStream.toByteArray() : null;
                    throw isAcknowledgementReader()
                            ? new MllpReceiveAcknowledgementException(errorMessage, hl7MessageBytes, partialPayload, ioEx)
                            : new MllpReceiveException(errorMessage, partialPayload, ioEx);
                }
                if (readCount > 0) {  // If some data was read, make sure we found the end of the message
                    endPosition = MllpSocketUtil.findEndOfMessage(receiveBuffer, readCount);
                    if (endPosition != -1) {
                        if (endPosition < readCount - 2) {
                            final String format = "Ignoring {} out-of-band bytes after the end of the payload";
                            int length = readCount - endPosition - 2;
                            if (MllpComponent.isLogPhi()) {
                                log.warn(format + ": {}", length, MllpComponent.covertBytesToPrintFriendlyString(receiveBuffer, endPosition + 2, length));
                            } else {
                                log.warn(format, length);
                            }
                        }
                        readAdditionalStream.write(receiveBuffer, 0, endPosition);
                    } else {
                        readAdditionalStream.write(receiveBuffer, 0, readCount);
                    }
                }
            } while (endPosition == -1);

            // All available data has been read - return the data
            answer = readAdditionalStream.toByteArray();
        }

        // Check to see if there is any more data available
        int availableCount;
        do {
            try {
                availableCount = socketInputStream.available();
            } catch (IOException ioEx) {
                log.warn("Ignoring IOException encountered while checking for additional available trailing bytes", ioEx);
                break;
            }
            if (availableCount > 0) { // if data is available, eat it
                try {
                    readCount = socketInputStream.read(receiveBuffer);
                    final String format = "Ignoring {} out-of-band bytes trailing after the end of the payload";
                    if (MllpComponent.isLogPhi()) {
                        log.warn(format + ": {}", readCount, MllpComponent.covertBytesToPrintFriendlyString(receiveBuffer, 0, readCount));
                    } else {
                        log.warn(format, readCount);
                    }
                } catch (IOException ioEx) {
                    log.warn(String.format("Ignoring IOException encountered while attempting to read %d bytes of trailing data", availableCount), ioEx);
                    break;
                }
            }
        } while (availableCount != 0);

        return answer;
    }

    public void closeConnection(String reasonMessage) {
        MllpSocketUtil.close(socket, log, reasonMessage);
    }

    public void resetConnection(String reasonMessage) {
        MllpSocketUtil.reset(socket, log, reasonMessage);
    }

    public Socket getSocket() {
        return socket;
    }

    public int getReceiveTimeout() {
        return receiveTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public byte[] getReceiveBuffer() {
        return receiveBuffer;
    }

    public boolean isAcknowledgementReader() {
        return acknowledgementReader;
    }

    public ByteArrayOutputStream getReadAdditionalStream() {
        if (readAdditionalStream == null) {
            readAdditionalStream = new ByteArrayOutputStream(receiveBuffer.length);
        }

        return readAdditionalStream;
    }
}
