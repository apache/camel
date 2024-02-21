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

import org.apache.camel.spi.Metadata;

/**
 * Constants for the MLLP Protocol and the Camel MLLP component.
 */
public final class MllpConstants {
    @Metadata(description = "The local TCP Address of the Socket", javaType = "String")
    public static final String MLLP_LOCAL_ADDRESS = "CamelMllpLocalAddress";
    @Metadata(description = "The remote TCP Address of the Socket", javaType = "String")
    public static final String MLLP_REMOTE_ADDRESS = "CamelMllpRemoteAddress";
    @Metadata(description = "The HL7 Acknowledgment received in bytes", javaType = "byte[]")
    public static final String MLLP_ACKNOWLEDGEMENT = "CamelMllpAcknowledgement";
    @Metadata(description = "The HL7 Acknowledgment received, converted to a String", javaType = "String")
    public static final String MLLP_ACKNOWLEDGEMENT_STRING = "CamelMllpAcknowledgementString";
    @Metadata(description = "The HL7 acknowledgement type (AA, AE, AR, etc)", javaType = "String")
    public static final String MLLP_ACKNOWLEDGEMENT_TYPE = "CamelMllpAcknowledgementType";
    public static final String MLLP_ACKNOWLEDGEMENT_MSA_TEXT = "CamelMllpAcknowledgementMsaText";

    public static final String MLLP_ACKNOWLEDGEMENT_EXCEPTION = "CamelMllpAcknowledgementException";
    public static final String MLLP_AUTO_ACKNOWLEDGE = "CamelMllpAutoAcknowledge";
    /*
     Connection Control Exchange Properties
      - For Consumers, "SEND" => ACKNOWLEDGEMENT
      - For Producers, "SEND" => MESSAGE
      */
    public static final String MLLP_CLOSE_CONNECTION_BEFORE_SEND = "CamelMllpCloseConnectionBeforeSend";
    public static final String MLLP_RESET_CONNECTION_BEFORE_SEND = "CamelMllpResetConnectionBeforeSend";
    public static final String MLLP_CLOSE_CONNECTION_AFTER_SEND = "CamelMllpCloseConnectionAfterSend";
    public static final String MLLP_RESET_CONNECTION_AFTER_SEND = "CamelMllpResetConnectionAfterSend";

    /** MSH-3 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_SENDING_APPLICATION = "CamelMllpSendingApplication";
    /** MSH-4 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_SENDING_FACILITY = "CamelMllpSendingFacility";
    /** MSH-5 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_RECEIVING_APPLICATION = "CamelMllpReceivingApplication";
    /** MSH-6 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_RECEIVING_FACILITY = "CamelMllpReceivingFacility";
    /** MSH-7 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_TIMESTAMP = "CamelMllpTimestamp";
    /** MSH-8 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_SECURITY = "CamelMllpSecurity";
    /** MSH-9 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_MESSAGE_TYPE = "CamelMllpMessageType";
    /** MSH-9.1 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_EVENT_TYPE = "CamelMllpEventType";
    /** MSH-9.2 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_TRIGGER_EVENT = "CamelMllpTriggerEvent";
    /** MSH-10 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_MESSAGE_CONTROL = "CamelMllpMessageControlId";
    /** MSH-11 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_PROCESSING_ID = "CamelMllpProcessingId";
    /** MSH-12 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_VERSION_ID = "CamelMllpVersionId";
    /** MSH-18 value */
    @Metadata(label = "consumer", javaType = "String")
    public static final String MLLP_CHARSET = "CamelMllpCharset";

    private MllpConstants() {
        //utility class, never constructed
    }
}
