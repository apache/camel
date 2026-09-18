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

/**
 * Constants for Alibaba EventBridge payload and response dictionary keys.
 */
public final class AlibabaEventBridgeConstants {

    public static final String EVENT_BUS_NAME = "eventBusName";
    public static final String EVENT_SOURCE = "eventSource";
    public static final String EVENT_TYPE = "eventType";
    public static final String EVENT_SUBJECT = "eventSubject";
    public static final String EVENT_DATA = "eventData";

    public static final String EVENT_RESPONSE_REQUEST_IDENTIFIER = "requestId";
    public static final String EVENT_RESPONSE_RESOURCE_OWNER_ACCOUNT_IDENTIFIER = "resourceOwnerAccountId";
    public static final String EVENT_RESPONSE_FAILED_ENTRY_COUNT = "failedEntryCount";
    public static final String EVENT_RESPONSE_ID = "eventId";
    public static final String EVENT_RESPONSE_ERROR_CODE = "errorCode";
    public static final String EVENT_RESPONSE_ERROR_MESSAGE = "errorMessage";
    public static final String EVENT_RESPONSE_ENTRY_LIST = "entryList";

    private AlibabaEventBridgeConstants() {
    }
}
