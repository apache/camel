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
package org.apache.camel.component.knative.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public final class Knative {
    public static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

    public static final String MIME_STRUCTURED_CONTENT_MODE = "application/cloudevents+json";
    public static final String MIME_BATCH_CONTENT_MODE = "application/cloudevents-batch+json";

    public static final String KNATIVE_TRANSPORT_RESOURCE_PATH = "META-INF/services/org/apache/camel/knative/transport/";

    public static final String KNATIVE_FILTER_PREFIX = "filter.";
    public static final String KNATIVE_CE_OVERRIDE_PREFIX = "ce.override.";
    public static final String KNATIVE_TYPE = "knative.type";
    public static final String KNATIVE_CLOUD_EVENT_TYPE = "knative.event.type";
    public static final String KNATIVE_REPLY = "knative.reply";
    public static final String CONTENT_TYPE = "content.type";
    public static final String CAMEL_ENDPOINT_KIND = "camel.endpoint.kind";

    public static final String SERVICE_META_PATH = "service.path";
    public static final String SERVICE_META_URL = "service.url";

    public static final String KNATIVE_OBJECT_API_VERSION = "knative.apiVersion";
    public static final String KNATIVE_OBJECT_KIND = "knative.kind";
    public static final String KNATIVE_OBJECT_NAME = "knative.name";

    private Knative() {
    }

    public enum EndpointKind {
        source,
        sink,
    }

    public enum Type {
        endpoint,
        channel,
        event
    }

    public enum Protocol {
        http,
    }
}
