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
package org.apache.camel.component.azure.servicebus;

public final class SbConstants {
    public static final String BROKER_PROPERTIES = "CamelAzureSbBrokerProperties";
    public static final String CONTENT_TYPE = "CamelAzureContentType";
    public static final String DATE = "CamelAzureSb";
    public static final String CUSTOM_PROPERTIES = "CamelAzureSb";

    public static final String MESSAGE_ID = "CamelAzureMessageId";
    public static final String CORRELATION_ID = "CamelAzureCorrelationId";
    public static final String DELIVERY_COUNT = "CamelAzureDeliveryCount";
    public static final String LABEL = "CamelAzureLabel";
    public static final String LOCKED_UNTIL_UTC = "CamelAzureLockedUntilUtc";
    public static final String LOCK_LOCATION = "CamelAzureLockLocation";
    public static final String LOCK_TOKEN = "CamelAzureLockToken";
    public static final String MESSAGE_LOCATION = "CamelAzureMessageLocation";
    public static final String PARTITION_KEY = "CamelAzurePartitionKey";
    public static final String REPLY_TO = "CamelAzureReplyTo";
    public static final String REPLY_TO_SESSION_ID = "CamelAzureReplyToSessionId";
    public static final String SCHEDULED_ENQUEUE_TIME_UTC = "CamelAzureScheduledEnqueueTimeUtc";
    public static final String SEQUENCE_NUMBER = "CamelAzureSequenceNumber";
    public static final String SESSION_ID = "CamelAzureSessionId";
    public static final String TIME_TO_LIVE = "CamelAzureTimeToLive";
    public static final String TO = "CamelAzureTo";
    public static final String VIA_PARTITION_KEY = "CamelAzureViaPartitionKey";

    public static final String DEFAULT_CONTENT_TYPE = "application/json";

    public enum EntityType {
        QUEUE,
        TOPIC,
        EVENT
    }

    private SbConstants() {
    }
}
