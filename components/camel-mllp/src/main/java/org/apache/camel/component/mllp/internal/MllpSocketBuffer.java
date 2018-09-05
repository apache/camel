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
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.camel.component.mllp.MllpComponent;
import org.apache.camel.component.mllp.MllpEndpoint;
import org.apache.camel.component.mllp.MllpProtocolConstants;
import org.apache.camel.component.mllp.MllpSocketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An OutputStream modeled after the ByteArrayOutputStream specifically for MLLP operations.
 */
public class MllpSocketBuffer {
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
                    String.format("write(byte[%d], offset[%d], writeCount[%d]) - offset is less than zero",
                        sourceBytes.length, offset, writeCount));
            }
            if (offset > sourceBytes.length) {
                throw new IndexOutOfBoundsException(
                    String.format("write(byte[%d], offset[%d], writeCount[%d]) - offset is greater than write count",
                        sourceBytes.length, offset, writeCount));
            }

            if (writeCount < 0) {
                throw new IndexOutOfBoundsException(
                    String.format("write(byte[%d], offset[%d], writeCount[%d]) - write count is less than zero",
                        sourceBytes.length, offset, writeCount));
            }
            if (writeCount > sourceBytes.length) {
                throw new IndexOutOfBoundsException(
                    String.format("write(byte[%d], offset[%d], writeCount[%d]) - write count is greater than length of the source byte[]",
                        sourceBytes.length, offset, writeCount));
            }
            if ((offset + writeCount) - sourceBytes.length > 0) {
                throw new IndexOutOfBoundsException(
                    String.format("write(byte[%d], offset[%d], writeCount[%d]) - offset plus write count <%d> is greater than length of the source byte[]",
                        sourceBytes.length, offset, writeCount, offset + writeCount));
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
                openMllpEnvelope();
            }

            write(hl7Payload, offset, length);

            if (!hasCompleteEnvelope()) {
                closeMllpEnvelope();
            }
        } else {
            openMllpEnvelope();
            closeMllpEnvelope();
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
        readFrom(socket, endpoint.getConfiguration().getReceiveTimeout(), endpoint.getConfiguration().getReadTimeout());
    }

    public synchronized void readFrom(Socket socket, int receiveTimeout, int readTimeout) throws MllpSocketException, SocketTimeoutException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            log.trace("readFrom({}, {}, {}) - entering", socket, receiveTimeout, readTimeout);
            ensureCapacity(MIN_BUFFER_SIZE);

            try {
                InputStream socketInputStream = socket.getInputStream();

                socket.setSoTimeout(receiveTimeout);

                readSocketInputStream(socketInputStream, socket);
                if (!hasCompleteEnvelope()) {
                    socket.setSoTimeout(readTimeout);

                    while (!hasCompleteEnvelope()) {
                        ensureCapacity(Math.max(MIN_BUFFER_SIZE, socketInputStream.available()));
                        readSocketInputStream(socketInputStream, socket);
                    }
                }

            } catch (SocketTimeoutException timeoutEx) {
                throw timeoutEx;
            } catch (IOException ioEx) {
                final String exceptionMessage = String.format("readFrom(%s, %d, %d) - IOException encountered", socket, receiveTimeout, readTimeout);
                resetSocket(socket, exceptionMessage);
                throw new MllpSocketException(exceptionMessage, ioEx);
            } finally {
                if (size() > 0 && !hasCompleteEnvelope()) {
                    if (!hasEndOfData() && hasEndOfBlock() && endOfBlockIndex < size() - 1) {
                        log.warn("readFrom({}, {}, {}) - exiting with partial payload {}", socket, receiveTimeout, readTimeout, Hl7Util.convertToPrintFriendlyString(buffer, 0, size() - 1));
                    }
                }
            }

        } else {
            log.warn("readFrom({}, {}, {}) - no data read because Socket is invalid", socket, receiveTimeout, readTimeout);
        }

        log.trace("readFrom({}, {}, {}) - exiting", socket, receiveTimeout, readTimeout);
    }

    public synchronized void writeTo(Socket socket) throws MllpSocketException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            log.trace("writeTo({}) - entering", socket);
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
                    final String exceptionMessage = String.format("writeTo({}) - IOException encountered", socket);
                    resetSocket(socket, exceptionMessage);
                    throw new MllpSocketException(exceptionMessage, ioEx);
                }
            } else {
                log.warn("writeTo({}) - no data written because buffer is empty", socket);
            }
        } else {
            log.warn("writeTo({}) - no data written because Socket is invalid", socket);
        }

        log.trace("writeTo({}) - exiting", socket);
    }

    public synchronized byte[] toByteArray() {
        if (availableByteCount > 0) {
            return Arrays.copyOf(buffer, availableByteCount);
        }

        return null;
    }

    public synchronized byte[] toByteArrayAndReset() {
        byte[] answer = toByteArray();

        reset();

        return answer;
    }

    @Override
    public synchronized String toString() {
        return toString(MllpComponent.getDefaultCharset());
    }

    public synchronized String toString(Charset charset) {
        if (availableByteCount > 0) {
            return new String(buffer, 0, availableByteCount, charset);
        }

        return "";
    }

    public synchronized String toString(String charsetName) {
        if (availableByteCount > 0) {
            try {
                if (Charset.isSupported(charsetName)) {
                    return toString(Charset.forName(charsetName));
                }
                log.warn("toString(charsetName[{}]) - unsupported character set name - using the MLLP default character set {}", charsetName, MllpComponent.getDefaultCharset());
            } catch (Exception charsetEx) {
                log.warn("toString(charsetName[{}]) - ignoring exception encountered determining character set - using the MLLP default character set {}",
                    charsetName, MllpComponent.getDefaultCharset(), charsetEx);
            }

            return toString(MllpComponent.getDefaultCharset());
        }

        return "";
    }

    public synchronized String toStringAndReset() {
        String answer = toString();

        reset();

        return answer;
    }

    public synchronized String toStringAndReset(String charsetName) {
        String answer = toString(charsetName);

        reset();

        return answer;
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

    public String toPrintFriendlyStringAndReset() {
        String answer = toPrintFriendlyString();

        reset();

        return answer;
    }

    public synchronized String toHl7String() {
        return this.toHl7String(MllpComponent.getDefaultCharset());
    }

    public String toHl7StringAndReset() {
        String answer = toHl7String();

        reset();

        return answer;
    }

    public synchronized String toHl7String(String charsetName) {
        if (charsetName != null && !charsetName.isEmpty()) {
            try {
                if (Charset.isSupported(charsetName)) {
                    return toHl7String(Charset.forName(charsetName));
                }
                log.warn("toHl7String(charsetName[{}]) - unsupported character set name - using the MLLP default character set {}", charsetName, MllpComponent.getDefaultCharset());
            } catch (Exception charsetEx) {
                log.warn("toHl7String(charsetName[{}]) - ignoring exception encountered determining character set for name - using the MLLP default character set {}",
                    charsetName, MllpComponent.getDefaultCharset(), charsetEx);
            }
        }

        return toHl7String(MllpComponent.getDefaultCharset());
    }

    public synchronized String toHl7String(Charset charset) {
        if (hasCompleteEnvelope()) {
            int offset = hasStartOfBlock() ? startOfBlockIndex + 1 : 1;
            int length = hasEndOfBlock() ? endOfBlockIndex - offset : availableByteCount - startOfBlockIndex - 1;
            if (length > 0) {
                return new String(buffer, offset, length, charset != null ? charset : MllpComponent.getDefaultCharset());
            } else {
                return "";
            }
        }

        return null;
    }

    public String toHl7StringAndReset(String charsetName) {
        String answer = toHl7String(charsetName);

        reset();

        return answer;
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

    public String toPrintFriendlyHl7StringAndReset() {
        String answer = toPrintFriendlyHl7String();

        reset();

        return answer;
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

    public byte[] toMllpPayloadAndReset() {
        byte[] answer = toMllpPayload();

        reset();

        return answer;
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
        if (startOfBlockIndex < 0) {
            if (b == MllpProtocolConstants.START_OF_BLOCK) {
                startOfBlockIndex = availableByteCount + indexOffset;
            }
        } else if (endOfBlockIndex < 0 && b == MllpProtocolConstants.END_OF_BLOCK) {
            endOfBlockIndex = availableByteCount + indexOffset;
        }
    }

    void readSocketInputStream(InputStream socketInputStream, Socket socket) throws MllpSocketException, SocketTimeoutException {
        log.trace("readSocketInputStream(socketInputStream, {}) - entering with initial buffer size = {}", socket, size());
        try {
            int readCount = socketInputStream.read(buffer, availableByteCount, buffer.length - availableByteCount);
            if (readCount == MllpProtocolConstants.END_OF_STREAM) {
                final String exceptionMessage = String.format("readSocketInputStream(socketInputStream, %s) - END_OF_STREAM returned from SocketInputStream.read(byte[%d], %d, %d)",
                    socket, buffer.length, availableByteCount, buffer.length - availableByteCount);
                resetSocket(socket);
                throw new MllpSocketException(exceptionMessage);
            }
            if (readCount > 0) {
                for (int i = 0; (startOfBlockIndex == -1 || endOfBlockIndex == -1) && i < readCount; ++i) {
                    updateIndexes(buffer[availableByteCount + i], i);
                }
                availableByteCount += readCount;

                if (hasStartOfBlock()) {
                    log.trace("readSocketInputStream(socketInputStream, {}) - read {} bytes for a total of {} bytes", socket, readCount, availableByteCount);
                } else {
                    log.warn("readSocketInputStream(socketInputStream, {}) - ignoring {} bytes received before START_OF_BLOCK", socket, size(), toPrintFriendlyStringAndReset());
                }
            }
        } catch (SocketTimeoutException timeoutEx) {
            throw timeoutEx;
        } catch (IOException ioEx) {
            final String exceptionMessage = String.format("readSocketInputStream(socketInputStream, %s) - IOException thrown from SocketInputStream.read(byte[%d], %d, %d) from %s",
                socket, buffer.length, availableByteCount, buffer.length - availableByteCount, socket);
            resetSocket(socket);
            throw new MllpSocketException(exceptionMessage, ioEx);
        } finally {
            log.trace("readSocketInputStream(socketInputStream, {}) - exiting with buffer size = {}", socket, size());
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
                log.debug("{} socket {}", reset ? "Resetting" : "Closing", socket);
            }

            endpoint.updateLastConnectionTerminatedTicks();

            if (!socket.isInputShutdown()) {
                try {
                    socket.shutdownInput();
                } catch (IOException ignoredEx) {
                    log.trace("doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.shutdownInput()", socket, logMessage, reset, ignoredEx);
                }
            }

            if (!socket.isOutputShutdown()) {
                try {
                    socket.shutdownOutput();
                } catch (IOException ignoredEx) {
                    log.trace("doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.shutdownOutput()", socket, logMessage, reset, ignoredEx);
                }
            }

            if (reset) {
                final boolean on = true;
                final int linger = 0;
                try {
                    socket.setSoLinger(on, linger);
                } catch (IOException ignoredEx) {
                    log.trace("doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.setSoLinger({}, {})", socket, logMessage, reset, on, linger, ignoredEx);
                }
            }

            try {
                socket.close();
            } catch (IOException ignoredEx) {
                // TODO: Maybe log this
                log.trace("doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.close()", socket, logMessage, reset, ignoredEx);
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
