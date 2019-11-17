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
package org.apache.camel.processor.mllp;

import org.apache.camel.component.mllp.internal.Hl7Util;

/*
 * Exception thrown by the HL7AcknowledgmentGenerator in the event of a failure.
 */
public class Hl7AcknowledgementGenerationException extends Exception {
    private final byte[] hl7MessageBytes;

    public Hl7AcknowledgementGenerationException(String message) {
        super(message);
        this.hl7MessageBytes = null;
    }

    public Hl7AcknowledgementGenerationException(String message, byte[] hl7MessageBytes) {
        super(message);
        this.hl7MessageBytes = hl7MessageBytes;
    }

    public Hl7AcknowledgementGenerationException(String message, byte[] hl7MessageBytes, Throwable cause) {
        super(message, cause);
        this.hl7MessageBytes = hl7MessageBytes;
    }


    public boolean hasHl7MessageBytes() {
        return hl7MessageBytes != null && hl7MessageBytes.length > 0;
    }

    public byte[] getHl7MessageBytes() {
        return hl7MessageBytes;
    }

    /**
     * Override the base version of this method, and include the HL7 Message and Acknowledgement, if any
     *
     * @return the detail message of this MLLP Exception
     */
    @Override
    public String getMessage() {
        if (hasHl7MessageBytes()) {
            String parentMessage = super.getMessage();

            StringBuilder messageBuilder = new StringBuilder(parentMessage.length() + hl7MessageBytes.length);

            messageBuilder.append(parentMessage).append("\n\t{hl7MessageBytes [")
                .append(hl7MessageBytes.length)
                .append("] = ");

            Hl7Util.appendBytesAsPrintFriendlyString(messageBuilder, hl7MessageBytes, 0, hl7MessageBytes.length);

            messageBuilder.append('}');

            return messageBuilder.toString();
        }

        return super.getMessage();
    }
}
