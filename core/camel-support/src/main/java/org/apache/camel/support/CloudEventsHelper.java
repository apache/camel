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

package org.apache.camel.support;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.camel.Exchange;

/**
 * Helper class to manage CloudEvents specific Camel message headers and other utilities.
 */
public class CloudEventsHelper {

    public static final String CAMEL_CLOUD_EVENT_ID = "CamelCloudEventID";
    public static final String CAMEL_CLOUD_EVENT_VERSION = "CamelCloudEventVersion";
    public static final String CAMEL_CLOUD_EVENT_TYPE = "CamelCloudEventType";
    public static final String CAMEL_CLOUD_EVENT_SOURCE = "CamelCloudEventSource";
    public static final String CAMEL_CLOUD_EVENT_SUBJECT = "CamelCloudEventSubject";
    public static final String CAMEL_CLOUD_EVENT_TIME = "CamelCloudEventTime";
    public static final String CAMEL_CLOUD_EVENT_CONTENT_TYPE = Exchange.CONTENT_TYPE;

    public static String getEventTime(Exchange exchange) {
        final ZonedDateTime created
                = ZonedDateTime.ofInstant(Instant.ofEpochMilli(exchange.getCreated()), ZoneId.systemDefault());
        return DateTimeFormatter.ISO_INSTANT.format(created);
    }
}
