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
 * Raised when a MLLP Producer receives a HL7 Commit Error Acknowledgement
 */
public class MllpCommitErrorAcknowledgementException extends MllpNegativeAcknowledgementException {

    static final String EXCEPTION_MESSAGE = "HL7 Commit Error Acknowledgment Received";

    public MllpCommitErrorAcknowledgementException(byte[] hl7Message, byte[] hl7Acknowledgement) {
        super(EXCEPTION_MESSAGE, hl7Message, hl7Acknowledgement);
    }

    public MllpCommitErrorAcknowledgementException(byte[] hl7Message, byte[] hl7Acknowledgement, Throwable cause) {
        super(EXCEPTION_MESSAGE, hl7Message, hl7Acknowledgement, cause);
    }

    @Override
    public String getAcknowledgmentType() {
        return "CE";
    }
}
