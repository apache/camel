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

import org.apache.camel.component.mllp.MllpEnvelopeException;
import org.apache.camel.component.mllp.MllpException;
import org.apache.camel.component.mllp.MllpRequestTimeoutException;
import org.apache.camel.component.mllp.MllpResponseTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;

import static org.apache.camel.component.mllp.impl.MllpConstants.*;

public class MllpSocketUtil {
    static Logger log = LoggerFactory.getLogger(MllpSocketUtil.class);

    static public String readEnvelopedMessage(Charset charset, Socket clientSocket, InputStream inputStream) throws MllpException {
        // TODO:  This needs to be Charset aware
        // Read the HL7 Message
        StringBuilder hl7MessageBuilder = new StringBuilder();

        try {
            int inByte = inputStream.read();
            if (inByte != START_OF_BLOCK) {
                // We have out-of-band data
                StringBuilder outOfBandData = new StringBuilder();
                do {
                    if ( -1 == inByte ) {
                        String errorMessage = "End of buffer reached before START_OF_BLOCK Found";
                        log.warn("{}\n{}", errorMessage, outOfBandData.toString());
                        throw new MllpEnvelopeException(errorMessage);
                    } else {
                        outOfBandData.append((char) inByte);
                        inByte = inputStream.read();
                    }
                } while (START_OF_BLOCK != inByte );
                log.warn("Eating out-of-band data: {}", outOfBandData.toString());

            }


            if (START_OF_BLOCK != inByte) {
                throw new MllpEnvelopeException("Message did not start with START_OF_BLOCK");
            }

            boolean readingMessage = true;
            while (readingMessage) {
                int nextByte = inputStream.read();
                switch (nextByte) {
                    case -1:
                         throw new MllpEnvelopeException("Reached end of stream before END_OF_BLOCK");
                    case START_OF_BLOCK:
                        throw new MllpEnvelopeException("Received START_OF_BLOCK before END_OF_BLOCK");
                    case END_OF_BLOCK:
                        if (END_OF_DATA != inputStream.read()) {
                            throw new MllpEnvelopeException("END_OF_BLOCK was not followed by END_OF_DATA");
                        }
                        readingMessage = false;
                        break;
                    default:
                        hl7MessageBuilder.append((char) nextByte);
                }
            }
        } catch ( SocketTimeoutException timeoutEx ) {
            if ( hl7MessageBuilder.length() > 0 ) {
                log.error( "Timeout reading message after receiveing partial payload:\n{}", hl7MessageBuilder.toString().replace('\r', '\n'));
            } else {
                log.error( "Timout reading message - no data received");
            }
            try {
                clientSocket.close();
            } catch (IOException ioEx) {
                throw new MllpException( "Error Closing socket after message read timeout", ioEx);
            }
            throw new MllpRequestTimeoutException("Timeout reading message", timeoutEx);
        } catch (IOException e) {
            log.error("Unable to read HL7 message", e);
            throw new MllpException("Unable to read HL7 message", e);
        }
        return hl7MessageBuilder.toString();
    }

    static public String readEnvelopedAcknowledgement(Charset charset, Socket clientSocket, InputStream inputStream) throws MllpException {
        StringBuilder acknowledgementBuilder = new StringBuilder();
        // TODO:  This needs to be Charset aware
        try {
            int inByte = inputStream.read();
            if ( inByte != START_OF_BLOCK) {
                // We have out-of-band data
                StringBuilder outOfBandData = new StringBuilder();
                do {
                    if ( END_OF_STREAM == inByte ) {
                        String errorMessage = "END_OF_STREAM reached before START_OF_BLOCK Found";
                        log.warn("{}\n{}", errorMessage, outOfBandData.toString());
                        throw new MllpResponseTimeoutException(errorMessage);
                    } else {
                        outOfBandData.append((char) inByte);
                        inByte = inputStream.read();
                    }
                } while ( START_OF_BLOCK != inByte );
                log.warn( "Eating out-of-band data: {}", outOfBandData.toString());
            }

            if (START_OF_BLOCK != inByte) {
                throw new MllpEnvelopeException("Message did not start with START_OF_BLOCK");
            }
            boolean readingMessage = true;
            while (readingMessage) {
                int nextByte = inputStream.read();
                switch (nextByte) {
                    case -1:
                        throw new MllpEnvelopeException("Reached end of stream before END_OF_BLOCK");
                    case START_OF_BLOCK:
                        throw new MllpEnvelopeException("Received START_OF_BLOCK before END_OF_BLOCK");
                    case END_OF_BLOCK:
                        if (END_OF_DATA != inputStream.read()) {
                            throw new MllpEnvelopeException("END_OF_BLOCK was not followed by END_OF_DATA");
                        }
                        readingMessage = false;
                        break;
                    default:
                        acknowledgementBuilder.append((char) nextByte);
                }
            }
        } catch (SocketTimeoutException timeoutEx ) {
            log.error( "Timout reading response");
            throw new MllpResponseTimeoutException( "Timeout reading response", timeoutEx);
        } catch (IOException e) {
            log.error("Unable to read HL7 acknowledgement", e);
            throw new MllpEnvelopeException("Unable to read HL7 acknowledgement", e);
        }

        return acknowledgementBuilder.toString();
    }

    static public void writeEnvelopedMessage(String hl7Message, Charset charset, Socket clientSocket, BufferedOutputStream outputStream) throws IOException {
        byte[] hl7Bytes = hl7Message.getBytes(charset);

        outputStream.write(START_OF_BLOCK);
        outputStream.write(hl7Bytes, 0, hl7Bytes.length);
        outputStream.write(END_OF_BLOCK);
        outputStream.write(END_OF_DATA);
        outputStream.flush();

    }
}
