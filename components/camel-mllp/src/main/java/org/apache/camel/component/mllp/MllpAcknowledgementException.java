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
package org.apache.camel.component.mllp;

/**
 * Base Exception for with HL7 Application Acknowledgements
 */
public abstract class MllpAcknowledgementException extends MllpException {

    protected MllpAcknowledgementException(String message, boolean logPhi) {
        super(message, logPhi);
    }

    protected MllpAcknowledgementException(String message, Throwable cause, boolean logPhi) {
        super(message, cause, logPhi);
    }

    protected MllpAcknowledgementException(String message, byte[] hl7Message, boolean logPhi) {
        super(message, hl7Message, logPhi);
    }

    protected MllpAcknowledgementException(String message, byte[] hl7Message, byte[] hl7Acknowledgement, boolean logPhi) {
        super(message, hl7Message, hl7Acknowledgement, logPhi);
    }

    protected MllpAcknowledgementException(String message, byte[] hl7Message, Throwable cause, boolean logPhi) {
        super(message, hl7Message, cause, logPhi);
    }

    protected MllpAcknowledgementException(String message, byte[] hl7Message, byte[] hl7Acknowledgement, Throwable cause,
                                           boolean logPhi) {
        super(message, hl7Message, hl7Acknowledgement, cause, logPhi);
    }

}
