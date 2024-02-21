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
package org.apache.camel.component.azure.eventhubs;

import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.component.azure.eventhubs.operations.EventHubsProducerOperations;
import org.apache.camel.support.DefaultAsyncProducer;

public class EventHubsProducer extends DefaultAsyncProducer {

    private EventHubProducerAsyncClient producerAsyncClient;
    private EventHubsProducerOperations producerOperations;

    public EventHubsProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        EventHubsConfiguration configuration = getConfiguration();
        producerAsyncClient = configuration.getProducerAsyncClient();
        if (producerAsyncClient == null) {
            // create the client
            producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
        }

        // create our operations
        producerOperations = new EventHubsProducerOperations(producerAsyncClient, getConfiguration());
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            return producerOperations.sendEvents(exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

    }

    @Override
    protected void doStop() throws Exception {
        if (producerAsyncClient != null) {
            // shutdown async client
            producerAsyncClient.close();
            producerAsyncClient = null;
        }

        super.doStop();
    }

    @Override
    public EventHubsEndpoint getEndpoint() {
        return (EventHubsEndpoint) super.getEndpoint();
    }

    public EventHubsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
