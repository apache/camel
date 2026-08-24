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

    @Metadata(label = "producer", description = "Event bus name override", javaType = "String")
    public static final String EVENT_BUS_NAME = "CamelAlibabaEventBridgeEventBusName";

    @Metadata(label = "producer", description = "Event source override", javaType = "String")
    public static final String EVENT_SOURCE = "CamelAlibabaEventBridgeEventSource";

    @Metadata(label = "producer", description = "Event type override", javaType = "String")
    public static final String EVENT_TYPE = "CamelAlibabaEventBridgeEventType";

    @Metadata(label = "producer", description = "Event subject override", javaType = "String")
    public static final String EVENT_SUBJECT = "CamelAlibabaEventBridgeEventSubject";

    @Metadata(label = "producer", description = "Alibaba Cloud request id", javaType = "String")
    public static final String REQUEST_ID = "CamelAlibabaEventBridgeRequestId";

    AlibabaEventBridgeHeaders() {
    }
}
