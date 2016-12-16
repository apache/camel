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
package org.apache.camel.component.mllp;

/**
 * Raised when a MLLP Producer does not receive a HL7 acknowledgement within the configured timespan
 */
public class MllpAcknowledgementTimeoutException extends MllpAcknowledgementException {

    static final String EXCEPTION_MESSAGE = "Timeout receiving HL7 Acknowledgement";

    public MllpAcknowledgementTimeoutException(byte[] hl7Message) {
        super(EXCEPTION_MESSAGE, hl7Message);
    }

    public MllpAcknowledgementTimeoutException(byte[] hl7Message, byte[] partialHl7Acknowledgement) {
        super(EXCEPTION_MESSAGE, hl7Message, partialHl7Acknowledgement);
    }

    public MllpAcknowledgementTimeoutException(byte[] hl7Message, Throwable cause) {
        super(EXCEPTION_MESSAGE, hl7Message, cause);
    }

    public MllpAcknowledgementTimeoutException(byte[] hl7Message, byte[] partialHl7Acknowledgement, Throwable cause) {
        super(EXCEPTION_MESSAGE, hl7Message, partialHl7Acknowledgement, cause);
    }

    public MllpAcknowledgementTimeoutException(String message, byte[] hl7Message) {
        super(message, hl7Message);
    }

    public MllpAcknowledgementTimeoutException(String message, byte[] hl7Message, byte[] partialHl7Acknowledgement) {
        super(message, hl7Message, partialHl7Acknowledgement);
    }

    public MllpAcknowledgementTimeoutException(String message, byte[] hl7Message, Throwable cause) {
        super(message, hl7Message, cause);
    }

    public MllpAcknowledgementTimeoutException(String message, byte[] hl7Message, byte[] partialHl7Acknowledgement, Throwable cause) {
        super(message, hl7Message, partialHl7Acknowledgement, cause);
    }

    /**
     * Get the HL7 acknowledgement payload associated with this exception, if any.
     *
     * @return If the timeout occurred while attempting to receive an HL7 Message, this will be null.  If the timeout
     * occurred while attempting to receive an HL7 Acknowledgement, this will be the HL7 Message.  If the timeout occurred
     * while attempting to complete the read of an HL7 message (i.e. part of the message has already been read), this
     * will be the partial acknowledgement payload that was read before the timeout.
     */
    public byte[] getHl7Acknowledgement() {
        return super.getHl7Acknowledgement();
    }

}
