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
public class MllpAcknowledgementTimoutException extends MllpTimeoutException {
    public MllpAcknowledgementTimoutException(String message) {
        super(message);
    }

    public MllpAcknowledgementTimoutException(String message, byte[] mllpPayload) {
        super(message, mllpPayload);
    }

    public MllpAcknowledgementTimoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public MllpAcknowledgementTimoutException(String message, byte[] mllpPayload, Throwable cause) {
        super(message, mllpPayload, cause);
    }
}
