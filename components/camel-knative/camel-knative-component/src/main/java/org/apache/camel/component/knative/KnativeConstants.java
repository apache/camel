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
package org.apache.camel.component.knative;

import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.spi.Metadata;

public final class KnativeConstants {
    public static final String SCHEME = "knative";
    public static final String CONFIGURATION_ENV_VARIABLE = "CAMEL_KNATIVE_CONFIGURATION";

    @Metadata(label = "common",
              description = "The event ID. The producer uses the exchange ID when not set.", javaType = "String")
    public static final String CLOUD_EVENT_ID = CloudEvent.CAMEL_CLOUD_EVENT_ID;
    @Metadata(label = "common",
              description = "The event source. The producer uses the route ID when not set.", javaType = "String")
    public static final String CLOUD_EVENT_SOURCE = CloudEvent.CAMEL_CLOUD_EVENT_SOURCE;
    @Metadata(label = "common",
              description = "The CloudEvents specification version. The producer uses the configured version when not set.",
              javaType = "String")
    public static final String CLOUD_EVENT_VERSION = CloudEvent.CAMEL_CLOUD_EVENT_VERSION;
    @Metadata(label = "common",
              description = "The event type. On a knative event endpoint with a type ID in the URI, the type ID takes precedence over this header.",
              javaType = "String")
    public static final String CLOUD_EVENT_TYPE = CloudEvent.CAMEL_CLOUD_EVENT_TYPE;
    @Metadata(label = "common", description = "The content type of the event data.", javaType = "String")
    public static final String CLOUD_EVENT_DATA_CONTENT_TYPE = CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE;
    @Metadata(label = "common", description = "The URI of the schema that the event data adheres to.",
              javaType = "String")
    public static final String CLOUD_EVENT_SCHEMA_URL = CloudEvent.CAMEL_CLOUD_EVENT_SCHEMA_URL;
    @Metadata(label = "common", description = "The subject of the event in the context of the event source.",
              javaType = "String")
    public static final String CLOUD_EVENT_SUBJECT = CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT;
    @Metadata(label = "common",
              description = "The time the event occurred. The producer uses the exchange creation time when not set.",
              javaType = "String")
    public static final String CLOUD_EVENT_TIME = CloudEvent.CAMEL_CLOUD_EVENT_TIME;

    private KnativeConstants() {
    }
}
