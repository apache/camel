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

import org.apache.camel.component.mllp.internal.Hl7Util;

/**
 * Base class for all MLLP Exceptions, and also used as a generic MLLP exception
 */
public class MllpException extends Exception {
    final byte[] hl7MessageBytes;
    final byte[] hl7AcknowledgementBytes;

    // No-payload constructors
    public MllpException(String message) {
        this(message, (byte[]) null, (byte[]) null, (Throwable) null);
    }

    public MllpException(String message, Throwable cause) {
        this(message, (byte[]) null, (byte[]) null, cause);
    }

    // Message only payload constructors
    public MllpException(String message, byte[] hl7MessageBytes) {
        this(message, hl7MessageBytes, (byte[]) null, (Throwable) null);
    }

    public MllpException(String message, byte[] hl7MessageBytes, Throwable cause) {
        this(message, hl7MessageBytes, (byte[]) null, cause);
    }

    // Message payload and Acknowledgement payload constructors
    public MllpException(String message, byte[] hl7MessageBytes, byte[] hl7AcknowledgementBytes) {
        this(message, hl7MessageBytes, hl7AcknowledgementBytes, (Throwable) null);
    }

    public MllpException(String message, byte[] hl7MessageBytes, byte[] hl7AcknowledgementBytes, Throwable cause) {
        super(message, cause);

        if (hl7MessageBytes != null && hl7MessageBytes.length > 0) {
            this.hl7MessageBytes = hl7MessageBytes;
        } else {
            this.hl7MessageBytes = null;
        }

        if (hl7AcknowledgementBytes != null && hl7AcknowledgementBytes.length > 0) {
            this.hl7AcknowledgementBytes = hl7AcknowledgementBytes;
        } else {
            this.hl7AcknowledgementBytes = null;
        }
    }


    /**
     * Determine if there is an HL7 message payload associated with this exception.
     *
     * @return true if this exception contains an HL7 message payload; false otherwise
     */
    public boolean hasHl7MessageBytes() {
        return hl7MessageBytes != null && hl7MessageBytes.length > 0;
    }


    /**
     * Get the HL7 message payload associated with this exception, if any.
     *
     * @return the HL7 message payload; null if a message payload is not associated with this exception
     */
    public byte[] getHl7MessageBytes() {
        return hl7MessageBytes;
    }

    /**
     * Determine if there is an HL7 message payload associated with this exception.
     *
     * @return true if this exception contains an HL7 message payload; false otherwise
     */
    public boolean hasHl7AcknowledgementBytes() {
        return hl7AcknowledgementBytes != null && hl7AcknowledgementBytes.length > 0;
    }

    /**
     * Get the HL7 acknowledgement payload associated with this exception, if any.
     *
     * @return the HL7 acknowledgement payload; null if an acknowledgement payload is not associated with this exception
     */
    public byte[] getHl7AcknowledgementBytes() {
        return hl7AcknowledgementBytes;
    }

    /**
     * Override the base version of this method, and include the HL7 Message and Acknowledgement, if any
     *
     * @return the detail message of this MLLP Exception
     */
    @Override
    public String getMessage() {
        String answer;

        if (hasHl7MessageBytes() || hasHl7AcknowledgementBytes()) {
            String parentMessage = super.getMessage();

            StringBuilder messageBuilder = new StringBuilder(parentMessage.length()
                + (hasHl7MessageBytes() ? hl7MessageBytes.length : 0)
                + (hasHl7AcknowledgementBytes() ? hl7AcknowledgementBytes.length : 0)
            );

            messageBuilder.append(parentMessage);

            if (hasHl7MessageBytes()) {
                messageBuilder.append("\n\t{hl7Message [")
                    .append(hl7MessageBytes.length)
                    .append("] = ");

                Hl7Util.appendBytesAsPrintFriendlyString(messageBuilder, hl7MessageBytes, 0, hl7MessageBytes.length);

                messageBuilder.append('}');
            }

            if (hasHl7AcknowledgementBytes()) {
                messageBuilder.append("\n\t{hl7Acknowledgement [")
                    .append(hl7AcknowledgementBytes.length)
                    .append("] = ");

                Hl7Util.appendBytesAsPrintFriendlyString(messageBuilder, hl7AcknowledgementBytes, 0, hl7AcknowledgementBytes.length);

                messageBuilder.append('}');
            }

            answer = messageBuilder.toString();
        } else {
            answer = super.getMessage();
        }

        return answer;
    }

}
