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
package org.apache.camel.component.cloudevents;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public enum CloudEvents implements CloudEvent {
    //
    // V0.1 - https://github.com/cloudevents/spec/blob/v0.1/spec.md
    //
    v0_1(new CloudEventImpl(
            "0.1",
            Arrays.asList(
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "CE-EventType", "eventType"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TYPE_VERSION, "CE-EventTypeVersion", "eventTypeVersion"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, "CE-CloudEventsVersion", "cloudEventsVersion"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "CE-Source", "source"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_ID, "CE-EventID", "eventID"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TIME, "CE-EventTime", "eventTime"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SCHEMA_URL, "CE-SchemaURL", "schemaURL"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "Content-Type", "contentType"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_EXTENSIONS, "CE-Extensions", "extensions")))),
    //
    // V0.2 - https://github.com/cloudevents/spec/blob/v0.2/spec.md
    //
    v0_2(new CloudEventImpl(
            "0.2",
            Arrays.asList(
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "ce-type", "type"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, "ce-specversion", "specversion"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "ce-source", "source"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_ID, "ce-id", "id"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TIME, "ce-time", "time"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SCHEMA_URL, "ce-schemaurl", "schemaurl"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "Content-Type", "contenttype")))),
    //
    // V0.3 - https://github.com/cloudevents/spec/blob/v0.3/spec.md
    //
    v0_3(new CloudEventImpl(
            "0.3",
            Arrays.asList(
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_ID, "ce-id", "id"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "ce-source", "source"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, "ce-specversion", "specversion"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "ce-type", "type"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_ENCODING, "ce-datacontentencoding",
                            "datacontentencoding"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE, "ce-datacontenttype", "datacontenttype"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SCHEMA_URL, "ce-schemaurl", "schemaurl"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, "ce-subject", "subject"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TIME, "ce-time", "time")))),
    //
    // V1.0 - https://github.com/cloudevents/spec/blob/v1.0/spec.md
    //
    v1_0(new CloudEventImpl(
            "1.0",
            Arrays.asList(
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_ID, "ce-id", "id"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "ce-source", "source"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_VERSION, "ce-specversion", "specversion"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "ce-type", "type"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE, "ce-datacontenttype", "datacontenttype"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SCHEMA_URL, "ce-dataschema", "dataschema"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, "ce-subject", "subject"),
                    Attribute.simple(CloudEvent.CAMEL_CLOUD_EVENT_TIME, "ce-time", "time"))));

    private final CloudEvent instance;

    CloudEvents(CloudEvent instance) {
        this.instance = instance;
    }

    @Override
    public String version() {
        return instance.version();
    }

    @Override
    public Collection<Attribute> attributes() {
        return instance.attributes();
    }

    public static CloudEvent fromSpecVersion(String version) {
        for (CloudEvent event : CloudEvents.values()) {
            if (Objects.equals(event.version(), version)) {
                return event;
            }
        }

        throw new IllegalArgumentException("Unable to find an implementation fo CloudEvents spec: " + version);
    }

    private static class CloudEventImpl implements CloudEvent {
        private final String version;
        private final Collection<Attribute> attributes;

        public CloudEventImpl(String version, Collection<Attribute> attributes) {
            this.version = version;
            this.attributes = attributes;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public Collection<Attribute> attributes() {
            return attributes;
        }
    }
}
