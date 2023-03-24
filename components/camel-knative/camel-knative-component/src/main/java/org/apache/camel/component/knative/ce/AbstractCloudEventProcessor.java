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

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.knative.KnativeEndpoint;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractCloudEventProcessor implements CloudEventProcessor {
    private final CloudEvent cloudEvent;

    protected AbstractCloudEventProcessor(CloudEvent cloudEvent) {
        this.cloudEvent = cloudEvent;
    }

    @Override
    public CloudEvent cloudEvent() {
        return cloudEvent;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Processor consumer(KnativeEndpoint endpoint, KnativeResource service) {
        return exchange -> {
            if (Objects.equals(exchange.getIn().getHeader(Exchange.CONTENT_TYPE), Knative.MIME_BATCH_CONTENT_MODE)) {
                throw new UnsupportedOperationException("Batched CloudEvents are not yet supported");
            }

            if (!Objects.equals(exchange.getIn().getHeader(Exchange.CONTENT_TYPE), Knative.MIME_STRUCTURED_CONTENT_MODE)) {
                final Map<String, Object> headers = exchange.getIn().getHeaders();

                for (CloudEvent.Attribute attribute : cloudEvent.attributes()) {
                    Object val = headers.remove(attribute.http());
                    if (val != null) {
                        headers.put(attribute.id(), val);
                    }
                }
            } else {
                try (InputStream is = exchange.getIn().getBody(InputStream.class)) {
                    decodeStructuredContent(exchange, Knative.MAPPER.readValue(is, Map.class));
                }
            }
        };
    }

    protected abstract void decodeStructuredContent(Exchange exchange, Map<String, Object> content);

    @Override
    public Processor producer(KnativeEndpoint endpoint, KnativeResource service) {
        final CloudEvent ce = cloudEvent();
        final Logger logger = LoggerFactory.getLogger(getClass());
        final String contentType = service.getContentType();

        return exchange -> {
            final Map<String, Object> headers = exchange.getMessage().getHeaders();

            for (CloudEvent.Attribute attribute : ce.attributes()) {
                Object value = headers.get(attribute.id());
                if (value != null) {
                    headers.putIfAbsent(attribute.http(), value);
                }
            }

            if (contentType != null) {
                headers.putIfAbsent(Exchange.CONTENT_TYPE, contentType);
            }

            //
            // in case of events, if the type of the event is defined as URI param so we need
            // to override it to avoid the event type be overridden by Messages's headers
            //
            if (endpoint.getType() == Knative.Type.event && endpoint.getTypeId() != null) {
                final Object eventType = headers.get(CloudEvent.CAMEL_CLOUD_EVENT_TYPE);
                if (eventType != null) {
                    logger.debug(
                            "Detected the presence of {} header with value {}: it will be ignored and replaced by value set as uri parameter {}",
                            CloudEvent.CAMEL_CLOUD_EVENT_TYPE,
                            eventType,
                            endpoint.getTypeId());
                }

                headers.put(cloudEvent().mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).http(), endpoint.getTypeId());
            } else {
                setCloudEventHeader(headers, CloudEvent.CAMEL_CLOUD_EVENT_TYPE, () -> {
                    String eventType = service.getCloudEventType();
                    if (eventType == null) {
                        eventType = endpoint.getConfiguration().getCloudEventsType();
                    }
                    return eventType;
                });
            }

            setCloudEventHeader(headers, CloudEvent.CAMEL_CLOUD_EVENT_ID, exchange::getExchangeId);
            setCloudEventHeader(headers, CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, exchange::getFromRouteId);
            setCloudEventHeader(headers, CloudEvent.CAMEL_CLOUD_EVENT_VERSION, ce::version);
            setCloudEventHeader(headers, CloudEvent.CAMEL_CLOUD_EVENT_TIME, () -> {
                final ZonedDateTime created
                        = ZonedDateTime.ofInstant(Instant.ofEpochMilli(exchange.getCreated()), ZoneId.systemDefault());

                return DateTimeFormatter.ISO_INSTANT.format(created);
            });

            headers.putAll(service.getCeOverrides());
        };
    }

    protected void setCloudEventHeader(Map<String, Object> headers, String id, Supplier<Object> supplier) {
        headers.putIfAbsent(cloudEvent().mandatoryAttribute(id).http(), supplier.get());
    }
}
