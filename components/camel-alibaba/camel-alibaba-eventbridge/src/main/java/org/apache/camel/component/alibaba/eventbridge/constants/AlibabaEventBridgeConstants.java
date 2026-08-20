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
package org.apache.camel.component.alibaba.eventbridge.constants;

import org.apache.camel.spi.Metadata;

/**
 * Constants for Alibaba EventBridge
 */
public final class AlibabaEventBridgeConstants {

    @Metadata(label = "producer", description = "Event bus name override", javaType = "String")
    public static final String EVENT_BUS_NAME = "eventBusName";

    @Metadata(label = "producer", description = "Event source override", javaType = "String")
    public static final String EVENT_SOURCE = "eventSource";

    @Metadata(label = "producer", description = "Event type override", javaType = "String")
    public static final String EVENT_TYPE = "eventType";

    @Metadata(label = "producer", description = "Event subject override", javaType = "String")
    public static final String EVENT_SUBJECT = "eventSubject";

    @Metadata(label = "producer", description = "Event data override", javaType = "String")
    public static final String EVENT_DATA = "eventData";

    @Metadata(label = "producer", description = "Event request identifier", javaType = "String")
    public static final String EVENT_RESPONSE_REQUEST_IDENTIFIER = "requestId";

    @Metadata(label = "producer", description = "Event resource owner account identifier", javaType = "String")
    public static final String EVENT_RESPONSE_RESOURCE_OWNER_ACCOUNT_IDENTIFIER = "resourceOwnerAccountId";

    @Metadata(label = "producer", description = "Event failed entry count", javaType = "Integer")
    public static final String EVENT_RESPONSE_FAILED_ENTRY_COUNT = "failedEntryCount";

    @Metadata(label = "producer", description = "Event id", javaType = "String")
    public static final String EVENT_RESPONSE_ID = "eventId";

    @Metadata(label = "producer", description = "Event error code", javaType = "String")
    public static final String EVENT_RESPONSE_ERROR_CODE = "errorCode";

    @Metadata(label = "producer", description = "Event error message", javaType = "String")
    public static final String EVENT_RESPONSE_ERROR_MESSAGE = "errorMessage";

    @Metadata(label = "producer", description = "Event entry list", javaType = "List<Map<String, Object>>")
    public static final String EVENT_RESPONSE_ENTRY_LIST = "entryList";

    private AlibabaEventBridgeConstants() {
    }
}
