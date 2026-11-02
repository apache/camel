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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.models.CloudEvent;
import com.azure.core.models.CloudEventDataFormat;
import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import com.azure.json.JsonToken;
import com.azure.json.JsonWriter;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventGridProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventGridProducer.class);
    private static final String SOURCE = "/camel";

    private EventGridPublisherClient<CloudEvent> publisherClient;

    public EventGridProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        EventGridConfiguration configuration = getConfiguration();
        publisherClient = configuration.getPublisherClient();

        if (publisherClient == null) {
            // Create the client
            publisherClient = createEventGridPublisherClient(configuration);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message message = exchange.getMessage();
        final Object body = message.getMandatoryBody();

        // Handle both single event and list of events
        List<CloudEvent> events = new ArrayList<>();

        if (body instanceof List) {
            List<?> bodyList = (List<?>) body;
            for (Object item : bodyList) {
                if (item == null) {
                    throw new IllegalArgumentException(
                            "The List body has a null element: each element is the data of an event");
                }
                events.add(createCloudEvent(exchange, item, message));
            }
        } else {
            events.add(createCloudEvent(exchange, body, message));
        }

        LOG.debug("Publishing {} events to Event Grid", events.size());
        publisherClient.sendEvents(events);
    }

    private CloudEvent createCloudEvent(Exchange exchange, Object data, Message message) throws Exception {
        // Extract CloudEvent properties from headers or use defaults
        String eventType = message.getHeader(EventGridConstants.EVENT_TYPE, String.class);
        String subject = message.getHeader(EventGridConstants.SUBJECT, String.class);
        String id = message.getHeader(EventGridConstants.ID, String.class);
        OffsetDateTime eventTime = message.getHeader(EventGridConstants.EVENT_TIME, OffsetDateTime.class);

        // Set defaults if not provided
        if (ObjectHelper.isEmpty(eventType)) {
            eventType = "Camel.Event";
        }
        if (ObjectHelper.isEmpty(subject)) {
            subject = "/camel/event";
        }
        if (ObjectHelper.isEmpty(id)) {
            id = UUID.randomUUID().toString();
        }
        if (eventTime == null) {
            eventTime = OffsetDateTime.now();
        }

        // Create CloudEvent
        CloudEvent event = newCloudEvent(exchange, eventType, data);
        event.setSubject(subject);
        event.setId(id);
        event.setTime(eventTime);

        return event;
    }

    /**
     * The data of the event: text that is a JSON object or array as it is, other text as a JSON string, bytes, a stream
     * or a file as binary data (data_base64), and a Map, a POJO or a number serialized to JSON.
     */
    private static CloudEvent newCloudEvent(Exchange exchange, String eventType, Object data) throws Exception {
        if (data instanceof CharSequence || data instanceof Reader) {
            String text = data instanceof Reader
                    ? exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, data)
                    : data.toString();
            if (isJsonObjectOrArray(text)) {
                return new CloudEvent(SOURCE, eventType, BinaryData.fromString(text), CloudEventDataFormat.JSON, null);
            }
            return newTextCloudEvent(eventType, text);
        }
        if (data instanceof byte[] || data instanceof InputStream || data instanceof StreamCache
                || data instanceof File || data instanceof Path || data instanceof WrappedFile) {
            byte[] bytes = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, data);
            return new CloudEvent(SOURCE, eventType, BinaryData.fromBytes(bytes), CloudEventDataFormat.BYTES, null);
        }
        return new CloudEvent(SOURCE, eventType, BinaryData.fromObject(data), CloudEventDataFormat.JSON, null);
    }

    /**
     * The CloudEvent constructor does not escape the text of a JSON string (azure-core 1.57), so a quote, a backslash
     * or a line break in the text would make the request invalid JSON. The event is read from its JSON form instead,
     * which keeps the text as it is.
     */
    private static CloudEvent newTextCloudEvent(String eventType, String text) throws IOException {
        StringWriter json = new StringWriter();
        try (JsonWriter writer = JsonProviders.createWriter(json)) {
            writer.writeStartObject()
                    .writeStringField("id", UUID.randomUUID().toString())
                    .writeStringField("source", SOURCE)
                    .writeStringField("type", eventType)
                    .writeStringField("specversion", "1.0")
                    .writeStringField("data", text)
                    .writeEndObject();
        }
        return CloudEvent.fromString(json.toString()).get(0);
    }

    private static boolean isJsonObjectOrArray(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false;
        }
        try (JsonReader reader = JsonProviders.createReader(trimmed)) {
            reader.nextToken();
            reader.skipChildren();
            // nothing may follow the object or array
            return reader.nextToken() == JsonToken.END_DOCUMENT;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private EventGridPublisherClient<CloudEvent> createEventGridPublisherClient(EventGridConfiguration configuration) {
        EventGridPublisherClientBuilder builder = new EventGridPublisherClientBuilder()
                .endpoint(configuration.getTopicEndpoint());

        // Configure authentication based on credential type
        switch (configuration.getCredentialType()) {
            case ACCESS_KEY:
                AzureKeyCredential credential = configuration.getAzureKeyCredential();
                if (credential == null && ObjectHelper.isNotEmpty(configuration.getAccessKey())) {
                    credential = new AzureKeyCredential(configuration.getAccessKey());
                }
                if (credential == null) {
                    throw new IllegalArgumentException("Access key or Azure key credential must be provided");
                }
                builder.credential(credential);
                break;
            case TOKEN_CREDENTIAL:
                if (configuration.getTokenCredential() == null) {
                    throw new IllegalArgumentException("Token credential must be provided");
                }
                builder.credential(configuration.getTokenCredential());
                break;
            case AZURE_IDENTITY:
                builder.credential(new DefaultAzureCredentialBuilder().build());
                break;
            default:
                throw new IllegalArgumentException("Unknown credential type: " + configuration.getCredentialType());
        }

        return builder.buildCloudEventPublisherClient();
    }

    @Override
    protected void doStop() throws Exception {
        // EventGridPublisherClient doesn't need explicit cleanup
        publisherClient = null;
        super.doStop();
    }

    @Override
    public EventGridEndpoint getEndpoint() {
        return (EventGridEndpoint) super.getEndpoint();
    }

    public EventGridConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
