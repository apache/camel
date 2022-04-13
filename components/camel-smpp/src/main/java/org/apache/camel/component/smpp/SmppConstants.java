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

    @Metadata(label = "producer", description = "*For SubmitSm, SubmitMulti and ReplaceSm*  The data\n" +
                                                "coding according to the SMPP 3.4 specification, section 5.2.19. Use the\n" +
                                                "URI option `alphabet` settings above.",
              javaType = "Byte")
    String ALPHABET = "CamelSmppAlphabet";
    @Metadata(description = "The command", javaType = "String")
    String COMMAND = "CamelSmppCommand";
    @Metadata(label = "consumer", description = "*only for AlertNotification, DeliverSm and DataSm* The command id field\n" +
                                                "identifies the particular SMPP PDU. For the complete list of defined\n" +
                                                "values see chapter 5.1.2.1 in the smpp specification v3.4.",
              javaType = "Integer")
    String COMMAND_ID = "CamelSmppCommandId";
    @Metadata(label = "consumer", description = "*only for DataSm* The Command status of the message.", javaType = "Integer")
    String COMMAND_STATUS = "CamelSmppCommandStatus";
    @Metadata(label = "producer", description = "*For SubmitSm, SubmitMulti and ReplaceSm*  The data\n" +
                                                "coding according to the SMPP 3.4 specification, section 5.2.19. Use the\n" +
                                                "URI option `alphabet` settings above.",
              javaType = "Byte")
    String DATA_CODING = "CamelSmppDataCoding";
    @Metadata(label = "producer", description = "The splitter", javaType = "org.apache.camel.component.smpp.SmppSplitter")
    String DATA_SPLITTER = "CamelSmppSplitter";
    @Metadata(label = "consumer", description = "*only for smsc DeliveryReceipt* Number of short messages delivered. This\n" +
                                                "is only relevant where the original message was submitted to a\n" +
                                                "distribution list.The value is padded with leading zeros if necessary.",
              javaType = "Integer")
    String DELIVERED = "CamelSmppDelivered";
    @Metadata(description = "*Producer:* *only for SubmitSm, SubmitMulti, CancelSm and DataSm* Defines the\n" +
                            "destination SME address(es). For mobile terminated messages, this is the\n" +
                            "directory number of the recipient MS. It must be a `List<String>` for\n" +
                            "SubmitMulti and a `String` otherwise.\n" +
                            "*Consumer:* *only for DeliverSm and DataSm:* Defines the destination SME address.\n" +
                            "For mobile terminated messages, this is the directory number of the\n" +
                            "recipient MS.",
              javaType = "List or String")
    String DEST_ADDR = "CamelSmppDestAddr";
    @Metadata(description = "*Producer:* *only for SubmitSm, SubmitMulti, CancelSm and DataSm* Defines the\n" +
                            "numeric plan indicator (NPI) to be used in the SME destination address\n" +
                            "parameters. Use the URI option `sourceAddrNpi` values defined above.\n" +
                            "*Consumer:* *only for DataSm* Defines the numeric plan indicator (NPI) in the\n" +
                            "destination address parameters. Use the URI option `sourceAddrNpi`\n" +
                            "values defined above.",
              javaType = "Byte")
    String DEST_ADDR_NPI = "CamelSmppDestAddrNpi";
    @Metadata(description = "*Producer:* *only for SubmitSm, SubmitMulti, CancelSm and DataSm* Defines the type\n" +
                            "of number (TON) to be used in the SME destination address parameters.\n" +
                            "Use the `sourceAddrTon` URI option values defined above.\n" +
                            "*Consumer:* *only for DataSm* Defines the type of number (TON) in the destination\n" +
                            "address parameters. Use the `sourceAddrTon` URI option values defined\n" +
                            "above.",
              javaType = "Byte")
    String DEST_ADDR_TON = "CamelSmppDestAddrTon";
    @Metadata(label = "consumer", description = "*only for smsc DeliveryReceipt* The time and date at which the short\n" +
                                                "message reached it's final state. The format is as follows: YYMMDDhhmm.",
              javaType = "Date")
    String DONE_DATE = "CamelSmppDoneDate";
    @Metadata(label = "producer", description = "*only for SubmitSm,\n" +
                                                "SubmitMulti and DataSm*.  Specifies the encoding (character set name) of\n" +
                                                "the bytes in the message body.  If the message body is a string then\n" +
                                                "this is not relevant because Java Strings are always Unicode.  If the\n" +
                                                "body is a byte array then this header can be used to indicate that it is\n" +
                                                "ISO-8859-1 or some other value.  Default value is specified by the\n" +
                                                "endpoint configuration parameter _encoding_",
              javaType = "String")
    String ENCODING = "CamelSmppEncoding";
    @Metadata(description = "*Producer:* *only for SubmitMultiSm* The errors which\n" +
                            "occurred by sending the short message(s) the form `Map<String, List<Map<String, Object>>>` (messageID : (destAddr :\n"
                            +
                            "address, error : errorCode)).\n" +
                            "*Consumer:* *only for smsc DeliveryReceipt* Where appropriate this may hold a\n" +
                            "Network specific error code or an SMSC error code for the attempted\n" +
                            "delivery of the message. These errors are Network or SMSC specific and\n" +
                            "are not included here.",
              javaType = "String or Map<String, List<Map<String, Object>>>")
    String ERROR = "CamelSmppError";
    @Metadata(label = "producer", description = "the ASM class", javaType = "org.jsmpp.bean.ESMClass")
    String ESM_CLASS = "CamelSmppClass";
    @Metadata(label = "consumer", description = "*only for AlertNotification* Defines the destination ESME address. For\n" +
                                                "mobile terminated messages, this is the directory number of the\n" +
                                                "recipient MS.",
              javaType = "String")
    String ESME_ADDR = "CamelSmppEsmeAddr";
    @Metadata(label = "consumer", description = "*only for AlertNotification* Defines the numeric plan indicator (NPI) to\n" +
                                                "be used in the ESME originator address parameters. Use the URI option\n" +
                                                "`sourceAddrNpi` values defined above.",
              javaType = "Byte")
    String ESME_ADDR_NPI = "CamelSmppEsmeAddrNpi";
    @Metadata(label = "consumer", description = "*only for AlertNotification* Defines the type of number (TON) to be used\n" +
                                                "in the ESME originator address parameters. Use the `sourceAddrTon` URI\n" +
                                                "option values defined above.",
              javaType = "Byte")
    String ESME_ADDR_TON = "CamelSmppEsmeAddrTon";
    @Metadata(label = "producer", description = "The final date", javaType = "java.util.Date")
    String FINAL_DATE = "CamelSmppFinalDate";
    @Metadata(label = "consumer", description = "*only for smsc DeliveryReceipt:* The final status of the message.",
              javaType = "org.jsmpp.util.DeliveryReceiptState")
    String FINAL_STATUS = "CamelSmppStatus";
    @Metadata(description = "*Producer:* The id to identify the submitted short message(s) for later use.\n" +
                            "In case of a ReplaceSm, QuerySm, CancelSm and DataSm this\n" +
                            "header value is a `String`. In case of a SubmitSm or SubmitMultiSm this\n" +
                            "header value is a `List<String>`.\n" +
                            "*Consumer:* *only for smsc DeliveryReceipt and DataSm* The message ID allocated to\n" +
                            "the message by the SMSC when originally submitted.",
              javaType = "String or List<String>")
    String ID = "CamelSmppId";
    @Metadata(label = "producer", description = "The message date", javaType = "String")
    String MESSAGE_STATE = "CamelSmppMessageState";
    @Metadata(label = "consumer", description = "Identifies the type of an incoming message:\n" +
                                                "`AlertNotification`: an SMSC alert notification,\n" +
                                                "`DataSm`: an SMSC data short message,\n" +
                                                "`DeliveryReceipt`: an SMSC delivery receipt,\n" +
                                                "`DeliverSm`: an SMSC deliver short message",
              javaType = "String")
    String MESSAGE_TYPE = "CamelSmppMessageType";
    @Metadata(label = "producer", description = "*only for SubmitSm and SubmitMulti* Allows the originating SME to assign\n" +
                                                "a priority level to the short message. Use the URI option `priorityFlag`\n" +
                                                "settings above.",
              javaType = "Byte")
    String PRIORITY_FLAG = "CamelSmppPriorityFlag";
    @Metadata(label = "producer", description = "The protocol id", javaType = "Byte")
    String PROTOCOL_ID = "CamelSmppProtocolId";
    @Metadata(description = "*Producer:* *only for SubmitSm, ReplaceSm, SubmitMulti and DataSm* Is used to\n" +
                            "request an SMSC delivery receipt and/or SME originated acknowledgements.\n" +
                            "Use the URI option `registeredDelivery` settings above.\n" +
                            "*Consumer:* *only for DataSm* Is used to request an delivery receipt and/or SME\n" +
                            "originated acknowledgements. Same values as in Producer header list\n" +
                            "above.",
              javaType = "Byte")
    String REGISTERED_DELIVERY = "CamelSmppRegisteredDelivery";
    @Metadata(label = "producer", description = "*only for SubmitSm, SubmitMulti* Is used to\n" +
                                                "request the SMSC delivery receipt only on the last segment of multi-segment (long) messages.\n"
                                                +
                                                "Use the URI option `singleDLR` settings above.",
              javaType = "Boolean")
    String SINGLE_DLR = "CamelSmppSingleDLR";
    @Metadata(label = "producer", description = "*only for SubmitSm and SubmitMulti* The replace if present flag\n" +
                                                "parameter is used to request the SMSC to replace a previously submitted\n" +
                                                "message, that is still pending delivery. The SMSC will replace an\n" +
                                                "existing message provided that the source address, destination address\n" +
                                                "and service type match the same fields in the new message. The following\n" +
                                                "values are defined: `0`, Don't replace and `1`, Replace",
              javaType = "Boolean")
    String REPLACE_IF_PRESENT_FLAG = "CamelSmppReplaceIfPresentFlag";
    @Metadata(description = "*Producer:* only for SubmitSm, SubmitMulti and ReplaceSm* This parameter specifies\n" +
                            "the scheduled time at which the message delivery should be first\n" +
                            "attempted. It defines either the absolute date and time or relative time\n" +
                            "from the current SMSC time at which delivery of this message will be\n" +
                            "attempted by the SMSC. It can be specified in either absolute time\n" +
                            "format or relative time format. The encoding of a time format is\n" +
                            "specified in chapter 7.1.1. in the smpp specification v3.4.\n" +
                            "*Consumer:* *only for DeliverSm:* This parameter specifies the scheduled time at\n" +
                            "which the message delivery should be first attempted. It defines either\n" +
                            "the absolute date and time or relative time from the current SMSC time\n" +
                            "at which delivery of this message will be attempted by the SMSC. It can\n" +
                            "be specified in either absolute time format or relative time format. The\n" +
                            "encoding of a time format is specified in Section 7.1.1. in the smpp\n" +
                            "specification v3.4.",
              javaType = "Date")
    String SCHEDULE_DELIVERY_TIME = "CamelSmppScheduleDeliveryTime";
    @Metadata(label = "producer", description = "*only for SubmitSm and SubmitMultiSm* The total\n" +
                                                "number of messages which has been sent.",
              javaType = "Integer")
    String SENT_MESSAGE_COUNT = "CamelSmppSentMessageCount";
    @Metadata(label = "consumer", description = "*only for AlertNotification, DeliverSm and DataSm* A sequence number\n" +
                                                "allows a response PDU to be correlated with a request PDU. The\n" +
                                                "associated SMPP response PDU must preserve this field.",
              javaType = "int")
    String SEQUENCE_NUMBER = "CamelSmppSequenceNumber";
    @Metadata(description = "*Producer:* The service type parameter can be used to indicate the SMS Application\n" +
                            "service associated with the message. Use the URI option `serviceType`\n" +
                            "settings above.\n" +
                            "*Consumer:* *only for DeliverSm and DataSm* The service type parameter indicates the\n" +
                            "SMS Application service associated with the message.",
              javaType = "String")
    String SERVICE_TYPE = "CamelSmppServiceType";
    @Metadata(description = "*Producer:* Defines the address of SME (Short Message Entity) which originated this message.\n" +
                            "*Consumer:* *Only for AlertNotification, DeliverSm and DataSm* Defines the address\n" +
                            "of SME (Short Message Entity) which originated this message.",
              javaType = "String")
    String SOURCE_ADDR = "CamelSmppSourceAddr";
    @Metadata(description = "*Producer:* Defines the numeric plan indicator (NPI) to be used in the SME\n" +
                            "originator address parameters. Use the URI option `sourceAddrNpi` values\n" +
                            "defined above.\n" +
                            "*Consumer:* *only for AlertNotification and DataSm* Defines the numeric plan\n" +
                            "indicator (NPI) to be used in the SME originator address parameters. Use\n" +
                            "the URI option `sourceAddrNpi` values defined above.",
              javaType = "Byte")
    String SOURCE_ADDR_NPI = "CamelSmppSourceAddrNpi";
    @Metadata(description = "*Producer:* Defines the type of number (TON) to be used in the SME originator\n" +
                            "address parameters. Use the `sourceAddrTon` URI option values defined\n" +
                            "above.\n" +
                            "*Consumer:* *only for AlertNotification and DataSm* Defines the type of number (TON)\n" +
                            "to be used in the SME originator address parameters. Use the\n" +
                            "`sourceAddrTon` URI option values defined above.",
              javaType = "Byte")
    String SOURCE_ADDR_TON = "CamelSmppSourceAddrTon";
    @Metadata(label = "consumer", description = "*only for smsc DeliveryReceipt* Number of short messages originally\n" +
                                                "submitted. This is only relevant when the original message was submitted\n" +
                                                "to a distribution list.The value is padded with leading zeros if\n" +
                                                "necessary.",
              javaType = "Integer")
    String SUBMITTED = "CamelSmppSubmitted";
    @Metadata(label = "consumer", description = "*only for smsc DeliveryReceipt* The time and date at which the short\n" +
                                                "message was submitted. In the case of a message which has been replaced,\n" +
                                                "this is the date that the original message was replaced. The format is\n" +
                                                "as follows: YYMMDDhhmm.",
              javaType = "Date")
    String SUBMIT_DATE = "CamelSmppSubmitDate";
    @Metadata(label = "producer", description = "The system id.", javaType = "String")
    String SYSTEM_ID = "CamelSmppSystemId";
    @Metadata(label = "producer", description = "The password.", javaType = "String")
    String PASSWORD = "CamelSmppPassword";
    @Metadata(description = "*Producer:* *only for SubmitSm, SubmitMulti and ReplaceSm* The validity period\n" +
                            "parameter indicates the SMSC expiration time, after which the message\n" +
                            "should be discarded if not delivered to the destination. If it's\n" +
                            "provided as `Date`, it's interpreted as absolute time or relative time\n" +
                            "format if you provide it as `String` as specified in chapter 7.1.1 in\n" +
                            "the smpp specification v3.4.\n" +
                            "*Consumer:* *only for DeliverSm* The validity period parameter indicates the SMSC\n" +
                            "expiration time, after which the message should be discarded if not\n" +
                            "delivered to the destination. It can be defined in absolute time format\n" +
                            "or relative time format. The encoding of absolute and relative time\n" +
                            "format is specified in Section 7.1.1 in the smpp specification v3.4.",
              javaType = "String or Date")
    String VALIDITY_PERIOD = "CamelSmppValidityPeriod";
    @Metadata(label = "consumer", description = "The optional parameters by name.", javaType = "Map<String, Object>",
              deprecationNote = "Use CamelSmppOptionalParameter instead")
    String OPTIONAL_PARAMETERS = "CamelSmppOptionalParameters";
    @Metadata(description = "*Producer:* *only for SubmitSm, SubmitMulti and\n" +
                            "DataSm* The optional parameter which are send to the SMSC. The value is\n" +
                            "converted in the following way: `String` -> `org.jsmpp.bean.OptionalParameter.COctetString`, \n" +
                            "`byte[]` -> `org.jsmpp.bean.OptionalParameter.OctetString`, \n" +
                            "`Byte` -> `org.jsmpp.bean.OptionalParameter.Byte`,\n" +
                            "`Integer` -> `org.jsmpp.bean.OptionalParameter.Int`,\n" +
                            "`Short` -> `org.jsmpp.bean.OptionalParameter.Short`, \n" +
                            "`null` -> `org.jsmpp.bean.OptionalParameter.Null`\n" +
                            "*Consumer:* *only for DeliverSm* The optional\n" +
                            "parameters send back by the SMSC. The key is the `Short` code for the\n" +
                            "optional parameter. The value is converted in the following way: \n" +
                            "`org.jsmpp.bean.OptionalParameter.COctetString` -> `String`,\n" +
                            "`org.jsmpp.bean.OptionalParameter.OctetString` -> `byte[]`,\n" +
                            "`org.jsmpp.bean.OptionalParameter.Byte` -> `Byte`,\n" +
                            "`org.jsmpp.bean.OptionalParameter.Int` -> `Integer`,\n" +
                            "`org.jsmpp.bean.OptionalParameter.Short` -> `Short`,\n" +
                            "`org.jsmpp.bean.OptionalParameter.Null` -> `null`",
              javaType = "Map<Short, Object>")
    String OPTIONAL_PARAMETER = "CamelSmppOptionalParameter";
    @Metadata(label = "producer", description = "*only for SubmitSm,\n" +
                                                "SubmitMulti and DataSm*.  Specifies the policy for message splitting for\n" +
                                                "this exchange.  Possible values are described in the endpoint\n" +
                                                "configuration parameter _splittingPolicy_",
              javaType = "String")
    String SPLITTING_POLICY = "CamelSmppSplittingPolicy";

    byte UNKNOWN_ALPHABET = -1;
}
