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
package org.apache.camel.component.salesforce;

import org.apache.camel.spi.Metadata;

public final class SalesforceConstants {

    // Streaming headers
    @Metadata(label = "consumer", description = "The Streaming API replayId.", javaType = "Object")
    public static final String HEADER_SALESFORCE_REPLAY_ID = "CamelSalesforceReplayId";
    @Metadata(label = "consumer", description = "The Streaming API eventUuid.", javaType = "Object")
    public static final String HEADER_SALESFORCE_EVENT_UUID = "CamelSalesforceEventUuid";
    @Metadata(label = "consumer", description = "The change event schema.", javaType = "Object")
    public static final String HEADER_SALESFORCE_CHANGE_EVENT_SCHEMA = "CamelSalesforceChangeEventSchema";
    @Metadata(label = "consumer",
              description = "For change and platform events, the last segment of the configured topic name, such as"
                            + " AccountChangeEvent. For PushTopic messages, the Salesforce event type of the"
                            + " notification, such as created.",
              javaType = "String")
    public static final String HEADER_SALESFORCE_EVENT_TYPE = "CamelSalesforceEventType";
    @Metadata(label = "consumer",
              description = "Time of the Salesforce transaction that produced the change event, from the"
                            + " ChangeEventHeader.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_COMMIT_TIMESTAMP = "CamelSalesforceCommitTimestamp";
    @Metadata(label = "consumer",
              description = "Id of the Salesforce user whose transaction produced the change event, from the"
                            + " ChangeEventHeader.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_COMMIT_USER = "CamelSalesforceCommitUser";
    @Metadata(label = "consumer",
              description = "System change number of the Salesforce transaction that produced the change event, from"
                            + " the ChangeEventHeader.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_COMMIT_NUMBER = "CamelSalesforceCommitNumber";
    @Metadata(label = "consumer",
              description = "Ids of the records affected by the change event, from the ChangeEventHeader. Holds more"
                            + " than one id when a single transaction changed several records.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_RECORD_IDS = "CamelSalesforceRecordIds";
    @Metadata(label = "consumer",
              description = "Kind of change carried by the change event, from the ChangeEventHeader, for example"
                            + " CREATE or UPDATE.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_CHANGE_TYPE = "CamelSalesforceChangeType";
    @Metadata(label = "consumer",
              description = "Identifies the Salesforce API and client that made the change, from the"
                            + " ChangeEventHeader.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_CHANGE_ORIGIN = "CamelSalesforceChangeOrigin";
    @Metadata(label = "consumer",
              description = "Key shared by every change event produced by the same Salesforce transaction, from the"
                            + " ChangeEventHeader. Use it to group events that belong to one transaction.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_TRANSACTION_KEY = "CamelSalesforceTransactionKey";
    @Metadata(label = "consumer",
              description = "Position of this change event within its Salesforce transaction, from the"
                            + " ChangeEventHeader.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_SEQUENCE_NUMBER = "CamelSalesforceSequenceNumber";
    @Metadata(label = "consumer",
              description = "True when this change event is the last one of its Salesforce transaction, from the"
                            + " ChangeEventHeader. Use it together with CamelSalesforceTransactionKey to detect the"
                            + " end of a transaction.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_IS_TRANSACTION_END = "CamelSalesforceIsTransactionEnd";
    @Metadata(label = "consumer",
              description = "Name of the Salesforce object the change event applies to, such as Account, from the"
                            + " ChangeEventHeader.",
              javaType = "Object")
    public static final String HEADER_SALESFORCE_ENTITY_NAME = "CamelSalesforceEntityName";
    @Metadata(label = "consumer", description = "The platform event schema.", javaType = "Object")
    public static final String HEADER_SALESFORCE_PLATFORM_EVENT_SCHEMA = "CamelSalesforcePlatformEventSchema";
    @Metadata(label = "consumer",
              description = "Creation time of the event. Platform events supply a ZonedDateTime taken from the event"
                            + " payload, while PushTopic notifications supply the raw createdDate value as a String.",
              javaType = "java.time.ZonedDateTime")
    public static final String HEADER_SALESFORCE_CREATED_DATE = "CamelSalesforceCreatedDate";
    @Metadata(label = "consumer",
              description = "Name of the PushTopic the message was received from. Set for PushTopic messages only.",
              javaType = "String")
    public static final String HEADER_SALESFORCE_TOPIC_NAME = "CamelSalesforceTopicName";
    @Metadata(label = "consumer",
              description = "Streaming API channel the message was received on, such as /topic/MyTopic or"
                            + " /data/AccountChangeEvent.",
              javaType = "String")
    public static final String HEADER_SALESFORCE_CHANNEL = "CamelSalesforceChannel";
    @Metadata(label = "consumer",
              description = "Client id of the Streaming API subscription that received the message. Set only when the"
                            + " server supplies one.",
              javaType = "String")
    public static final String HEADER_SALESFORCE_CLIENT_ID = "CamelSalesforceClientId";

    // Pub/Sub API headers
    @Metadata(label = "consumer", description = "The Pub/Sub API replayId.", javaType = "String")
    public static final String HEADER_SALESFORCE_PUBSUB_REPLAY_ID = "CamelSalesforcePubSubReplayId";
    @Metadata(label = "consumer", description = "The Pub/Sub API event id.", javaType = "String")
    public static final String HEADER_SALESFORCE_PUBSUB_EVENT_ID = "CamelSalesforcePubSubEventId";
    @Metadata(label = "consumer", description = "The Pub/Sub API rpc id.", javaType = "String")
    public static final String HEADER_SALESFORCE_PUBSUB_RPC_ID = "CamelSalesforcePubSubRpcId";

    @Metadata(label = "producer", description = "Total number of records matching a query.", javaType = "int")
    public static final String HEADER_SALESFORCE_QUERY_RESULT_TOTAL_SIZE = "CamelSalesforceQueryResultTotalSize";

    private SalesforceConstants() {

    }
}
