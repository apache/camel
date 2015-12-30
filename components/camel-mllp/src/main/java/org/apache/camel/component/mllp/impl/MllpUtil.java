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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.camel.component.mllp.MllpComponent;
import org.apache.camel.component.mllp.MllpCorruptFrameException;
import org.apache.camel.component.mllp.MllpException;
import org.apache.camel.component.mllp.MllpTimeoutException;
import org.apache.camel.component.mllp.MllpWriteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_DATA;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_STREAM;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

/**
 * Supplies methods to read and write messages in a MLLP Frame.
 * <p/>
 * Although the methods in the class are intended to handle HL7 v2 formatted messages, the methods do not
 * depend on that format - any byte[]can be written to the Socket.  Also, any byte[] can be read from the socket
 * provided it has the proper MLLP Enveloping - <START_OF_BLOCK>payload<END_OF_BLOCK><END_OF_DATA>>.
 * <p/>
 * NOTE: MLLP payloads are not logged unless the logging level is set to DEBUG or TRACE to avoid introducing PHI
 * into the log files.  Logging of PHI can be globally disabled by setting the org.apache.camel.mllp.logPHI system
 * property.  The property is evaluated using Boolean.parseBoolean.
 * <p/>
 */
public final class MllpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(MllpUtil.class);

    private MllpUtil() {
    }

    /**
     * Open the MLLP frame by reading from the Socket until the begging of the frame is found.
     * <p/>
     * If any errors occur (including MLLP frame errors) while opening the frame, the socket will be closed and an
     * Exception will be thrown.
     *
     * @param socket the Socket to read
     * @throws SocketTimeoutException    thrown if a timeout occurs while looking for the beginning of the MLLP frame, but
     *                                   nothing is yet available - this is NOT an error condition
     * @throws MllpCorruptFrameException if the MLLP Frame is corrupted in some way
     * @throws MllpException             for other unexpected error conditions
     */
    public static void openFrame(Socket socket) throws SocketTimeoutException, MllpCorruptFrameException, MllpException {
        if (socket.isConnected() && !socket.isClosed()) {
            InputStream socketInputStream = MllpUtil.getInputStream(socket);

            int readByte;
            try {
                readByte = socketInputStream.read();
                switch (readByte) {
                case START_OF_BLOCK:
                    return;
                case END_OF_STREAM:
                    resetConnection(socket);
                    return;
                default:
                    // Continue on and process the out-of-frame data
                }
            } catch (SocketTimeoutException normaTimeoutEx) {
                // Just pass this on - the caller will wrap it in a MllpTimeoutException
                throw normaTimeoutEx;
            } catch (IOException unexpectedException) {
                LOG.error("Unexpected Exception occurred opening MLLP frame - resetting the connection");
                MllpUtil.resetConnection(socket);
                throw new MllpException("Unexpected Exception occurred opening MLLP frame", unexpectedException);
            }

            /*
             From here on, we're in a bad frame state.  Read what's left in the socket, close the connection and
             return the out-of-frame data.
              */
            ByteArrayOutputStream outOfFrameData = new ByteArrayOutputStream();
            outOfFrameData.write(readByte);

            try {
                while (true) {
                    readByte = socketInputStream.read();
                    switch (readByte) {
                    case END_OF_STREAM:
                        if (isLogPHIEnabled(LOG)) {
                            LOG.error("END_OF_STREAM read while looking for the beginning of the MLLP frame, and "
                                            + "out-of-frame data had been read - resetting connection and eating out-of-frame data: {}",
                                    outOfFrameData.toString().replace('\r', '\n'));
                        } else {
                            LOG.error("END_OF_STREAM read while looking for the beginning of the MLLP frame, and out-of-frame data had been read - resetting connection and eating out-of-frame data");
                        }
                        resetConnection(socket);

                        throw new MllpCorruptFrameException("END_OF_STREAM read while looking for the beginning of the MLLP frame", outOfFrameData.toByteArray());
                    case START_OF_BLOCK:
                        if (isLogPHIEnabled(LOG)) {
                            LOG.warn("The beginning of the MLLP frame was preceded by out-of-frame data - eating data: {}", outOfFrameData.toString().replace('\r', '\n'));
                        } else {
                            LOG.warn("The beginning of the MLLP frame was preceded by out-of-frame data - eating data");
                        }

                        throw new MllpCorruptFrameException("The beginning of the MLLP frame was preceded by out-of-frame data", outOfFrameData.toByteArray());
                    default:
                        // still reading out-of-frame data
                        outOfFrameData.write(readByte);
                        break;
                    }
                }
            } catch (SocketTimeoutException timeoutEx) {
                if (isLogPHIEnabled(LOG)) {
                    LOG.error("Timeout looking for the beginning of the MLLP frame, and out-of-frame data had been read - resetting connection and eating out-of-frame data: {}",
                            outOfFrameData.toString().replace('\r', '\n'));
                } else {
                    LOG.error("Timeout looking for the beginning of the MLLP frame, and out-of-frame data had been read - resetting connection and eating out-of-frame data");
                }

                resetConnection(socket);

                throw new MllpCorruptFrameException("Timeout looking for the beginning of the MLLP frame, and out-of-frame data had been read", outOfFrameData.toByteArray());
            } catch (IOException e) {
                if (isLogPHIEnabled(LOG)) {
                    LOG.error("Exception encountered looking for the beginning of the MLLP frame, and out-of-frame data had been read - resetting connection and eating out-of-frame data: {}",
                            outOfFrameData.toString().replace('\r', '\n'));
                } else {
                    LOG.error("Exception encountered looking for the beginning of the MLLP frame, and out-of-frame data had been read - resetting connection and eating out-of-frame data");
                }

                resetConnection(socket);

                throw new MllpCorruptFrameException("Exception encountered looking for the beginning of the MLLP frame, and out-of-frame data had been read", outOfFrameData.toByteArray());
            }
        }
    }

    /**
     * Close a MLLP frame by reading from the socket until the end of the frame is found.
     * <p/>
     * The method assumes the MLLP frame has already been opened and the first byte available
     * will be the first byte of the framed message.
     * <p/>
     * The method consumes the END_OF_BLOCK and END_OF_DATA bytes from the stream before returning the payload
     * <p/>
     * If any errors occur (including MLLP frame errors) while opening the frame, the socket will be closed and an
     * Exception will be thrown.
     *
     * @param socket the Socket to be read
     * @return the payload of the MLLP-Enveloped message as a byte[]
     * @throws MllpTimeoutException      thrown if a timeout occurs while closing the MLLP frame
     * @throws MllpCorruptFrameException if the MLLP Frame is corrupted in some way
     * @throws MllpException             for other unexpected error conditions
     */
    public static byte[] closeFrame(Socket socket) throws MllpTimeoutException, MllpCorruptFrameException, MllpException {
        if (socket.isConnected() && !socket.isClosed()) {
            InputStream socketInputStream = MllpUtil.getInputStream(socket);
            // TODO:  Come up with an intelligent way to size this stream
            ByteArrayOutputStream payload = new ByteArrayOutputStream(4096);
            try {
                while (true) {
                    int readByte = socketInputStream.read();
                    switch (readByte) {
                    case END_OF_STREAM:
                        if (isLogPHIEnabled(LOG)) {
                            LOG.error("END_OF_STREAM read while looking for the end of the MLLP frame - resetting connection and eating data: {}", payload.toString().replace('\r', '\n'));
                        } else {
                            LOG.error("END_OF_STREAM read while looking for the end of the MLLP frame - resetting connection and eating data");
                        }

                        resetConnection(socket);

                        throw new MllpCorruptFrameException("END_OF_STREAM read while looking for the end of the MLLP frame", payload.size() > 0 ? payload.toByteArray() : null);
                    case START_OF_BLOCK:
                        if (isLogPHIEnabled(LOG)) {
                            LOG.error("A new MLLP frame was opened before the previous frame was closed - resetting connection and eating data: {}", payload.toString().replace('\r', '\n'));
                        } else {
                            LOG.error("A new MLLP frame was opened before the previous frame was closed - resetting connection and eating data");
                        }

                        resetConnection(socket);

                        throw new MllpCorruptFrameException("A new MLLP frame was opened before the previous frame was closed", payload.size() > 0 ? payload.toByteArray() : null);
                    case END_OF_BLOCK:
                        if (END_OF_DATA != socketInputStream.read()) {
                            if (isLogPHIEnabled(LOG)) {
                                LOG.error("The MLLP frame was partially closed - END_OF_BLOCK was not followed by END_OF_DATA - resetting connection and eating data: {}",
                                        payload.toString().replace('\r', '\n'));
                            } else {
                                LOG.error("The MLLP frame was partially closed - END_OF_BLOCK was not followed by END_OF_DATA - resetting connection and eating data");
                            }

                            resetConnection(socket);

                            throw new MllpCorruptFrameException("The MLLP frame was partially closed - END_OF_BLOCK was not followed by END_OF_DATA",
                                    payload.size() > 0 ? payload.toByteArray() : null);
                        }
                        return payload.toByteArray();
                    default:
                        // log.trace( "Read Character: {}", (char)readByte );
                        payload.write(readByte);
                    }
                }
            } catch (SocketTimeoutException timeoutEx) {
                if (0 < payload.size()) {
                    if (isLogPHIEnabled(LOG)) {
                        LOG.error("Timeout looking for the end of the MLLP frame - resetting connection and eating data: {}", payload.toString().replace('\r', '\n'));
                    } else {
                        LOG.error("Timeout looking for the end of the MLLP frame - resetting connection and eating data");
                    }
                } else {
                    LOG.error("Timeout looking for the end of the MLLP frame - resetting connection");
                }

                resetConnection(socket);

                throw new MllpCorruptFrameException("Timeout looking for the end of the MLLP frame", payload.size() > 0 ? payload.toByteArray() : null, timeoutEx);
            } catch (IOException ioEx) {
                if (0 < payload.size()) {
                    if (isLogPHIEnabled(LOG)) {
                        LOG.error("Exception encountered looking for the end of the MLLP frame - resetting connection and eating data: {}", payload.toString().replace('\r', '\n'));
                    } else {
                        LOG.error("Exception encountered looking for the end of the MLLP frame - resetting connection and eating data");
                    }
                } else {
                    LOG.error("Exception encountered looking for the end of the MLLP frame - resetting connection");
                }

                resetConnection(socket);

                throw new MllpException("Exception encountered looking for the end of the MLLP frame", payload.size() > 0 ? payload.toByteArray() : null, ioEx);
            }
        }

        return null;
    }

    /**
     * Write a MLLP-Framed payload to the Socket
     *
     * @param socket  the Socket to write the payload
     * @param payload the MLLP payload
     * @return true if write was successful; false otherwise
     * @throws MllpWriteException if the write fails
     * @throws MllpException      for other unexpected error conditions
     */
    public static void writeFramedPayload(Socket socket, byte[] payload) throws MllpException {
        if (socket.isConnected() && !socket.isClosed()) {
            OutputStream outputStream;
            try {
                outputStream = new BufferedOutputStream(socket.getOutputStream(), payload.length + 4);
            } catch (IOException ioEx) {
                LOG.error("Error Retrieving OutputStream from Socket - resetting connection");

                resetConnection(socket);

                throw new MllpException("Error Retrieving OutputStream from Socket", ioEx);
            }

            if (null != outputStream) {
                try {
                    outputStream.write(START_OF_BLOCK);
                    outputStream.write(payload, 0, payload.length);
                    outputStream.write(END_OF_BLOCK);
                    outputStream.write(END_OF_DATA);
                    outputStream.flush();
                } catch (IOException ioEx) {
                    LOG.error("Error writing MLLP payload - resetting connection");

                    resetConnection(socket);

                    throw new MllpWriteException("Error writing MLLP payload", payload, ioEx);
                }
            }
        }
    }

    public static void closeConnection(Socket socket) {
        if (null != socket) {
            if (!socket.isClosed()) {
                try {
                    socket.shutdownInput();
                } catch (Exception ex) {
                    LOG.warn("Exception encountered shutting down the input stream on the client socket", ex);
                }

                try {
                    socket.shutdownOutput();
                } catch (Exception ex) {
                    LOG.warn("Exception encountered shutting down the output stream on the client socket", ex);
                }

                try {
                    socket.close();
                } catch (Exception ex) {
                    LOG.warn("Exception encountered closing the client socket", ex);
                }
            }
        }
    }

    public static void resetConnection(Socket socket) {
        if (null != socket) {
            try {
                socket.setSoLinger(true, 0);
            } catch (Exception ex) {
                LOG.warn("Exception encountered setting SO_LINGER to 0 on the socket to force a reset", ex);
            }

            try {
                socket.close();
            } catch (Exception ex) {
                LOG.warn("Exception encountered closing the client socket", ex);
            }

        }

    }

    /**
     * Retrieve the InputStream from the Socket
     * <p/>
     * Private utility method that catches IOExceptions when retrieving the InputStream
     *
     * @param socket Socket to get the InputStream from
     * @return the InputStream for the Socket
     * @throws MllpException when unexpected conditions occur
     */
    private static InputStream getInputStream(Socket socket) throws MllpException {
        InputStream socketInputStream = null;
        try {
            socketInputStream = socket.getInputStream();
        } catch (IOException ioEx) {
            throw new MllpException("Error Retrieving InputStream from Socket", ioEx);
        }

        return socketInputStream;
    }

    private static boolean isLogPHIEnabled(Logger targetLogger) {
        if (targetLogger.isDebugEnabled()) {
            if (Boolean.parseBoolean(System.getProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true"))) {
                return true;
            }
        }

        return false;
    }

}
