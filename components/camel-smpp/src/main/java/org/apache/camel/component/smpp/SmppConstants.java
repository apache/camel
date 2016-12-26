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
package org.apache.camel.component.smpp;

/**
 * Constants used in Camel SMPP module
 *
 */
public interface SmppConstants {

    String ALPHABET = "CamelSmppAlphabet";
    String COMMAND = "CamelSmppCommand";
    String COMMAND_ID = "CamelSmppCommandId";
    String COMMAND_STATUS = "CamelSmppCommandStatus";
    String DATA_CODING = "CamelSmppDataCoding";
    String DATA_SPLITTER = "CamelSmppSplitter";
    String DELIVERED = "CamelSmppDelivered";
    String DEST_ADDR = "CamelSmppDestAddr";
    String DEST_ADDR_NPI = "CamelSmppDestAddrNpi";
    String DEST_ADDR_TON = "CamelSmppDestAddrTon";
    String DONE_DATE = "CamelSmppDoneDate";
    String ENCODING = "CamelSmppEncoding";
    String ERROR = "CamelSmppError";
    String ESM_CLASS = "CamelSmppClass";
    String ESME_ADDR = "CamelSmppEsmeAddr";
    String ESME_ADDR_NPI = "CamelSmppEsmeAddrNpi";
    String ESME_ADDR_TON = "CamelSmppEsmeAddrTon";
    String FINAL_DATE = "CamelSmppFinalDate";
    String FINAL_STATUS = "CamelSmppStatus";
    String ID = "CamelSmppId";
    String MESSAGE_STATE = "CamelSmppMessageState";
    String MESSAGE_TYPE = "CamelSmppMessageType";
    String PRIORITY_FLAG = "CamelSmppPriorityFlag";
    String PROTOCOL_ID = "CamelSmppProtocolId";
    String REGISTERED_DELIVERY = "CamelSmppRegisteredDelivery";
    String REPLACE_IF_PRESENT_FLAG = "CamelSmppReplaceIfPresentFlag";
    String SCHEDULE_DELIVERY_TIME = "CamelSmppScheduleDeliveryTime";
    String SENT_MESSAGE_COUNT = "CamelSmppSentMessageCount";
    String SEQUENCE_NUMBER = "CamelSmppSequenceNumber";
    String SERVICE_TYPE = "CamelSmppServiceType";
    String SOURCE_ADDR = "CamelSmppSourceAddr";
    String SOURCE_ADDR_NPI = "CamelSmppSourceAddrNpi";
    String SOURCE_ADDR_TON = "CamelSmppSourceAddrTon";
    String SUBMITTED = "CamelSmppSubmitted";
    String SUBMIT_DATE = "CamelSmppSubmitDate";
    String SYSTEM_ID = "CamelSmppSystemId";
    String PASSWORD = "CamelSmppPassword";
    String VALIDITY_PERIOD = "CamelSmppValidityPeriod";
    String OPTIONAL_PARAMETERS = "CamelSmppOptionalParameters";
    String OPTIONAL_PARAMETER = "CamelSmppOptionalParameter";
    String SPLITTING_POLICY = "CamelSmppSplittingPolicy";

    String UCS2_ENCODING = "UTF-16BE";
    byte UNKNOWN_ALPHABET = -1;
}
