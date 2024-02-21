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

    @Metadata(label = "consumer", description = "The Streaming API replayId.", javaType = "Object")
    public static final String HEADER_SALESFORCE_REPLAY_ID = "CamelSalesforceReplayId";
    @Metadata(label = "consumer", description = "The Pub/Sub API replayId.", javaType = "Object")
    public static final String HEADER_SALESFORCE_PUBSUB_REPLAY_ID = "CamelSalesforcePubSubReplayId";
    @Metadata(label = "consumer", description = "The change event schema.", javaType = "Object")
    public static final String HEADER_SALESFORCE_CHANGE_EVENT_SCHEMA = "CamelSalesforceChangeEventSchema";
    @Metadata(label = "consumer", description = "The event type.", javaType = "String")
    public static final String HEADER_SALESFORCE_EVENT_TYPE = "CamelSalesforceEventType";
    @Metadata(label = "consumer", description = "The commit timestamp.", javaType = "Object")
    public static final String HEADER_SALESFORCE_COMMIT_TIMESTAMP = "CamelSalesforceCommitTimestamp";
    @Metadata(label = "consumer", description = "The commit user.", javaType = "Object")
    public static final String HEADER_SALESFORCE_COMMIT_USER = "CamelSalesforceCommitUser";
    @Metadata(label = "consumer", description = "The commit number.", javaType = "Object")
    public static final String HEADER_SALESFORCE_COMMIT_NUMBER = "CamelSalesforceCommitNumber";
    @Metadata(label = "consumer", description = "The record ids.", javaType = "Object")
    public static final String HEADER_SALESFORCE_RECORD_IDS = "CamelSalesforceRecordIds";
    @Metadata(label = "consumer", description = "The change type.", javaType = "Object")
    public static final String HEADER_SALESFORCE_CHANGE_TYPE = "CamelSalesforceChangeType";
    @Metadata(label = "consumer", description = "The change origin.", javaType = "Object")
    public static final String HEADER_SALESFORCE_CHANGE_ORIGIN = "CamelSalesforceChangeOrigin";
    @Metadata(label = "consumer", description = "The transaction key.", javaType = "Object")
    public static final String HEADER_SALESFORCE_TRANSACTION_KEY = "CamelSalesforceTransactionKey";
    @Metadata(label = "consumer", description = "The sequence number.", javaType = "Object")
    public static final String HEADER_SALESFORCE_SEQUENCE_NUMBER = "CamelSalesforceSequenceNumber";
    @Metadata(label = "consumer", description = "Is transaction end.", javaType = "Object")
    public static final String HEADER_SALESFORCE_IS_TRANSACTION_END = "CamelSalesforceIsTransactionEnd";
    @Metadata(label = "consumer", description = "The entity name.", javaType = "Object")
    public static final String HEADER_SALESFORCE_ENTITY_NAME = "CamelSalesforceEntityName";
    @Metadata(label = "consumer", description = "The platform event schema.", javaType = "Object")
    public static final String HEADER_SALESFORCE_PLATFORM_EVENT_SCHEMA = "CamelSalesforcePlatformEventSchema";
    @Metadata(label = "consumer", description = "The created date.", javaType = "java.time.ZonedDateTime")
    public static final String HEADER_SALESFORCE_CREATED_DATE = "CamelSalesforceCreatedDate";
    @Metadata(label = "consumer", description = "The topic name.", javaType = "String")
    public static final String HEADER_SALESFORCE_TOPIC_NAME = "CamelSalesforceTopicName";
    @Metadata(label = "consumer", description = "The channel.", javaType = "String")
    public static final String HEADER_SALESFORCE_CHANNEL = "CamelSalesforceChannel";
    @Metadata(label = "consumer", description = "The client id.", javaType = "String")
    public static final String HEADER_SALESFORCE_CLIENT_ID = "CamelSalesforceClientId";

    @Metadata(label = "producer", description = "Total number of records matching a query.", javaType = "int")
    public static final String HEADER_SALESFORCE_QUERY_RESULT_TOTAL_SIZE = "CamelSalesforceQueryResultTotalSize";

    private SalesforceConstants() {

    }
}
