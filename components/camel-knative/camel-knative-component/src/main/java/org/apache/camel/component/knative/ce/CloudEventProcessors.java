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
package org.apache.camel.component.knative.ce;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.component.knative.KnativeEndpoint;
import org.apache.camel.component.knative.spi.KnativeResource;

import static org.apache.camel.util.ObjectHelper.ifNotEmpty;

public enum CloudEventProcessors implements CloudEventProcessor {

    v1_0(new AbstractCloudEventProcessor(CloudEvents.v1_0) {
        @Override
        protected void decodeStructuredContent(Exchange exchange, Map<String, Object> content) {
            final CloudEvent ce = cloudEvent();
            final Message message = exchange.getIn();

            // body
            ifNotEmpty(content.remove("data"), message::setBody);

            ifNotEmpty(content.remove(ce.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json()), val -> {
                message.setHeader(Exchange.CONTENT_TYPE, val);
            });

            for (CloudEvent.Attribute attribute : ce.attributes()) {
                ifNotEmpty(content.remove(attribute.json()), val -> {
                    message.setHeader(attribute.id(), val);
                });
            }

            //
            // Map every remaining field as it is (extensions).
            //
            content.forEach((key, val) -> {
                message.setHeader(key.toLowerCase(Locale.US), val);
            });
        }
    }),
    v1_0_1(new AbstractCloudEventProcessor(CloudEvents.v1_0_1) {
        @Override
        protected void decodeStructuredContent(Exchange exchange, Map<String, Object> content) {
            final CloudEvent ce = cloudEvent();
            final Message message = exchange.getIn();

            // body
            ifNotEmpty(content.remove("data"), message::setBody);

            ifNotEmpty(content.remove(ce.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json()), val -> {
                message.setHeader(Exchange.CONTENT_TYPE, val);
            });

            for (CloudEvent.Attribute attribute : ce.attributes()) {
                ifNotEmpty(content.remove(attribute.json()), val -> {
                    message.setHeader(attribute.id(), val);
                });
            }

            //
            // Map every remaining field as it is (extensions).
            //
            content.forEach((key, val) -> {
                message.setHeader(key.toLowerCase(Locale.US), val);
            });
        }
    }),
    v1_0_2(new AbstractCloudEventProcessor(CloudEvents.v1_0_2) {
        @Override
        protected void decodeStructuredContent(Exchange exchange, Map<String, Object> content) {
            final CloudEvent ce = cloudEvent();
            final Message message = exchange.getIn();

            // body
            ifNotEmpty(content.remove("data"), message::setBody);

            ifNotEmpty(content.remove(ce.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json()), val -> {
                message.setHeader(Exchange.CONTENT_TYPE, val);
            });

            for (CloudEvent.Attribute attribute : ce.attributes()) {
                ifNotEmpty(content.remove(attribute.json()), val -> {
                    message.setHeader(attribute.id(), val);
                });
            }

            //
            // Map every remaining field as it is (extensions).
            //
            content.forEach((key, val) -> {
                message.setHeader(key.toLowerCase(Locale.US), val);
            });
        }
    });

    private final CloudEventProcessor instance;

    CloudEventProcessors(CloudEventProcessor instance) {
        this.instance = instance;
    }

    @Override
    public CloudEvent cloudEvent() {
        return instance.cloudEvent();
    }

    @Override
    public Processor consumer(KnativeEndpoint endpoint, KnativeResource service) {
        return instance.consumer(endpoint, service);
    }

    @Override
    public Processor producer(KnativeEndpoint endpoint, KnativeResource service) {
        return instance.producer(endpoint, service);
    }

    public static CloudEventProcessor fromSpecVersion(String version) {
        for (CloudEventProcessor processor : CloudEventProcessors.values()) {
            if (Objects.equals(processor.cloudEvent().version(), version)) {
                return processor;
            }
        }

        throw new IllegalArgumentException("Unable to find an implementation for CloudEvents spec: " + version);
    }
}
