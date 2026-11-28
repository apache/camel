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
package org.apache.camel.component.azure.eventgrid;

import org.apache.camel.spi.Metadata;

public final class EventGridConstants {
    public static final String HEADER_PREFIX = "CamelAzureEventGrid";

    @Metadata(description = "The type of the event, the type attribute of the CloudEvent. If not set, the producer uses"
                            + " Camel.Event.",
              javaType = "String")
    public static final String EVENT_TYPE = HEADER_PREFIX + "EventType";

    @Metadata(description = "The subject of the event, the subject attribute of the CloudEvent. If not set, the producer"
                            + " uses /camel/event.",
              javaType = "String")
    public static final String SUBJECT = HEADER_PREFIX + "Subject";

    @Metadata(description = "The time when the event happened, the time attribute of the CloudEvent. If not set, the"
                            + " producer uses the current time.",
              javaType = "java.time.OffsetDateTime")
    public static final String EVENT_TIME = HEADER_PREFIX + "EventTime";

    @Metadata(description = "The ID of the event, the id attribute of the CloudEvent. If not set, the producer uses a"
                            + " random UUID for each event. For a List body, every event gets this ID.",
              javaType = "String")
    public static final String ID = HEADER_PREFIX + "Id";

    private EventGridConstants() {
    }
}
