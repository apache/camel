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
 * Base class for all MLLP Exceptions, and also used as a generic MLLP exception
 */
public class MllpException extends Exception {
    private final byte[] hl7Message;
    private final byte[] hl7Acknowledgement;

    public MllpException(String message) {
        super(message);
        this.hl7Message = null;
        this.hl7Acknowledgement = null;
    }

    public MllpException(String message, byte[] hl7Message) {
        super(message);
        this.hl7Message = (hl7Message != null && hl7Message.length > 0) ? hl7Message : null;
        this.hl7Acknowledgement = null;
    }

    public MllpException(String message, byte[] hl7Message, byte[] hl7Acknowledgement) {
        super(message);
        this.hl7Message = (hl7Message != null && hl7Message.length > 0) ? hl7Message : null;
        this.hl7Acknowledgement = (hl7Acknowledgement != null && hl7Acknowledgement.length > 0) ? hl7Acknowledgement : null;
    }

    public MllpException(String message, Throwable cause) {
        super(message, cause);
        this.hl7Message = null;
        this.hl7Acknowledgement = null;
    }

    public MllpException(String message, byte[] hl7Message, Throwable cause) {
        super(message, cause);
        this.hl7Message = (hl7Message != null && hl7Message.length > 0) ? hl7Message : null;
        this.hl7Acknowledgement = null;
    }

    public MllpException(String message, byte[] hl7Message, byte[] hl7Acknowledgement, Throwable cause) {
        super(message, cause);
        this.hl7Message = (hl7Message != null && hl7Message.length > 0) ? hl7Message : null;
        this.hl7Acknowledgement = (hl7Acknowledgement != null && hl7Acknowledgement.length > 0) ? hl7Acknowledgement : null;
    }

    /**
     * Get the HL7 message payload associated with this exception, if any.
     *
     * @return HL7 message payload
     */
    public byte[] getHl7Message() {
        return hl7Message;
    }

    /**
     * Get the HL7 acknowledgement payload associated with this exception, if any.
     *
     * @return HL7 acknowledgement payload
     */
    public byte[] getHl7Acknowledgement() {
        return hl7Acknowledgement;
    }

    /**
     * Override the base version of this method, and include the HL7 Message and Acknowledgement, if any
     *
     * @return the detail message of this MLLP Exception
     */
    @Override
    public String getMessage() {
        if (MllpComponent.isLogPhi()) {
            return String.format("%s \n\t{hl7Message= %s} \n\t{hl7Acknowledgement= %s}",
                    super.getMessage(), MllpComponent.covertBytesToPrintFriendlyString(hl7Message), MllpComponent.covertBytesToPrintFriendlyString(hl7Acknowledgement));
        } else {
            return super.getMessage();
        }
    }

    /**
     * Return the MLLP Payload that is most likely the cause of the Exception
     *
     * If the HL7 Acknowledgement is present, return it.  Otherwise, return the HL7 Message.
     *
     * @return the MLLP Payload with the framing error
     */
    public byte[] getMllpPayload() {
        return (hl7Acknowledgement != null  &&  hl7Acknowledgement.length > 0) ? hl7Acknowledgement : hl7Message;
    }

}
