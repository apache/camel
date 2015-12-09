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

import org.apache.camel.component.mllp.MllpEnvelopeException;
import org.apache.camel.component.mllp.MllpException;
import org.apache.camel.component.mllp.MllpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.apache.camel.component.mllp.impl.MllpConstants.*;

/**
 * NOTES:
 * <p/>
 * Switch to using a ByteArrayOutputStream for reading.  The String value can then be obtained
 * using ByteArrayOutputStream.toString( charset.name() )
 * <p/>
 * TODO:  Pull the read envelope stuff out so it can be used by both reading message and reading acknowledgement
 */
public class MllpSocketUtil {
    static Logger log = LoggerFactory.getLogger(MllpSocketUtil.class);

    static public byte[] readEnvelopedMessageBytes(Socket clientSocket) throws MllpException {
        boolean throwExceptionOnTimeout = false;
        byte[] messageBytes = null;

        InputStream socketInputStream = MllpSocketUtil.getInputStream(clientSocket);

        if (findStartOfBlock(socketInputStream, throwExceptionOnTimeout)) {
            messageBytes = readThroughEndOfBlock(socketInputStream);
        }

        return messageBytes;
    }

    static public byte[] readEnvelopedAcknowledgementBytes(Socket clientSocket) throws MllpException {
        boolean throwExceptionOnTimeout = true;
        byte[] acknowledgementBytes = null;

        InputStream socketInputStream = MllpSocketUtil.getInputStream(clientSocket);

        if (findStartOfBlock(socketInputStream, throwExceptionOnTimeout)) {
            acknowledgementBytes = readThroughEndOfBlock(socketInputStream);
        }

        return acknowledgementBytes;
    }

    static public void writeEnvelopedMessageBytes(Socket clientSocket, byte[] hl7MessageBytes) throws IOException {
        OutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream());

        outputStream.write(START_OF_BLOCK);
        outputStream.write(hl7MessageBytes, 0, hl7MessageBytes.length);
        outputStream.write(END_OF_BLOCK);
        outputStream.write(END_OF_DATA);
        outputStream.flush();
    }

    static public InputStream getInputStream(Socket clientSocket) throws MllpException {
        InputStream socketInputStream;
        try {
            socketInputStream = clientSocket.getInputStream();
        } catch (IOException ioEx) {
            //  The socket is closed, the socket is not connected, or the socket input has been shutdown
            // TODO:  Figure out what to do here
            throw new MllpException("Error retrieving input stream from client socket", ioEx);
        }

        return socketInputStream;
    }

    static public boolean findStartOfBlock(InputStream socketInputStream, boolean throwExceptionOnTimeout) throws MllpException {
        boolean foundStartOfBlock = false;

        int readByte = END_OF_STREAM;
        try {
            readByte = socketInputStream.read();
        } catch (SocketTimeoutException timeoutEx) {
            if (throwExceptionOnTimeout) {
                throw new MllpTimeoutException("Timeout waiting for START_OF_BLOCK", timeoutEx);
            } else {
                log.debug("Timeout looking for START_OF_BLOCK - no big deal");
            }
            return false;
        } catch (IOException e) {
            // TODO: Figure out what to do here
            e.printStackTrace();
        }

        if ( START_OF_BLOCK == readByte ) {
            foundStartOfBlock = true;
        } else if (END_OF_STREAM == readByte) {
            // Socket/Stream has been closed on the other side - close our side
            try {
                socketInputStream.close();
            } catch (IOException ioEx) {
                // TODO: Figure out what to do here
                log.warn("Exception encountered closing socket after read attempt looking for START_OF_BLOCK returned END_OF_STREAM", ioEx);
            }
            throw new MllpException("TCP connection has been closed by peer");
        } else {
            // We have out-of-band data, so read it and eat it
            StringBuilder outOfBandData = new StringBuilder();
            do {
                outOfBandData.append((char) readByte);
                try {
                    readByte = socketInputStream.read();
                } catch (SocketTimeoutException timeoutEx) {
                    // No more out-of-band data
                    log.warn( "Eating out-of-band data after read timeout\n{}", outOfBandData.toString().replace('\r', '\n'));
                    return false;
                } catch (IOException e) {
                    // TODO:  Figure out what to do with this one
                    e.printStackTrace();
                }
            } while (END_OF_STREAM != readByte && START_OF_BLOCK != readByte);
            // Now we're either at the start of the message or end of stream
            switch (readByte) {
                case START_OF_BLOCK:
                    // This is what we want
                    foundStartOfBlock = true;
                    break;
                case END_OF_STREAM:
                    // Something bad happened
                    try {
                        socketInputStream.close();
                    } catch (IOException ioEx) {
                        log.warn("Exception encountered closing socket after read attempt for out-of-band data returned END_OF_STREAM", ioEx);
                    }
            }
        }

        return foundStartOfBlock;
    }

    static public byte[] readThroughEndOfBlock(InputStream socketInputStream) throws MllpException {
        // TODO:  Come up with an intelligent way to size this stream
        ByteArrayOutputStream hl7MessageBytes = new ByteArrayOutputStream(4096);
        try {
            boolean readingMessage = true;
            while (readingMessage) {
                int nextByte = socketInputStream.read();
                switch (nextByte) {
                    case -1:
                        throw new MllpEnvelopeException("Reached end of stream before END_OF_BLOCK");
                    case START_OF_BLOCK:
                        throw new MllpEnvelopeException("Received START_OF_BLOCK before END_OF_BLOCK");
                    case END_OF_BLOCK:
                        if (END_OF_DATA != socketInputStream.read()) {
                            throw new MllpEnvelopeException("END_OF_BLOCK was not followed by END_OF_DATA");
                        }
                        readingMessage = false;
                        break;
                    default:
                        hl7MessageBytes.write(nextByte);
                }
            }
        } catch (SocketTimeoutException timeoutEx) {
            if (hl7MessageBytes.size() > 0) {
                log.error("Timeout reading message after receiveing partial payload:\n{}", hl7MessageBytes.toString().replace('\r', '\n'));
            } else {
                log.error("Timout reading message - no data received");
            }
            try {
                socketInputStream.close();
            } catch (IOException ioEx) {
                throw new MllpException("Error Closing socket after message read timeout", ioEx);
            }
            throw new MllpTimeoutException("Timeout reading message", timeoutEx);
        } catch (IOException e) {
            log.error("Unable to read HL7 message", e);
            throw new MllpException("Unable to read HL7 message", e);
        }

        return hl7MessageBytes.toByteArray();
    }
}
