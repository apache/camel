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
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.SendOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
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

    public EventHubsProducerOperations(final EventHubProducerAsyncClient producerAsyncClient, final EventHubsConfiguration configuration) {
        ObjectHelper.notNull(producerAsyncClient, "client cannot be null");

        this.producerAsyncClient = producerAsyncClient;
        configurationOptionsProxy = new EventHubsConfigurationOptionsProxy(configuration);
    }

    public boolean sendEvents(final Exchange exchange, final AsyncCallback callback) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        final SendOptions sendOptions = createSendOptions(configurationOptionsProxy.getPartitionKey(exchange), configurationOptionsProxy.getPartitionId(exchange));
        final Iterable<EventData> eventData = createEventData(exchange);

        return sendAsyncEvents(eventData, sendOptions, exchange, callback);
    }

    private boolean sendAsyncEvents(final Iterable<EventData> eventData, final SendOptions sendOptions, final Exchange exchange, final AsyncCallback asyncCallback) {
        final AtomicBoolean done = new AtomicBoolean(false);

        sendAsyncEventsWithSuitableMethod(eventData, sendOptions)
                .subscribe(unused -> {
                    // we finished only with one event, keep async check
                    LOG.debug("Processed one event...");
                    asyncCallback.done(false);
                    done.set(false);
                }, error -> {
                    // error but we continue
                    LOG.debug("Error processing async exchange with error:" + error.getMessage());
                    exchange.setException(error);
                    asyncCallback.done(false);
                    done.set(false);
                }, () -> {
                    // we are done from everything, so mark it as sync done
                    LOG.debug("All events with exchange have been sent successfully.");
                    asyncCallback.done(true);
                    done.set(true);
                });

        return done.get();
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

    private Iterable<EventData> createEventData(final Exchange exchange) {
        final byte[] data = exchange.getIn().getBody(byte[].class);

        if (ObjectHelper.isEmpty(data)) {
            throw new IllegalArgumentException(String.format("Cannot convert message body %s to byte[]. You will need "
                    + "to make sure the data encoded in byte[] or add a Camel TypeConverter to convert the data to byte[]", exchange.getIn().getBody()));
        }
        // for now we only support single event
        return Collections.singletonList(new EventData(data));
    }
}
