/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp.impl;

import org.apache.camel.component.mllp.*;
import org.apache.camel.component.properties.SysPropertiesFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.apache.camel.component.mllp.MllpEndpoint.*;

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
public class MllpUtil {
    static Logger log = LoggerFactory.getLogger(MllpUtil.class);

    /**
     * Open the MLLP frame by reading from the Socket until the begging of the frame is found.
     * <p/>
     * If any errors occur (including MLLP frame errors) while opening the frame, the socket will be closed and an
     * Exception will be thrown.
     *
     * @param socket the Socket to read
     * @throws SocketTimeoutException thrown if a timeout occurs while looking for the beginning of the MLLP frame, but
     *                                nothing is yet available - this is NOT an error condition
     * @throws MllpCorruptFrameException     if the MLLP Frame is corrupted in some way
     * @throws MllpException          for other unexpected error conditions
     */
    static public void openFrame(Socket socket) throws SocketTimeoutException, MllpCorruptFrameException, MllpException {
        if (socket.isConnected() && !socket.isClosed()) {
            InputStream socketInputStream = MllpUtil.getInputStream(socket);

            int readByte;
            try {
                readByte = socketInputStream.read();
                switch( readByte ) {
                    case START_OF_BLOCK:
                        return;
                    case END_OF_STREAM:
                        try {
                            socket.close();
                        } catch (Exception closeEx) {
                            log.warn("Exception encountered closing socket after reading END_OF_STREAM while opening frame");
                        }
                        return;
                }
            } catch (SocketTimeoutException normaTimeoutEx) {
                // Just pass this on - the caller will wrap it in a MllpTimeoutException
                throw normaTimeoutEx;
            } catch (IOException unexpectedException) {
                log.error("Unexpected Exception occurred opening MLLP frame - closing socket");
                try {
                    socket.close();
                } catch (Exception closeEx) {
                    log.warn("Exception encountered closing socket after unexpected exception opening frame");
                }

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
                            if (isLogPHIEnabled(log)) {
                                log.error("END_OF_STREAM read while looking for the beginning of the MLLP frame, and out-of-frame data had been read - closing socket and eating out-of-frame data: {}", outOfFrameData.toString().replace('\r', '\n'));
                            } else {
                                log.error("END_OF_STREAM read while looking for the beginning of the MLLP frame, and out-of-frame data had been read - closing socket and eating out-of-frame data");
                            }

                            try {
                                socketInputStream.close();
                            } catch (IOException closeEx) {
                                log.warn("Exception encountered closing Socket after read attempt looking for the beginning of the MLLP frame returned END_OF_STREAM", closeEx);
                            }

                            throw new MllpCorruptFrameException("END_OF_STREAM read while looking for the beginning of the MLLP frame", outOfFrameData.toByteArray());
                        case START_OF_BLOCK:
                            if (isLogPHIEnabled(log)) {
                                log.warn("The beginning of the MLLP frame was preceded by out-of-frame data - eating data: {}", outOfFrameData.toString().replace('\r', '\n'));
                            } else {
                                log.warn("The beginning of the MLLP frame was preceded by out-of-frame data - eating data");
                            }

                            throw new MllpCorruptFrameException("The beginning of the MLLP frame was preceded by out-of-frame data", outOfFrameData.toByteArray());
                        default:
                            // still reading out-of-frame data
                            outOfFrameData.write(readByte);
                            break;
                    }
                }
            } catch (SocketTimeoutException timeoutEx) {
                if (isLogPHIEnabled(log)) {
                    log.error("Timeout looking for the beginning of the MLLP frame, and out-of-frame data had been read - closing socket and eating out-of-frame data: {}", outOfFrameData.toString().replace('\r', '\n'));
                } else {
                    log.error("Timeout looking for the beginning of the MLLP frame, and out-of-frame data had been read - closing socket and eating out-of-frame data");
                }

                try {
                    socket.close();
                } catch (IOException closeEx) {
                    log.warn("Exception encountered closing socket after Timeout looking for the beginning of the MLLP frame, and out-of-frame data had been read", closeEx);
                }

                throw new MllpCorruptFrameException("Timeout looking for the beginning of the MLLP frame, and out-of-frame data had been read", outOfFrameData.toByteArray());
            } catch (IOException e) {
                if (isLogPHIEnabled(log)) {
                    log.error("Exception encountered looking for the beginning of the MLLP frame, and out-of-frame data had been read - closing socket and eating out-of-frame data: {}", outOfFrameData.toString().replace('\r', '\n'));
                } else {
                    log.error("Exception encountered looking for the beginning of the MLLP frame, and out-of-frame data had been read - closing socket and eating out-of-frame data");
                }

                try {
                    socket.close();
                } catch (IOException closeEx) {
                    log.warn("Exception encountered closing socket after exception was encountered looking for the beginning of the MLLP frame", closeEx);
                }

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
     * @throws MllpTimeoutException thrown if a timeout occurs while closing the MLLP frame
     * @throws MllpCorruptFrameException   if the MLLP Frame is corrupted in some way
     * @throws MllpException        for other unexpected error conditions
     */
    static public byte[] closeFrame(Socket socket) throws MllpTimeoutException, MllpCorruptFrameException, MllpException {
        if (socket.isConnected() && !socket.isClosed()) {
            InputStream socketInputStream = MllpUtil.getInputStream(socket);
            // TODO:  Come up with an intelligent way to size this stream
            ByteArrayOutputStream payload = new ByteArrayOutputStream(4096);
            try {
                while (true) {
                    int readByte = socketInputStream.read();
                    switch (readByte) {
                        case END_OF_STREAM:
                            if (isLogPHIEnabled(log)) {
                                log.error("END_OF_STREAM read while looking for the end of the MLLP frame - closing socket and eating data: {}", payload.toString().replace('\r', '\n'));
                            } else {
                                log.error("END_OF_STREAM read while looking for the end of the MLLP frame - closing socket and eating data");
                            }

                            try {
                                socketInputStream.close();
                            } catch (IOException closeEx) {
                                log.warn("Exception encountered closing Socket after read attempt looking for the end of the MLLP frame returned END_OF_STREAM", closeEx);
                            }

                            throw new MllpCorruptFrameException("END_OF_STREAM read while looking for the end of the MLLP frame", payload.size() > 0 ? payload.toByteArray() : null);
                        case START_OF_BLOCK:
                            if (isLogPHIEnabled(log)) {
                                log.error("A new MLLP frame was opened before the previous frame was closed - closing socket and eating data: {}", payload.toString().replace('\r', '\n'));
                            } else {
                                log.error("A new MLLP frame was opened before the previous frame was closed - closing socket and eating data");
                            }

                            try {
                                socketInputStream.close();
                            } catch (IOException closeEx) {
                                log.warn("Exception encountered closing Socket after a new MLLP frame was opened before the previous frame was closed", closeEx);
                            }

                            throw new MllpCorruptFrameException("A new MLLP frame was opened before the previous frame was closed", payload.size() > 0 ? payload.toByteArray() : null);
                        case END_OF_BLOCK:
                            if (END_OF_DATA != socketInputStream.read()) {
                                if (isLogPHIEnabled(log)) {
                                    log.error("The MLLP frame was partially closed - END_OF_BLOCK was not followed by END_OF_DATA - closing socket and eating data: {}", payload.toString().replace('\r', '\n'));
                                } else {
                                    log.error("The MLLP frame was partially closed - END_OF_BLOCK was not followed by END_OF_DATA - closing socket and eating data");
                                }

                                try {
                                    socketInputStream.close();
                                } catch (IOException closeEx) {
                                    log.warn("Exception encountered closing Socket after the MLLP frame was partially closed - END_OF_BLOCK was not followed by END_OF_DATA", closeEx);
                                }

                                throw new MllpCorruptFrameException("The MLLP frame was partially closed - END_OF_BLOCK was not followed by END_OF_DATA", payload.size() > 0 ? payload.toByteArray() : null);
                            }
                            return payload.toByteArray();
                        default:
                            payload.write(readByte);
                    }
                }
            } catch (SocketTimeoutException timeoutEx) {
                if (0 < payload.size()) {
                    if (isLogPHIEnabled(log)) {
                        log.error("Timeout looking for the end of the MLLP frame - closing socket and eating data: {}", payload.toString().replace('\r', '\n'));
                    } else {
                        log.error("Timeout looking for the end of the MLLP frame - closing socket and eating data");
                    }
                } else {
                    log.error("Timeout looking for the end of the MLLP frame - closing socket");
                }
                try {
                    socket.close();
                } catch (IOException closeEx) {
                    log.warn("Exception encountered closing socket after Timeout looking for the end of the MLLP frame", closeEx);
                }
                throw new MllpCorruptFrameException("Timeout looking for the end of the MLLP frame", payload.size() > 0 ? payload.toByteArray() : null, timeoutEx);
            } catch (IOException ioEx) {
                if (0 < payload.size()) {
                    if (isLogPHIEnabled(log)) {
                        log.error("Exception encountered looking for the end of the MLLP frame - closing socket and eating data: {}", payload.toString().replace('\r', '\n'));
                    } else {
                        log.error("Exception encountered looking for the end of the MLLP frame - closing socket and eating data");
                    }
                } else {
                    log.error("Exception encountered looking for the end of the MLLP frame - closing socket");
                }
                try {
                    socket.close();
                } catch (IOException closeEx) {
                    log.warn("Exception encountered closing socket after exception was encountered looking for the end of the MLLP frame", closeEx);
                }

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
    static public void writeFramedPayload(Socket socket, byte[] payload) throws MllpException {
        if (socket.isConnected() && !socket.isClosed()) {
            OutputStream outputStream = null;
            try {
                outputStream = new BufferedOutputStream(socket.getOutputStream(), payload.length + 4);
            } catch (IOException ioEx) {
                log.error("Error Retrieving OutputStream from Socket - closing Socket");
                if (!socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException closeEx) {
                        log.warn("Error closing Socket after retrieving output stream failed", closeEx);
                    }
                }

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
                    log.error("Error writing MLLP payload - closing Socket");
                    if (!socket.isClosed()) {
                        try {
                            socket.close();
                        } catch (IOException closeEx) {
                            log.warn("Exception encountered while closeing Socket after write failure", closeEx);
                        }
                    }
                    throw new MllpWriteException("Error writing MLLP payload", payload, ioEx);
                }
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
    static private InputStream getInputStream(Socket socket) throws MllpException {
        InputStream socketInputStream = null;
        try {
            socketInputStream = socket.getInputStream();
        } catch (IOException ioEx) {
            throw new MllpException("Error Retrieving InputStream from Socket", ioEx);
        }

        return socketInputStream;
    }


    static private boolean isLogPHIEnabled(Logger targetLogger) {
        String logPHIProperty = System.getProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true");
        if (targetLogger.isDebugEnabled()) {
            if ( Boolean.parseBoolean( System.getProperty(MllpComponent.MLLP_LOG_PHI_PROPERTY, "true")) ){
                return true;
            }
        }

        return false;
    }

}
