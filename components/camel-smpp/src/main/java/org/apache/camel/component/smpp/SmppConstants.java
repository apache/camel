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
package org.apache.camel.component.smpp;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel SMPP module
 *
 */
public interface SmppConstants {

    @Metadata(label = "producer",
              description = "The alphabet of the short message, which replaces the alphabet option for SubmitSm, SubmitMulti"
                            + " and ReplaceSm: 0 for the SMSC default alphabet, 4 for 8-bit data and 8 for UCS2. When the"
                            + " CamelSmppDataCoding header is not set, SubmitSm, SubmitMulti and DataSm also send it as the"
                            + " data_coding.",
              javaType = "Byte")
    String ALPHABET = "CamelSmppAlphabet";
    @Metadata(description = "The command to send: SubmitSm (the default when the header is missing), ReplaceSm, QuerySm,\n" +
                            "SubmitMulti, CancelSm or DataSm. The names are case-sensitive, and any other value fails.",
              javaType = "String")
    String COMMAND = "CamelSmppCommand";
    @Metadata(label = "consumer",
              description = "The command_id of the received PDU, which identifies its type, as listed in section 5.1.2.1 of"
                            + " the SMPP 3.4 specification. Set for an AlertNotification, a DeliverSm and a DataSm.",
              javaType = "Integer")
    String COMMAND_ID = "CamelSmppCommandId";
    @Metadata(label = "consumer",
              description = "The command_status of the received PDU. Set for an AlertNotification and a DataSm.",
              javaType = "Integer")
    String COMMAND_STATUS = "CamelSmppCommandStatus";
    @Metadata(label = "producer",
              description = "For the producer, the data_coding to send with SubmitSm, SubmitMulti and DataSm, which replaces"
                            + " the dataCoding option; with an 8-bit value, the body is sent as bytes, for ReplaceSm too. The"
                            + " consumer sets it to the data_coding of a received DeliverSm or DataSm.",
              javaType = "Byte")
    String DATA_CODING = "CamelSmppDataCoding";
    @Metadata(label = "producer",
              description = "An org.apache.camel.component.smpp.SmppSplitter that splits the body into segments for SubmitSm"
                            + " and SubmitMulti, instead of the splitter that the producer chooses for the alphabet. A value"
                            + " that is not an SmppSplitter fails the exchange.",
              javaType = "org.apache.camel.component.smpp.SmppSplitter")
    String DATA_SPLITTER = "CamelSmppSplitter";
    @Metadata(label = "consumer",
              description = "The number of short messages delivered, from the dlvrd field of a delivery receipt. It is"
                            + " relevant only when the original message was submitted to a distribution list.",
              javaType = "Integer")
    String DELIVERED = "CamelSmppDelivered";
    @Metadata(description = "For the producer, the destination address for SubmitSm, CancelSm and DataSm, or a list of"
                            + " destination addresses for SubmitMulti, which replaces the destAddr option. The consumer sets it"
                            + " to the destination address of a received DeliverSm or DataSm.",
              javaType = "List or String")
    String DEST_ADDR = "CamelSmppDestAddr";
    @Metadata(description = "For the producer, the numbering plan indicator (NPI) of the destination address for SubmitSm,"
                            + " SubmitMulti, CancelSm and DataSm, which replaces the destAddrNpi option. The consumer sets it"
                            + " to the NPI of the destination address of a received DeliverSm or DataSm.",
              javaType = "Byte")
    String DEST_ADDR_NPI = "CamelSmppDestAddrNpi";
    @Metadata(description = "For the producer, the type of number (TON) of the destination address for SubmitSm,"
                            + " SubmitMulti, CancelSm and DataSm, which replaces the destAddrTon option. The consumer sets it"
                            + " to the TON of the destination address of a received DeliverSm or DataSm.",
              javaType = "Byte")
    String DEST_ADDR_TON = "CamelSmppDestAddrTon";
    @Metadata(label = "consumer",
              description = "The date and time at which the message reached its final state, from the done date field of a"
                            + " delivery receipt.",
              javaType = "Date")
    String DONE_DATE = "CamelSmppDoneDate";
    @Metadata(label = "producer",
              description = "The charset in which the producer encodes the text of the body for SubmitSm, SubmitMulti and"
                            + " ReplaceSm, which replaces the encoding option, also for the UCS2 alphabet. A charset that the"
                            + " JVM does not support is logged and ignored. It is not used for an 8-bit message, whose body is"
                            + " sent as bytes: a byte array as it is, any other body converted in the charset of the exchange.",
              javaType = "String")
    String ENCODING = "CamelSmppEncoding";
    @Metadata(description = "The producer sets it for SubmitMulti, when some destinations failed, to a map from the message"
                            + " id to a list of maps, each with the destination address (CamelSmppDestAddr key) and the error"
                            + " code (CamelSmppError key); for QuerySm, it sets it to the error_code of the response. The"
                            + " consumer sets it to the err field of a delivery receipt, a network or SMSC specific error code,"
                            + " when it is not 000.",
              javaType = "String or Map<String, List<Map<String, Object>>>")
    String ERROR = "CamelSmppError";
    @Metadata(label = "producer",
              description = "The esm_class to send with SubmitSm and SubmitMulti. Without it, the producer sends the default"
                            + " esm_class, or the one with the UDH indicator for a message that it splits.",
              javaType = "org.jsmpp.bean.ESMClass")
    String ESM_CLASS = "CamelSmppClass";
    @Metadata(label = "consumer",
              description = "The esme_addr of a received AlertNotification: the address of the ESME that requested the"
                            + " alert.",
              javaType = "String")
    String ESME_ADDR = "CamelSmppEsmeAddr";
    @Metadata(label = "consumer",
              description = "The numbering plan indicator (NPI) of the esme_addr of a received AlertNotification.",
              javaType = "Byte")
    String ESME_ADDR_NPI = "CamelSmppEsmeAddrNpi";
    @Metadata(label = "consumer",
              description = "The type of number (TON) of the esme_addr of a received AlertNotification.",
              javaType = "Byte")
    String ESME_ADDR_TON = "CamelSmppEsmeAddrTon";
    @Metadata(label = "producer",
              description = "The date and time at which the message reached its final state, from the final_date of the"
                            + " response to QuerySm, or null when the message has not reached a final state.",
              javaType = "java.util.Date")
    String FINAL_DATE = "CamelSmppFinalDate";
    @Metadata(label = "consumer",
              description = "The final state of the message, from the stat field of a delivery receipt, such as DELIVRD,"
                            + " UNDELIV or EXPIRED.",
              javaType = "org.jsmpp.util.DeliveryReceiptState")
    String FINAL_STATUS = "CamelSmppStatus";
    @Metadata(description = "For ReplaceSm, QuerySm and CancelSm, the message id of the message to act on, as the SMSC"
                            + " returned it. After the send, the producer sets it to the list of message ids that the SMSC"
                            + " returned for SubmitSm and SubmitMulti (one per segment), and to a single message id for the"
                            + " other commands. The consumer sets it to the id of the original message for a delivery receipt,"
                            + " and to the message id that it returns to the SMSC for a DataSm.",
              javaType = "String or List<String>")
    String ID = "CamelSmppId";
    @Metadata(label = "producer",
              description = "The state of the message from the response to QuerySm, as the name of an"
                            + " org.jsmpp.bean.MessageState, such as ENROUTE, DELIVERED or EXPIRED.",
              javaType = "String")
    String MESSAGE_STATE = "CamelSmppMessageState";
    @Metadata(label = "consumer", description = "Identifies the type of an incoming message:\n" +
                                                "`AlertNotification`: an SMSC alert notification,\n" +
                                                "`DataSm`: an SMSC data short message,\n" +
                                                "`DeliveryReceipt`: an SMSC delivery receipt,\n" +
                                                "`DeliverSm`: an SMSC deliver short message",
              javaType = "String")
    String MESSAGE_TYPE = "CamelSmppMessageType";
    @Metadata(label = "producer",
              description = "The priority_flag to send with SubmitSm and SubmitMulti, from 0 (lowest) to 3 (highest), which"
                            + " replaces the priorityFlag option.",
              javaType = "Byte")
    String PRIORITY_FLAG = "CamelSmppPriorityFlag";
    @Metadata(label = "producer",
              description = "The protocol_id to send with SubmitSm and SubmitMulti, which replaces the protocolId option.",
              javaType = "Byte")
    String PROTOCOL_ID = "CamelSmppProtocolId";
    @Metadata(description = "For the producer, the registered_delivery to send with SubmitSm, SubmitMulti, ReplaceSm and"
                            + " DataSm, which replaces the registeredDelivery option. The consumer sets it to the"
                            + " registered_delivery of a received DataSm.",
              javaType = "Byte")
    String REGISTERED_DELIVERY = "CamelSmppRegisteredDelivery";
    @Metadata(label = "producer",
              description = "Whether to request a delivery receipt only for the last segment of a message that is split, for"
                            + " SubmitSm and SubmitMulti, which replaces the singleDLR option.",
              javaType = "Boolean")
    String SINGLE_DLR = "CamelSmppSingleDLR";
    @Metadata(label = "producer",
              description = "The replace_if_present_flag to send with SubmitSm and SubmitMulti, which replaces the"
                            + " replaceIfPresentFlag option: 1 asks the SMSC to replace a message that is still pending"
                            + " delivery and has the same source address, destination address and service type, 0 does not.",
              javaType = "Boolean")
    String REPLACE_IF_PRESENT_FLAG = "CamelSmppReplaceIfPresentFlag";
    @Metadata(description = "For the producer, the time at which the SMSC first tries to deliver the message, for SubmitSm,"
                            + " SubmitMulti and ReplaceSm, as a java.util.Date that is sent in the absolute time format of the"
                            + " SMPP 3.4 specification, section 7.1.1. The consumer sets it to the schedule_delivery_time of a"
                            + " received DeliverSm, as the String in that format.",
              javaType = "Date")
    String SCHEDULE_DELIVERY_TIME = "CamelSmppScheduleDeliveryTime";
    @Metadata(label = "producer",
              description = "The number of short messages that the producer sent for SubmitSm and SubmitMulti, which is more"
                            + " than one when the body was split.",
              javaType = "Integer")
    String SENT_MESSAGE_COUNT = "CamelSmppSentMessageCount";
    @Metadata(label = "consumer",
              description = "The sequence_number of the received PDU, which correlates the response with the request. Set"
                            + " for an AlertNotification, a DeliverSm and a DataSm.",
              javaType = "int")
    String SEQUENCE_NUMBER = "CamelSmppSequenceNumber";
    @Metadata(description = "For the producer, the service_type to send with SubmitSm, SubmitMulti, CancelSm and DataSm,"
                            + " which replaces the serviceType option. The consumer sets it to the service_type of a received"
                            + " DeliverSm or DataSm.",
              javaType = "String")
    String SERVICE_TYPE = "CamelSmppServiceType";
    @Metadata(description = "For the producer, the source address to send with every command, which replaces the sourceAddr"
                            + " option; for ReplaceSm, QuerySm and CancelSm, the source address of the original message. The"
                            + " consumer sets it to the source address of a received AlertNotification, DeliverSm or DataSm.",
              javaType = "String")
    String SOURCE_ADDR = "CamelSmppSourceAddr";
    @Metadata(description = "For the producer, the numbering plan indicator (NPI) of the source address to send with every"
                            + " command, which replaces the sourceAddrNpi option. The consumer sets it to the NPI of the source"
                            + " address of a received AlertNotification, DeliverSm or DataSm.",
              javaType = "Byte")
    String SOURCE_ADDR_NPI = "CamelSmppSourceAddrNpi";
    @Metadata(description = "For the producer, the type of number (TON) of the source address to send with every command,"
                            + " which replaces the sourceAddrTon option. The consumer sets it to the TON of the source address"
                            + " of a received AlertNotification, DeliverSm or DataSm.",
              javaType = "Byte")
    String SOURCE_ADDR_TON = "CamelSmppSourceAddrTon";
    @Metadata(label = "consumer",
              description = "The number of short messages originally submitted, from the sub field of a delivery receipt. It"
                            + " is relevant only when the original message was submitted to a distribution list.",
              javaType = "Integer")
    String SUBMITTED = "CamelSmppSubmitted";
    @Metadata(label = "consumer",
              description = "The date and time at which the original message was submitted, or replaced, from the submit"
                            + " date field of a delivery receipt.",
              javaType = "Date")
    String SUBMIT_DATE = "CamelSmppSubmitDate";
    @Metadata(label = "producer",
              description = "The system id to connect to the SMSC with. Used only by the first exchange of a producer with"
                            + " `lazySessionCreation=true` and `allowSystemIdAndPasswordFromHeaders=true`, together with"
                            + " the `CamelSmppPassword` header; otherwise ignored.",
              javaType = "String")
    String SYSTEM_ID = "CamelSmppSystemId";
    @Metadata(label = "producer",
              description = "The password to connect to the SMSC with. Used only by the first exchange of a producer with"
                            + " `lazySessionCreation=true` and `allowSystemIdAndPasswordFromHeaders=true`, together with"
                            + " the `CamelSmppSystemId` header; otherwise ignored.",
              javaType = "String", secret = true)
    String PASSWORD = "CamelSmppPassword";
    @Metadata(description = "For the producer, the time after which the SMSC discards the message if it was not delivered,"
                            + " for SubmitSm, SubmitMulti and ReplaceSm: a java.util.Date is sent in the absolute time format,"
                            + " and a String as it is, so it can hold a relative time (SMPP 3.4 specification, section 7.1.1)."
                            + " The consumer sets it to the validity_period of a received DeliverSm, as a String.",
              javaType = "String or Date")
    String VALIDITY_PERIOD = "CamelSmppValidityPeriod";
    @Metadata(label = "consumer",
              description = "The optional parameters (TLVs) by the name of their org.jsmpp.bean.OptionalParameter.Tag. The"
                            + " producer sends them, with String values, with SubmitSm, SubmitMulti and DataSm when the"
                            + " CamelSmppOptionalParameter header is not set. The consumer sets it for a received DeliverSm,"
                            + " and the producer for the response to DataSm.",
              javaType = "Map<String, Object>", deprecationNote = "Use CamelSmppOptionalParameter instead")
    String OPTIONAL_PARAMETERS = "CamelSmppOptionalParameters";
    @Metadata(description = "The optional parameters (TLVs) by their tag code. The producer sends them with SubmitSm,"
                            + " SubmitMulti and DataSm: a String as a COctetString, a byte array as an OctetString, a Byte, a"
                            + " Short or an Integer as a parameter of that size, and null as a parameter without value; it"
                            + " skips other values. The consumer sets it for a received DeliverSm, and the producer for the"
                            + " response to DataSm, with the values converted back the same way.",
              javaType = "Map<Short, Object>")
    String OPTIONAL_PARAMETER = "CamelSmppOptionalParameter";
    @Metadata(label = "producer",
              description = "The splitting policy for SubmitSm and SubmitMulti, ALLOW, REJECT or TRUNCATE, which replaces"
                            + " the splittingPolicy option. Any other value fails the exchange.",
              javaType = "String")
    String SPLITTING_POLICY = "CamelSmppSplittingPolicy";

    byte UNKNOWN_ALPHABET = -1;
}
