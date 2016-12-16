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

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.apache.camel.component.mllp.MllpAcknowledgementDeliveryException;
import org.apache.camel.component.mllp.MllpException;
import org.apache.camel.component.mllp.MllpWriteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_BLOCK;
import static org.apache.camel.component.mllp.MllpEndpoint.END_OF_DATA;
import static org.apache.camel.component.mllp.MllpEndpoint.START_OF_BLOCK;

public class MllpSocketWriter {

    static final byte[] PAYLOAD_TERMINATOR;

    static {
        PAYLOAD_TERMINATOR = new byte[2];
        PAYLOAD_TERMINATOR[0] = END_OF_BLOCK;
        PAYLOAD_TERMINATOR[1] = END_OF_DATA;
    }

    final Socket socket;
    final boolean acknowledgementWriter;

    Logger log = LoggerFactory.getLogger(this.getClass());

    public MllpSocketWriter(Socket socket, boolean acknowledgementWriter) {
        this.socket = socket;
        this.acknowledgementWriter = acknowledgementWriter;
    }

    public void writeEnvelopedPayload(byte[] hl7MessageBytes, byte[] hl7AcknowledgementBytes) throws MllpException {
        if (socket == null) {
            final String errorMessage = "Socket is null";
            if (isAcknowledgementWriter()) {
                throw new MllpAcknowledgementDeliveryException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            } else {
                throw new MllpWriteException(errorMessage, hl7MessageBytes);
            }
        } else if (!socket.isConnected()) {
            final String errorMessage = "Socket is not connected";
            if (isAcknowledgementWriter()) {
                throw new MllpAcknowledgementDeliveryException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            } else {
                throw new MllpWriteException(errorMessage, hl7MessageBytes);
            }
        } else if (socket.isClosed()) {
            final String errorMessage = "Socket is closed";
            if (isAcknowledgementWriter()) {
                throw new MllpAcknowledgementDeliveryException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            } else {
                throw new MllpWriteException(errorMessage, hl7MessageBytes);
            }
        }

        OutputStream socketOutputStream = null;
        try {
            socketOutputStream = socket.getOutputStream();
        } catch (IOException e) {
            final String errorMessage = "Failed to retrieve the OutputStream from the Socket";
            if (isAcknowledgementWriter()) {
                throw new MllpAcknowledgementDeliveryException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            } else {
                throw new MllpWriteException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            }
        }

        try {
            socketOutputStream.write(START_OF_BLOCK);
        } catch (IOException e) {
            final String errorMessage = "Failed to write the START_OF_BLOCK to the Socket's OutputStream";
            if (isAcknowledgementWriter()) {
                throw new MllpAcknowledgementDeliveryException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            } else {
                throw new MllpWriteException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            }
        }

        if (isAcknowledgementWriter()) {
            if (hl7AcknowledgementBytes == null) {
                log.warn("HL7 Acknowledgement payload is null - sending empty MLLP payload");
            } else if (hl7AcknowledgementBytes.length <= 0) {
                log.warn("HL7 Acknowledgement payload is empty - sending empty MLLP payload");
            } else {
                try {
                    socketOutputStream.write(hl7AcknowledgementBytes);
                } catch (IOException ioEx) {
                    throw new MllpAcknowledgementDeliveryException("Failed to write the HL7 Acknowledgement payload to the Socket's OutputStream", hl7MessageBytes, hl7AcknowledgementBytes, ioEx);
                }
            }
        } else {
            if (hl7MessageBytes == null) {
                log.warn("HL7 Message payload is null - sending empty MLLP payload");
            } else if (hl7MessageBytes.length <= 0) {
                log.warn("HL7 Message payload is empty - sending empty MLLP payload");
            } else {
                try {
                    socketOutputStream.write(hl7MessageBytes);
                } catch (IOException ioEx) {
                    throw new MllpWriteException("Failed to write the HL7 Message payload to the Socket's OutputStream", hl7MessageBytes, hl7AcknowledgementBytes, ioEx);
                }
            }
        }

        try {
            socketOutputStream.write(PAYLOAD_TERMINATOR);
            socketOutputStream.flush();
        } catch (IOException e) {
            final String errorMessage = "Failed to write the END_OF_BLOCK and END_OF_DATA to the Socket's OutputStream";
            if (isAcknowledgementWriter()) {
                throw new MllpAcknowledgementDeliveryException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            } else {
                throw new MllpWriteException(errorMessage, hl7MessageBytes, hl7AcknowledgementBytes);
            }
        }

    }

    public boolean isAcknowledgementWriter() {
        return acknowledgementWriter;
    }
}
