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
package org.apache.camel.component.azure.eventhubs.operations;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.SendOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.EventHubsConfigurationOptionsProxy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class EventHubsProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsProducerOperations.class);

    private final EventHubProducerAsyncClient producerAsyncClient;
    private final EventHubsConfigurationOptionsProxy configurationOptionsProxy;

    public EventHubsProducerOperations(final EventHubProducerAsyncClient producerAsyncClient,
                                       final EventHubsConfiguration configuration) {
        ObjectHelper.notNull(producerAsyncClient, "client cannot be null");

        this.producerAsyncClient = producerAsyncClient;
        configurationOptionsProxy = new EventHubsConfigurationOptionsProxy(configuration);
    }

    public boolean sendEvents(final Exchange exchange, final AsyncCallback callback) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        final SendOptions sendOptions = createSendOptions(configurationOptionsProxy.getPartitionKey(exchange),
                configurationOptionsProxy.getPartitionId(exchange));
        final Iterable<EventData> eventData = createEventData(exchange);

        return sendAsyncEvents(eventData, sendOptions, exchange, callback);
    }

    private boolean sendAsyncEvents(
            final Iterable<EventData> eventData, final SendOptions sendOptions, final Exchange exchange,
            final AsyncCallback asyncCallback) {
        sendAsyncEventsWithSuitableMethod(eventData, sendOptions)
                .subscribe(unused -> LOG.debug("Processed one event..."), error -> {
                    // error but we continue
                    LOG.debug("Error processing async exchange with error: {}", error.getMessage());
                    exchange.setException(error);
                    asyncCallback.done(false);
                }, () -> {
                    // we are done from everything, so mark it as sync done
                    LOG.debug("All events with exchange have been sent successfully.");
                    asyncCallback.done(false);
                });

        return false;
    }

    private Mono<Void> sendAsyncEventsWithSuitableMethod(final Iterable<EventData> eventData, final SendOptions sendOptions) {
        if (ObjectHelper.isEmpty(sendOptions)) {
            return producerAsyncClient.send(eventData);
        }

        return producerAsyncClient.send(eventData, sendOptions);
    }

    private SendOptions createSendOptions(final String partitionKey, final String partitionId) {
        // if both are set, we don't want that
        if (ObjectHelper.isNotEmpty(partitionKey) && ObjectHelper.isNotEmpty(partitionId)) {
            throw new IllegalArgumentException("Both partitionKey and partitionId are set. Only one or the other can be set.");
        }
        // if both are not set, we return null and let EventHubs handle the partition assigning
        if (ObjectHelper.isEmpty(partitionKey) && ObjectHelper.isEmpty(partitionId)) {
            return null;
        }

        return new SendOptions()
                .setPartitionId(partitionId)
                .setPartitionKey(partitionKey);
    }

    @SuppressWarnings("unchecked")
    private Iterable<EventData> createEventData(final Exchange exchange) {
        // check if our exchange is list or contain some values
        if (exchange.getIn().getBody() instanceof Iterable) {
            return createEventDataFromIterable((Iterable<Object>) exchange.getIn().getBody(),
                    exchange.getContext().getTypeConverter(), exchange.getIn().getHeaders());
        }

        // we have only a single event here
        return Collections.singletonList(createEventDataFromExchange(exchange));
    }

    private Iterable<EventData> createEventDataFromIterable(
            final Iterable<Object> inputData, final TypeConverter converter, Map<String, Object> headers) {
        final List<EventData> finalEventData = new LinkedList<>();

        inputData.forEach(data -> {
            if (data instanceof Exchange) {
                finalEventData.add(createEventDataFromExchange((Exchange) data));
            } else if (data instanceof Message) {
                finalEventData.add(createEventDataFromMessage((Message) data));
            } else {
                finalEventData.add(createEventDataFromObject(data, converter, headers));
            }
        });

        return finalEventData;
    }

    private EventData createEventDataFromExchange(final Exchange exchange) {
        return createEventDataFromMessage(exchange.getIn());
    }

    private EventData createEventDataFromMessage(final Message message) {
        return createEventDataFromObject(message.getBody(), message.getExchange().getContext().getTypeConverter(),
                message.getHeaders());
    }

    private EventData createEventDataFromObject(
            final Object inputData, final TypeConverter converter, Map<String, Object> headers) {
        final byte[] data = converter.convertTo(byte[].class, inputData);

        if (ObjectHelper.isEmpty(data)) {
            throw new IllegalArgumentException(
                    String.format("Cannot convert message body %s to byte[]. You will need "
                                  + "to make sure the data encoded in byte[] or add a Camel TypeConverter to convert the data to byte[]",
                            inputData));
        }

        EventData eventData = new EventData(data);
        eventData.getProperties().putAll(headers);

        return eventData;
    }
}
