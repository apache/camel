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

public sealed class AlibabaEventBridgeHeaders permits AlibabaEventBridgeProperties {

    @Metadata(label = "producer",
              description = "The operation that the producer performs. This header replaces the operation option. If this"
                            + " header is not set, an exchange property of the same name replaces the operation option.",
              javaType = "String", enums = "putEvents")
    public static final String OPERATION = "CamelAlibabaEventBridgeOperation";

    @Metadata(label = "producer",
              description = "The name of the event bus for a JSON text or Map body. This header replaces the eventBusName"
                            + " option. If this header is not set, an exchange property of the same name replaces the"
                            + " eventBusName option. The key eventBusName of a Map body replaces this header.",
              javaType = "String")
    public static final String EVENT_BUS_NAME = "CamelAlibabaEventBridgeEventBusName";

    @Metadata(label = "producer",
              description = "The event source for a JSON text or Map body. The value must be a URI. This header replaces the"
                            + " eventSource option. If this header is not set, an exchange property of the same name replaces"
                            + " the eventSource option. The key eventSource or source of a Map body replaces this header.",
              javaType = "String")
    public static final String EVENT_SOURCE = "CamelAlibabaEventBridgeEventSource";

    @Metadata(label = "producer",
              description = "The event type for a JSON text or Map body. This header replaces the eventType option. If this"
                            + " header is not set, an exchange property of the same name replaces the eventType option. The key"
                            + " eventType or type of a Map body replaces this header.",
              javaType = "String")
    public static final String EVENT_TYPE = "CamelAlibabaEventBridgeEventType";

    @Metadata(label = "producer",
              description = "The event subject for a JSON text or Map body. This header replaces the eventSubject option. If"
                            + " this header is not set, an exchange property of the same name replaces the eventSubject option."
                            + " The key eventSubject or subject of a Map body replaces this header.",
              javaType = "String")
    public static final String EVENT_SUBJECT = "CamelAlibabaEventBridgeEventSubject";

    @Metadata(label = "producer",
              description = "Whether to check that the event bus exists and has a rule that matches the event source. The"
                            + " producer calls listEventBuses and listRules to check this. The producer also checks that each"
                            + " event source that allowedEventSources lists for the event bus matches a rule. This header"
                            + " replaces the validateEventSource option. If this header is not set, an exchange property of the"
                            + " same name replaces the validateEventSource option.",
              javaType = "Boolean")
    public static final String VALIDATE_EVENT_SOURCE = "CamelAlibabaEventBridgeValidateEventSource";

    @Metadata(label = "producer",
              description = "Whether to check that the event bus exists and has a rule that matches the event source and event"
                            + " type. The producer calls listEventBuses and listRules to check this. The producer also checks"
                            + " that each event type that allowedEventSources lists for the event bus matches a rule. This"
                            + " header replaces the validateEventType option. If this header is not set, an exchange property"
                            + " of the same name replaces the validateEventType option.",
              javaType = "Boolean")
    public static final String VALIDATE_EVENT_TYPE = "CamelAlibabaEventBridgeValidateEventType";

    @Metadata(label = "producer",
              description = "Whether to check the keys specversion and id of a Map body. The value of specversion must be 1.0,"
                            + " and the value of id must not be blank. This header replaces the validateEventSpec option. If"
                            + " this header is not set, an exchange property of the same name replaces the validateEventSpec"
                            + " option.",
              javaType = "Boolean")
    public static final String VALIDATE_EVENT_SPEC = "CamelAlibabaEventBridgeValidateEventSpec";

    @Metadata(label = "producer",
              description = "The event sources and event types that the producer allows on each event bus. The value must be a"
                            + " Map, a Collection or a String as in the allowedEventSources option. This header replaces the"
                            + " allowedEventSources option. If this header is not set, an exchange property of the same name"
                            + " replaces the allowedEventSources option.",
              javaType = "Object")
    public static final String ALLOWED_EVENT_SOURCES = "CamelAlibabaEventBridgeAllowedEventSources";

    @Metadata(label = "producer",
              description = "The time in milliseconds for which the producer caches the result of listEventBuses and listRules"
                            + " for an event bus. Only used when validateEventSource or validateEventType is true. Use 0 or"
                            + " less to cache nothing. This header replaces the eventSourceCacheTtl option. If this header is"
                            + " not set, an exchange property of the same name replaces the eventSourceCacheTtl option.",
              javaType = "Long")
    public static final String EVENT_SOURCE_CACHE_TTL = "CamelAlibabaEventBridgeEventSourceCacheTtl";

    @Metadata(label = "producer",
              description = "The request ID that EventBridge returns in the response to the operation. The producer sets it,"
                            + " unless the exchange fails.",
              javaType = "String")
    public static final String REQUEST_ID = "CamelAlibabaEventBridgeRequestId";

    AlibabaEventBridgeHeaders() {
    }
}
