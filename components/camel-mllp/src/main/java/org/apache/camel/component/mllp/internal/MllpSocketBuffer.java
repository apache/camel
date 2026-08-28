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
package org.apache.camel.component.mllp.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

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

    private static final Logger LOG = LoggerFactory.getLogger(MllpSocketBuffer.class);
    private final ReentrantLock lock = new ReentrantLock();
    final MllpEndpoint endpoint;

    byte[] buffer;
    int availableByteCount;

    int startOfBlockIndex = -1;
    int endOfBlockIndex = -1;
    String charset;
    Hl7Util hl7Util;
    int minBufferSize;
    int maxBufferSize;

    public MllpSocketBuffer(MllpEndpoint endpoint) {
        if (endpoint == null) {
            throw new IllegalArgumentException("MllpEndpoint cannot be null");
        }
        this.endpoint = endpoint;
        this.charset = endpoint.getCharsetName();
        MllpComponent component = endpoint.getComponent();
        this.hl7Util = new Hl7Util(component.getLogPhiMaxBytes(), component.getLogPhi());
        this.minBufferSize = endpoint.getConfiguration().getMinBufferSize();
        this.maxBufferSize = endpoint.getConfiguration().getMaxBufferSize();

        buffer = new byte[minBufferSize];
    }

    public boolean isEndOfDataRequired() {
        return endpoint.getConfiguration().isRequireEndOfData();
    }

    public boolean isEmpty() {
        return size() <= 0;
    }

    public void write(int b) {
        lock.lock();
        try {
            ensureCapacity(1);
            buffer[availableByteCount] = (byte) b;

            updateIndexes(b, 0);

            availableByteCount += 1;
        } finally {
            lock.unlock();
        }
    }

    public void write(byte[] b) {
        if (b != null && b.length > 0) {
            this.write(b, 0, b.length);
        }
    }

    public void write(byte[] sourceBytes, int offset, int writeCount) {
        lock.lock();
        try {
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
                            String.format(
                                    "write(byte[%d], offset[%d], writeCount[%d]) - write count is greater than length of the source byte[]",
                                    sourceBytes.length, offset, writeCount));
                }
                if ((offset + writeCount) - sourceBytes.length > 0) {
                    throw new IndexOutOfBoundsException(
                            String.format(
                                    "write(byte[%d], offset[%d], writeCount[%d]) - offset plus write count <%d> is greater than length of the source byte[]",
                                    sourceBytes.length, offset, writeCount, offset + writeCount));
                }

                ensureCapacity(writeCount);
                System.arraycopy(sourceBytes, offset, buffer, availableByteCount, writeCount);

                for (int i = offset; i < writeCount && (startOfBlockIndex < 0 || endOfBlockIndex < 0); ++i) {
                    updateIndexes(sourceBytes[i], i);
                }

                availableByteCount += writeCount;
            }
        } finally {
            lock.unlock();
        }
    }

    public void openMllpEnvelope() {
        lock.lock();
        try {
            reset();
            write(MllpProtocolConstants.START_OF_BLOCK);
        } finally {
            lock.unlock();
        }
    }

    public void closeMllpEnvelope() {
        lock.lock();
        try {
            write(MllpProtocolConstants.PAYLOAD_TERMINATOR);
        } finally {
            lock.unlock();
        }
    }

    public void setEnvelopedMessage(byte[] hl7Payload) {
        lock.lock();
        try {
            setEnvelopedMessage(hl7Payload, 0, hl7Payload != null ? hl7Payload.length : 0);
        } finally {
            lock.unlock();
        }
    }

    public void setEnvelopedMessage(byte[] hl7Payload, int offset, int length) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            if (availableByteCount > 0) {
                // TODO: May be able to get rid of this
                Arrays.fill(buffer, (byte) 0);
            }

            availableByteCount = 0;

            startOfBlockIndex = -1;
            endOfBlockIndex = -1;
        } finally {
            lock.unlock();
        }
    }

    public void readFrom(Socket socket) throws MllpSocketException, SocketTimeoutException {
        lock.lock();
        try {
            readFrom(socket, endpoint.getConfiguration().getReceiveTimeout(), endpoint.getConfiguration().getReadTimeout());
        } finally {
            lock.unlock();
        }
    }

    public void readFrom(Socket socket, int receiveTimeout, int readTimeout)
            throws MllpSocketException, SocketTimeoutException {
        lock.lock();
        try {
            if (socket != null && socket.isConnected() && !socket.isClosed()) {
                LOG.trace("readFrom({}, {}, {}) - entering", socket, receiveTimeout, readTimeout);
                ensureCapacity(minBufferSize);

                try {
                    InputStream socketInputStream = socket.getInputStream();

                    socket.setSoTimeout(receiveTimeout);

                    readSocketInputStream(socketInputStream, socket);
                    if (!hasCompleteEnvelope()) {
                        socket.setSoTimeout(readTimeout);

                        while (!hasCompleteEnvelope()) {
                            ensureCapacity(Math.max(minBufferSize, socketInputStream.available()));
                            readSocketInputStream(socketInputStream, socket);
                        }
                    }

                } catch (SocketTimeoutException timeoutEx) {
                    throw timeoutEx;
                } catch (IOException ioEx) {
                    final String exceptionMessage
                            = String.format("readFrom(%s, %d, %d) - IOException encountered", socket, receiveTimeout,
                                    readTimeout);
                    resetSocket(socket, exceptionMessage);
                    throw new MllpSocketException(exceptionMessage, ioEx);
                } finally {
                    if (size() > 0 && !hasCompleteEnvelope()) {
                        if (!hasEndOfData() && hasEndOfBlock() && endOfBlockIndex < size() - 1) {
                            LOG.warn("readFrom({}, {}, {}) - exiting with partial payload {}", socket, receiveTimeout,
                                    readTimeout,
                                    hl7Util.convertToLoggableString(buffer, 0, size() - 1));
                        }
                    }
                }

            } else {
                LOG.warn("readFrom({}, {}, {}) - no data read because Socket is invalid", socket, receiveTimeout, readTimeout);
            }

            LOG.trace("readFrom({}, {}, {}) - exiting", socket, receiveTimeout, readTimeout);
        } finally {
            lock.unlock();
        }
    }

    public void writeTo(Socket socket) throws MllpSocketException {
        lock.lock();
        try {
            if (socket != null && socket.isConnected() && !socket.isClosed()) {
                LOG.trace("writeTo({}) - entering", socket);
                if (!isEmpty()) {
                    try {
                        OutputStream socketOutputStream = socket.getOutputStream();
                        if (hasStartOfBlock()) {
                            if (hasEndOfData()) {
                                socketOutputStream.write(buffer, startOfBlockIndex, endOfBlockIndex - startOfBlockIndex + 2);
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
                        final String exceptionMessage = String.format("writeTo(%s) - IOException encountered", socket);
                        resetSocket(socket, exceptionMessage);
                        throw new MllpSocketException(exceptionMessage, ioEx);
                    }
                } else {
                    LOG.warn("writeTo({}) - no data written because buffer is empty", socket);
                }
            } else {
                LOG.warn("writeTo({}) - no data written because Socket is invalid", socket);
            }

            LOG.trace("writeTo({}) - exiting", socket);
        } finally {
            lock.unlock();
        }
    }

    public byte[] toByteArray() {
        lock.lock();
        try {
            if (availableByteCount > 0) {
                return Arrays.copyOf(buffer, availableByteCount);
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    public byte[] toByteArrayAndReset() {
        lock.lock();
        try {
            byte[] answer = toByteArray();

            reset();

            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            if (charset != null) {
                return toString(charset);
            } else {
                return toString(endpoint.getComponent().getDefaultCharset());
            }
        } finally {
            lock.unlock();
        }
    }

    public String toString(Charset charset) {
        lock.lock();
        try {
            if (availableByteCount > 0) {
                return new String(buffer, 0, availableByteCount, charset);
            }

            return "";
        } finally {
            lock.unlock();
        }
    }

    public String toString(String charsetName) {
        lock.lock();
        try {
            if (availableByteCount > 0) {
                try {
                    if (Charset.isSupported(charsetName)) {
                        return toString(Charset.forName(charsetName));
                    }
                } catch (Exception charsetEx) {
                    // ignore
                }
            }

            return "";
        } finally {
            lock.unlock();
        }
    }

    /**
     * Convert the entire contents of the buffer (including enveloping characters) to a print-friendly String
     * representation.
     *
     * @return print-friendly String
     */
    public String toPrintFriendlyString() {
        lock.lock();
        try {
            if (availableByteCount > 0) {
                return hl7Util.convertToPrintFriendlyString(buffer, 0, availableByteCount);
            }

            return "";
        } finally {
            lock.unlock();
        }
    }

    public String toPrintFriendlyStringAndReset() {
        String answer = toPrintFriendlyString();

        reset();

        return answer;
    }

    /**
     * The buffer content for a log statement, honouring the component's {@code logPhi} setting, and resetting the
     * buffer either way so the caller's behaviour does not depend on whether logging is enabled.
     */
    public String toLoggableStringAndReset() {
        String answer = hl7Util.isLogPhi() ? toPrintFriendlyString() : Hl7Util.PHI_SUPPRESSED_REPLACEMENT_VALUE;

        reset();

        return answer;
    }

    public String toHl7String() {
        lock.lock();
        try {
            return this.toHl7String(charset);
        } finally {
            lock.unlock();
        }
    }

    public String toHl7String(String charsetName) {
        lock.lock();
        try {
            if (charsetName != null && !charsetName.isEmpty()) {
                try {
                    if (Charset.isSupported(charsetName)) {
                        return toHl7String(Charset.forName(charsetName));
                    }
                } catch (Exception charsetEx) {
                    // ignore
                }
            }

            if (Charset.isSupported(endpoint.getComponent().getDefaultCharset())) {
                return toHl7String(endpoint.getComponent().getDefaultCharset());
            }

            return "";
        } finally {
            lock.unlock();
        }
    }

    public String toHl7String(Charset charset) {
        lock.lock();
        try {
            if (hasCompleteEnvelope()) {
                int offset = hasStartOfBlock() ? startOfBlockIndex + 1 : 1;
                int length = hasEndOfBlock() ? endOfBlockIndex - offset : availableByteCount - startOfBlockIndex - 1;
                if (length > 0) {
                    return new String(buffer, offset, length, charset);
                } else {
                    return "";
                }
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Convert the enveloped contents of the buffer (excluding enveloping characters) to a print-friendly String
     * representation.
     *
     * @return print-friendly String
     */
    public String toPrintFriendlyHl7String() {
        lock.lock();
        try {
            if (hasCompleteEnvelope()) {
                int startPosition = hasStartOfBlock() ? startOfBlockIndex + 1 : 1;
                int endPosition = hasEndOfBlock() ? endOfBlockIndex : availableByteCount - 1;
                return hl7Util.convertToPrintFriendlyString(buffer, startPosition, endPosition);
            }

            return "";
        } finally {
            lock.unlock();
        }
    }

    public byte[] toMllpPayload() {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public int getStartOfBlockIndex() {
        lock.lock();
        try {
            return startOfBlockIndex;
        } finally {
            lock.unlock();
        }
    }

    public int getEndOfBlockIndex() {
        lock.lock();
        try {
            return endOfBlockIndex;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasCompleteEnvelope() {
        lock.lock();
        try {
            if (hasStartOfBlock()) {
                if (isEndOfDataRequired()) {
                    return hasEndOfData();
                } else {
                    return hasEndOfBlock();
                }
            }

            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasStartOfBlock() {
        lock.lock();
        try {
            return startOfBlockIndex >= 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasEndOfBlock() {
        lock.lock();
        try {
            return endOfBlockIndex >= 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasEndOfData() {
        lock.lock();
        try {
            if (hasEndOfBlock()) {
                int potentialEndOfDataIndex = endOfBlockIndex + 1;
                if (potentialEndOfDataIndex < availableByteCount
                        && buffer[potentialEndOfDataIndex] == MllpProtocolConstants.END_OF_DATA) {
                    return true;
                }
            }

            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasOutOfBandData() {
        lock.lock();
        try {
            return hasLeadingOutOfBandData() || hasTrailingOutOfBandData();
        } finally {
            lock.unlock();
        }
    }

    public boolean hasLeadingOutOfBandData() {
        lock.lock();
        try {
            if (size() > 0) {
                if (!hasStartOfBlock() || startOfBlockIndex > 0) {
                    return true;
                }
            }

            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasTrailingOutOfBandData() {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public byte[] getLeadingOutOfBandData() {
        lock.lock();
        try {
            byte[] outOfBandData = null;

            if (hasLeadingOutOfBandData()) {
                outOfBandData = new byte[startOfBlockIndex == -1 ? availableByteCount : startOfBlockIndex];
                System.arraycopy(buffer, 0, outOfBandData, 0, outOfBandData.length);
            }

            return outOfBandData;
        } finally {
            lock.unlock();
        }
    }

    public byte[] getTrailingOutOfBandData() {
        lock.lock();
        try {
            byte[] outOfBandData = null;

            if (hasTrailingOutOfBandData()) {
                int offset = hasEndOfData() ? endOfBlockIndex + 2 : endOfBlockIndex + 1;
                int length = size() - offset;
                outOfBandData = new byte[length];
                System.arraycopy(buffer, offset, outOfBandData, 0, length);
            }

            return outOfBandData;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return availableByteCount;
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        lock.lock();
        try {
            if (buffer != null) {
                return buffer.length - availableByteCount;
            }

            return -1;
        } finally {
            lock.unlock();
        }
    }

    void ensureCapacity(int requiredAvailableCapacity) {
        int currentAvailableCapacity = capacity();

        if (requiredAvailableCapacity > currentAvailableCapacity) {
            int requiredBufferSize = buffer.length + (requiredAvailableCapacity - currentAvailableCapacity);

            if (buffer.length >= maxBufferSize) {
                final String exceptionMessageFormat = "Cannot increase the buffer size from <%d> to <%d>"
                                                      + " in order to increase the available capacity from <%d> to <%d> because the buffer is already the maximum size <%d>";
                throw new IllegalStateException(
                        String.format(exceptionMessageFormat, buffer.length, requiredBufferSize, currentAvailableCapacity,
                                requiredAvailableCapacity, maxBufferSize));
            } else if (requiredBufferSize > maxBufferSize) {
                final String exceptionMessageFormat = "Cannot increase the buffer size <%d>"
                                                      + " in order to increase the available capacity from <%d> to <%d> because the required buffer size <%d> exceeds the maximum buffer size <%d>";
                throw new IllegalStateException(
                        String.format(exceptionMessageFormat, buffer.length, currentAvailableCapacity,
                                requiredAvailableCapacity, requiredBufferSize, maxBufferSize));
            }
            int newBufferSize = Math.min(buffer.length + Math.max(minBufferSize, requiredAvailableCapacity), maxBufferSize);

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

    void readSocketInputStream(InputStream socketInputStream, Socket socket)
            throws MllpSocketException, SocketTimeoutException {
        LOG.trace("readSocketInputStream(socketInputStream, {}) - entering with initial buffer size = {}", socket, size());
        try {
            int readCount = socketInputStream.read(buffer, availableByteCount, buffer.length - availableByteCount);
            if (readCount == MllpProtocolConstants.END_OF_STREAM) {
                final String exceptionMessage = String.format(
                        "readSocketInputStream(socketInputStream, %s) - END_OF_STREAM returned from SocketInputStream.read(byte[%d], %d, %d)",
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
                    LOG.trace("readSocketInputStream(socketInputStream, {}) - read {} bytes for a total of {} bytes", socket,
                            readCount, availableByteCount);
                } else {
                    LOG.warn(
                            "readSocketInputStream(socketInputStream, {}) - ignoring {} bytes received before START_OF_BLOCK: {}",
                            socket, size(), toLoggableStringAndReset());
                }
            }
        } catch (SocketTimeoutException timeoutEx) {
            throw timeoutEx;
        } catch (IOException ioEx) {
            final String exceptionMessage = String.format(
                    "readSocketInputStream(socketInputStream, %s) - IOException thrown from SocketInputStream.read(byte[%d], %d, %d) from %s",
                    socket, buffer.length, availableByteCount, buffer.length - availableByteCount, socket);
            resetSocket(socket);
            throw new MllpSocketException(exceptionMessage, ioEx);
        } finally {
            LOG.trace("readSocketInputStream(socketInputStream, {}) - exiting with buffer size = {}", socket, size());
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
                LOG.info("{} - {} socket {}", reset ? "Resetting" : "Closing", logMessage, socket);
            } else {
                LOG.debug("{} socket {}", reset ? "Resetting" : "Closing", socket);
            }

            endpoint.updateLastConnectionTerminatedTicks();

            if (!socket.isInputShutdown()) {
                try {
                    socket.shutdownInput();
                } catch (IOException ignoredEx) {
                    LOG.trace(
                            "doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.shutdownInput()",
                            socket, logMessage, reset, ignoredEx);
                }
            }

            if (!socket.isOutputShutdown()) {
                try {
                    socket.shutdownOutput();
                } catch (IOException ignoredEx) {
                    LOG.trace(
                            "doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.shutdownOutput()",
                            socket, logMessage, reset, ignoredEx);
                }
            }

            if (reset) {
                final boolean on = true;
                final int linger = 0;
                try {
                    socket.setSoLinger(on, linger);
                } catch (IOException ignoredEx) {
                    LOG.trace(
                            "doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.setSoLinger({}, {})",
                            socket, logMessage, reset, on, linger, ignoredEx);
                }
            }

            try {
                socket.close();
            } catch (IOException ignoredEx) {
                // TODO: Maybe log this
                LOG.trace("doSocketClose(socket[{}], logMessage[{}], reset[{}] - ignoring exception raised by Socket.close()",
                        socket, logMessage, reset, ignoredEx);
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
