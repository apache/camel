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
 * Raised when a MLLP Producer or Consumer encounter a timeout reading a message
 */
public class MllpTimeoutException extends MllpException {
    private final byte[] hl7Message;

    public MllpTimeoutException(String message, byte[] hl7Message) {
        super(message);
        this.hl7Message = hl7Message;
    }

    public MllpTimeoutException(String message, byte[] hl7Message, Throwable cause) {
        super(message, cause);
        this.hl7Message = hl7Message;
    }

    public byte[] getHl7Message() {
        return hl7Message;
    }

    @Override
    public String getMessage() {
        if (isLogPhi()) {
            return String.format("%s:\n\tHL7 Message: %s", super.getMessage(), covertBytesToPrintFriendlyString(hl7Message));
        } else {
            return super.getMessage();
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(this.getClass().getName());

        stringBuilder.append(": {hl7Message=")
                .append(covertBytesToPrintFriendlyString(hl7Message))
                .append("}");

        return stringBuilder.toString();
    }

}
