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

package org.apache.camel.component.mllp.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.camel.component.mllp.MllpEndpoint;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.component.mllp.MllpSocketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An OutputStream modeled after the ByteArrayOutputStream specifically for MLLP operations.
 */
public class MllpSocketBuffer {
    static final Charset DEFAULT_CHARSET = StandardCharsets.US_ASCII;
    static final int MIN_BUFFER_SIZE = 2048;
    static final int MAX_BUFFER_SIZE = 0x40000000;  // Approximately 1-GB

    final Logger log = LoggerFactory.getLogger(this.getClass());
    final MllpEndpoint endpoint;

    byte buffer[];

    int availableByteCount;

    int startOfBlockIndex = -1;
    int endOfBlockIndex = -1;

    public MllpSocketBuffer(MllpEndpoint endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("MllpEndpoint cannot be null");
        }
        this.endpoint = endpoint;

        buffer = new byte[MIN_BUFFER_SIZE];
    }

    public boolean isEndOfDataRequired() {
        return endpoint.getConfiguration().isRequireEndOfData();
    }

    public boolean isEmpty() {
        return (size() > 0) ? false : true;
    }

    public synchronized void write(int b) {
        ensureCapacity(1);
        buffer[availableByteCount] = (byte) b;

        updateIndexes(b, 0);

        availableByteCount += 1;
    }


    public void write(byte[] b) {
        if (b != null && b.length > 0) {
            this.write(b, 0, b.length);
        }
    }

    public synchronized void write(byte[] sourceBytes, int offset, int writeCount) {
        if (sourceBytes != null && sourceBytes.length > 0) {
            if (offset < 0) {
                throw new IndexOutOfBoundsException(
                    String.format("offset <%d> is less than zero",
                        offset));
            }
            if (offset > sourceBytes.length) {
                throw new IndexOutOfBoundsException(
                    String.format("offset <%d> is greater than write count <%d>",
                        offset, writeCount));
            }

            if (writeCount < 0) {
                throw new IndexOutOfBoundsException(
                    String.format("write count <%d> is less than zero",
                        writeCount));
            }
            if (writeCount > sourceBytes.length) {
                throw new IndexOutOfBoundsException(
                    String.format("write count <%d> is greater than length of the source byte[] <%d>",
                        writeCount, sourceBytes.length));
            }
            if ((offset + writeCount) - sourceBytes.length > 0) {
                throw new IndexOutOfBoundsException(
                    String.format("offset <%d> plus write count <%d> is <%d> is greater than length <%d> of the source byte[]",
                    offset, writeCount, offset + writeCount, sourceBytes.length));
            }

            ensureCapacity(writeCount);
            System.arraycopy(sourceBytes, offset, buffer, availableByteCount, writeCount);

            for (int i = offset; i < writeCount && (startOfBlockIndex < 0 || endOfBlockIndex < 0); ++i) {
                updateIndexes(sourceBytes[i], i);
            }

            availableByteCount += writeCount;
        }
    }

    public synchronized void openMllpEnvelope() {
        reset();
        write(MllpProtocolConstants.START_OF_BLOCK);
    }

    public synchronized void closeMllpEnvelope() {
        write(MllpProtocolConstants.PAYLOAD_TERMINATOR);
    }

    public synchronized void setEnvelopedMessage(byte[] hl7Payload) {
        setEnvelopedMessage(hl7Payload, 0, hl7Payload != null ? hl7Payload.length : 0);
    }

    public synchronized void setEnvelopedMessage(byte[] hl7Payload, int offset, int length) {
        reset();

        if (hl7Payload != null && hl7Payload.length > 0) {
            if (hl7Payload[0] != MllpProtocolConstants.START_OF_BLOCK) {
                write(MllpProtocolConstants.START_OF_BLOCK);
            }

            write(hl7Payload, offset, length);

            if (!hasCompleteEnvelope()) {
                write(MllpProtocolConstants.PAYLOAD_TERMINATOR);
            }
        } else {
            write(MllpProtocolConstants.START_OF_BLOCK);
            write(MllpProtocolConstants.PAYLOAD_TERMINATOR);
        }
    }

    public synchronized void reset() {
        if (availableByteCount > 0) {
            // TODO: May be able to get rid of this
            Arrays.fill(buffer, (byte) 0);
        }

        availableByteCount = 0;

        startOfBlockIndex = -1;
        endOfBlockIndex = -1;
    }


    public synchronized void readFrom(Socket socket) throws MllpSocketException, SocketTimeoutException {
        log.trace("Entering readFrom ...");
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            ensureCapacity(MIN_BUFFER_SIZE);

            try {
                InputStream socketInputStream = socket.getInputStream();

                socket.setSoTimeout(endpoint.getConfiguration().getReceiveTimeout());

                readSocketInputStream(socketInputStream, socket);
                if (!hasCompleteEnvelope()) {
                    socket.setSoTimeout(endpoint.getConfiguration().getReadTimeout());

                    while (!hasCompleteEnvelope()) {
                        ensureCapacity(Math.max(MIN_BUFFER_SIZE, socketInputStream.available()));
                        readSocketInputStream(socketInputStream, socket);
                    }
                }

            } catch (SocketTimeoutException timeoutEx) {
                throw timeoutEx;
            } catch (IOException ioEx) {
                final String exceptionMessage = "Exception encountered reading Socket";
                resetSocket(socket, exceptionMessage);
                throw new MllpSocketException(exceptionMessage, ioEx);
            } finally {
                if (size() > 0 && !hasCompleteEnvelope()) {
                    if (!hasEndOfData() && hasEndOfBlock() && endOfBlockIndex < size() - 1) {
                        log.warn("readFrom exiting with partial payload ", Hl7Util.convertToPrintFriendlyString(buffer, 0, size() - 1));
                    }
                }
            }

        } else {
            log.warn("Socket is invalid - no data read");
        }

        log.trace("Exiting readFrom ...");
    }

    public synchronized void writeTo(Socket socket) throws MllpSocketException {
        log.trace("Entering writeTo ...");
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            if (!isEmpty()) {
                try {
                    OutputStream socketOutputStream = socket.getOutputStream();
                    if (hasStartOfBlock()) {
                        if (hasEndOfData()) {
                            socketOutputStream.write(buffer, startOfBlockIndex, endOfBlockIndex - startOfBlockIndex  + 2);
                        } else if (hasEndOfBlock()) {
                            socketOutputStream.write(buffer, startOfBlockIndex, endOfBlockIndex - startOfBlockIndex + 1);
                            socketOutputStream.write(MllpProtocolConstants.END_OF_DATA);
                        } else {
                            socketOutputStream.write(buffer, startOfBlockIndex, availableByteCount - startOfBlockIndex);
                            socketOutputStream.write(MllpProtocolConstants.PAYLOAD_TERMINATOR);
                        }
                    } else {
                        socketOutputStream.write(MllpProtocolConstants.START_OF_BLOCK);
                        socketOutputStream.write(buffer, 0, availableByteCount);
                        socketOutputStream.write(MllpProtocolConstants.PAYLOAD_TERMINATOR);
                    }
                    socketOutputStream.flush();
                } catch (IOException ioEx) {
                    final String exceptionMessage = "Exception encountered writing Socket";
                    resetSocket(socket, exceptionMessage);
                    throw new MllpSocketException(exceptionMessage, ioEx);
                }
            } else {
                log.warn("Ignoring call to writeTo(byte[] payload) - MLLP payload is null or empty");
            }
        } else {
            log.warn("Socket is invalid - no data written");
        }

        log.trace("Exiting writeTo ...");
    }

    public synchronized byte toByteArray()[] {
        if (availableByteCount > 0) {
            return Arrays.copyOf(buffer, availableByteCount);
        }

        return null;
    }

    public synchronized byte toByteArrayAndReset()[] {
        byte[] answer = toByteArray();

        reset();

        return answer;
    }

    @Override
    public synchronized String toString() {
        return toString(DEFAULT_CHARSET);
    }

    public synchronized String toString(Charset charset) {
        if (availableByteCount > 0) {
            return new String(buffer, 0, availableByteCount, charset);
        }

        return "";
    }

    public synchronized String toString(String charsetName) {
        if (availableByteCount > 0) {
            if (Charset.isSupported(charsetName)) {
                Charset charset = Charset.forName(charsetName);
                return toString(charset);
            } else if (MllpProtocolConstants.MSH18_VALUES.containsKey(charsetName)) {
                return toString(MllpProtocolConstants.MSH18_VALUES.get(charsetName));
            } else {
                return toString(DEFAULT_CHARSET);
            }
        }

        return "";
    }

    /**
     * Convert the entire contents of the buffer (including enveloping characters) to a print-friendly
     * String representation.
     *
     * @return print-friendly String
     */
    public synchronized String toPrintFriendlyString() {
        if (availableByteCount > 0) {
            return Hl7Util.convertToPrintFriendlyString(buffer, 0, availableByteCount);
        }

        return "";
    }

    public synchronized String toHl7String() {
        return this.toHl7String(StandardCharsets.US_ASCII.name());
    }

    public synchronized String toHl7String(String charsetName) {
        String hl7String = null;

        if (hasCompleteEnvelope()) {
            int offset = hasStartOfBlock() ? startOfBlockIndex + 1 : 1;
            int length = hasEndOfBlock() ? endOfBlockIndex - offset : availableByteCount - startOfBlockIndex - 1;
            if (length > 0) {
                try {
                    hl7String = new String(buffer,
                        offset,
                        length,
                        charsetName);
                } catch (UnsupportedEncodingException unsupportedEncodingEx) {
                    log.warn("Failed to create string using {} charset - falling back to default charset {}", charsetName, MllpProtocolConstants.DEFAULT_CHARSET);
                    hl7String = new String(buffer, offset, length, MllpProtocolConstants.DEFAULT_CHARSET);
                }
            } else {
                hl7String = "";
            }
        }

        return hl7String;
    }

    /**
     * Convert the enveloped contents of the buffer (excluding enveloping characters) to a print-friendly
     * String representation.
     *
     * @return print-friendly String
     */
    public synchronized String toPrintFriendlyHl7String() {
        if (hasCompleteEnvelope()) {
            int startPosition = hasStartOfBlock() ? startOfBlockIndex + 1 : 1;
            int endPosition = hasEndOfBlock() ? endOfBlockIndex : availableByteCount - 1;
            return Hl7Util.convertToPrintFriendlyString(buffer, startPosition, endPosition);
        }

        return "";
    }

    public synchronized byte[] toMllpPayload() {
        byte[] mllpPayload = null;

        if (hasCompleteEnvelope()) {
            int offset = hasStartOfBlock() ? startOfBlockIndex + 1 : 1;
            int length = hasEndOfBlock() ? endOfBlockIndex - offset : availableByteCount - startOfBlockIndex - 1;

            if (length > 0) {
                mllpPayload = new byte[length];
                System.arraycopy(buffer, offset, mllpPayload, 0, length);
            } else {
                mllpPayload = new byte[0];
            }
        }

        return mllpPayload;
    }

    public synchronized int getMllpPayloadLength() {
        int answer = -1;

        if (hasCompleteEnvelope()) {
            if (isEndOfDataRequired()) {
                answer = endOfBlockIndex - startOfBlockIndex + 2;
            } else {
                answer = endOfBlockIndex - startOfBlockIndex + 2;
            }
        }

        return answer;
    }




    public synchronized int getStartOfBlockIndex() {
        return startOfBlockIndex;
    }


    public synchronized int getEndOfBlockIndex() {
        return endOfBlockIndex;
    }

    public synchronized boolean hasCompleteEnvelope() {
        if (hasStartOfBlock()) {
            if (isEndOfDataRequired()) {
                return hasEndOfData();
            } else {
                return hasEndOfBlock();
            }
        }

        return false;
    }

    public synchronized boolean hasStartOfBlock() {
        return (startOfBlockIndex >= 0) ? true : false;
    }


    public synchronized boolean hasEndOfBlock() {
        return (endOfBlockIndex >= 0) ? true : false;
    }

    public synchronized boolean hasEndOfData() {
        if (hasEndOfBlock()) {
            int potentialEndOfDataIndex = endOfBlockIndex + 1;
            if ((potentialEndOfDataIndex < availableByteCount) && (buffer[potentialEndOfDataIndex] == MllpProtocolConstants.END_OF_DATA)) {
                return true;
            }
        }

        return false;
    }

    public synchronized boolean hasOutOfBandData() {
        return hasLeadingOutOfBandData() || hasTrailingOutOfBandData();
    }

    public synchronized boolean hasLeadingOutOfBandData() {
        if (size() > 0) {
            if (!hasStartOfBlock() || startOfBlockIndex > 0) {
                return true;
            }
        }

        return false;
    }

    public synchronized boolean hasTrailingOutOfBandData() {
        if (size() > 0) {
            if (hasEndOfData()) {
                if (endOfBlockIndex + 1 < size() - 1) {
                    return true;
                }
            } else if (!isEndOfDataRequired()) {
                if (hasEndOfBlock() && endOfBlockIndex < size() - 1) {
                    return true;
                }
            }
        }

        return false;
    }

    public synchronized byte[] getLeadingOutOfBandData() {
        byte[] outOfBandData = null;

        if (hasLeadingOutOfBandData()) {
            outOfBandData = new byte[startOfBlockIndex == -1 ? availableByteCount : startOfBlockIndex];
            System.arraycopy(buffer, 0, outOfBandData, 0, outOfBandData.length);
        }

        return outOfBandData;
    }

    public synchronized byte[] getTrailingOutOfBandData() {
        byte[] outOfBandData = null;

        if (hasTrailingOutOfBandData()) {
            int offset = hasEndOfData() ? endOfBlockIndex + 2 : endOfBlockIndex + 1;
            int length = size() - offset;
            outOfBandData = new byte[length];
            System.arraycopy(buffer, offset, outOfBandData, 0, length);
        }

        return outOfBandData;
    }

    public synchronized int size() {
        return availableByteCount;
    }

    public synchronized int capacity() {
        if (buffer != null) {
            return buffer.length - availableByteCount;
        }

        return -1;
    }

    public synchronized int bufferSize() {
        if (buffer != null) {
            return buffer.length;
        }

        return -1;
    }

    /**
     * Get the internal buffer.
     *
     * USE WITH CAUTION!!
     *
     * @return
     */
    public byte[] getBuffer() {
        return buffer;
    }

    void ensureCapacity(int requiredAvailableCapacity) {
        int currentAvailableCapacity = capacity();

        if (requiredAvailableCapacity > currentAvailableCapacity) {
            int requiredBufferSize = buffer.length + (requiredAvailableCapacity - currentAvailableCapacity);

            if (buffer.length >= MAX_BUFFER_SIZE) {
                final String exceptionMessageFormat = "Cannot increase the buffer size from <%d> to <%d>"
                    + " in order to increase the available capacity from <%d> to <%d> because the buffer is already the maximum size <%d>";
                throw new IllegalStateException(String.format(exceptionMessageFormat, buffer.length, requiredBufferSize, currentAvailableCapacity, requiredAvailableCapacity, MAX_BUFFER_SIZE));
            } else if (requiredBufferSize > MAX_BUFFER_SIZE) {
                final String exceptionMessageFormat = "Cannot increase the buffer size <%d>"
                    + " in order to increase the available capacity from <%d> to <%d> because the required buffer size <%d> exceeds the maximum buffer size <%d>";
                throw new IllegalStateException(String.format(exceptionMessageFormat, buffer.length, currentAvailableCapacity, requiredAvailableCapacity, requiredBufferSize, MAX_BUFFER_SIZE));
            }
            int newBufferSize = Math.min(buffer.length + Math.max(MIN_BUFFER_SIZE, requiredAvailableCapacity), MAX_BUFFER_SIZE);

            buffer = Arrays.copyOf(buffer, newBufferSize);
        }
    }

    void updateIndexes(int b, int indexOffset) {
        if (startOfBlockIndex < 0 && b == MllpProtocolConstants.START_OF_BLOCK) {
            startOfBlockIndex = availableByteCount + indexOffset;
        } else if (endOfBlockIndex < 0 && b == MllpProtocolConstants.END_OF_BLOCK) {
            endOfBlockIndex = availableByteCount + indexOffset;
        }
    }

    void readSocketInputStream(InputStream socketInputStream, Socket socket) throws MllpSocketException, SocketTimeoutException {
        log.trace("Entering readSocketInputStream - size = {}", size());
        try {
            int readCount = socketInputStream.read(buffer, availableByteCount, buffer.length - availableByteCount);
            if (readCount == MllpProtocolConstants.END_OF_STREAM) {
                final String exceptionMessage = "END_OF_STREAM returned from SocketInputStream.read(byte[], off, len)";
                resetSocket(socket, exceptionMessage);
                throw new SocketException(exceptionMessage);
            }
            if (readCount > 0) {
                for (int i = 0; (startOfBlockIndex == -1 || endOfBlockIndex == -1) && i < readCount; ++i) {
                    updateIndexes(buffer[availableByteCount + i], i);
                }
                availableByteCount += readCount;
                log.trace("Read {} bytes for a total of {} bytes", readCount, availableByteCount);
            }
        } catch (SocketTimeoutException timeoutEx) {
            throw timeoutEx;
        } catch (SocketException socketEx) {
            final String exceptionMessage = "SocketException encountered in readSocketInputStream";
            resetSocket(socket, exceptionMessage);
            throw new MllpSocketException(exceptionMessage, socketEx);
        } catch (IOException ioEx) {
            final String exceptionMessage = "IOException thrown from SocketInputStream.read(byte[], off, len)";
            resetSocket(socket, exceptionMessage);
            throw new MllpSocketException(exceptionMessage, ioEx);
        } finally {
            log.trace("Exiting readSocketInputStream - size = {}", size());
        }
    }

    public void closeSocket(Socket socket) {
        doSocketClose(socket, null, false);
    }

    public void closeSocket(Socket socket, String logMessage) {
        doSocketClose(socket, logMessage, false);
    }

    public void resetSocket(Socket socket) {
        doSocketClose(socket, null, true);
    }

    public void resetSocket(Socket socket, String logMessage) {
        doSocketClose(socket, logMessage, true);
    }

    void doSocketClose(Socket socket, String logMessage, boolean reset) {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            if (logMessage != null && !logMessage.isEmpty()) {
                log.info("{} - {} socket {}", reset ? "Resetting" : "Closing", logMessage, socket);
            } else {
                log.info("{} socket {}", reset ? "Resetting" : "Closing", socket);
            }

            endpoint.updateLastConnectionTerminatedTicks();

            if (!socket.isInputShutdown()) {
                try {
                    socket.shutdownInput();
                } catch (IOException ignoredEx) {
                    // TODO: Maybe log this
                }
            }

            if (!socket.isOutputShutdown()) {
                try {
                    socket.shutdownOutput();
                } catch (IOException ignoredEx) {
                    // TODO: Maybe log this
                }
            }

            if (reset) {
                try {
                    final boolean on = true;
                    final int linger = 0;
                    socket.setSoLinger(on, linger);
                } catch (IOException ignoredEx) {
                    // TODO: Maybe log this
                }
            }

            try {
                socket.close();
            } catch (IOException ignoredEx) {
                // TODO: Maybe log this
            }
        }
    }

    public static boolean isConnectionValid(Socket socket) {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public static String formatAddressString(SocketAddress sourceAddress, SocketAddress targetAddress) {
        String sourceAddressString = null;
        String targetAddressString = null;

        if (sourceAddress != null) {
            sourceAddressString = sourceAddress.toString();
        }

        if (targetAddress != null) {
            targetAddressString = targetAddress.toString();
        }

        return String.format("%s => %s", sourceAddressString, targetAddressString);
    }
}
