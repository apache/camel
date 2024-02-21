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

import java.util.Collection;
import java.util.Objects;

public enum CloudEvents implements CloudEvent {

    //
    // V1.0 - https://github.com/cloudevents/spec/blob/v1.0/spec.md
    //
    v1_0(new CloudEventImpl(
            "1.0",
            CloudEventAttributes.V1_0_ATTRIBUTES)),

    //
    // V1.0.1 - https://github.com/cloudevents/spec/blob/v1.0.1/spec.md
    //
    v1_0_1(new CloudEventImpl(
            "1.0.1",
            CloudEventAttributes.V1_0_ATTRIBUTES)),

    //
    // V1.0.2 - https://github.com/cloudevents/spec/blob/v1.0.2/spec.md
    //
    v1_0_2(new CloudEventImpl(
            "1.0.2",
            CloudEventAttributes.V1_0_ATTRIBUTES));

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

        throw new IllegalArgumentException("Cannot find an implementation for CloudEvents spec: " + version);
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
