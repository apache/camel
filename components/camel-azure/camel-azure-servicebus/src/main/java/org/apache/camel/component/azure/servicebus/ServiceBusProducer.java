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
package org.apache.camel.component.azure.servicebus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.servicebus.client.ServiceBusClientFactory;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class ServiceBusProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceBusProducer.class);

    private ServiceBusSenderAsyncClientWrapper senderClientWrapper;
    private ServiceBusConfigurationOptionsProxy configurationOptionsProxy;
    private final Map<ServiceBusOperationDefinition, BiConsumer<Exchange, AsyncCallback>> operations = new HashMap<>();


    public ServiceBusProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create the senderClient
        final ServiceBusSenderAsyncClient senderClient = ServiceBusClientFactory.createServiceBusSenderAsyncClient(getConfiguration());

        // create the wrapper
        senderClientWrapper = new ServiceBusSenderAsyncClientWrapper(senderClient);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            return false;
            //return producerOperations.sendEvents(exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

    }

    @Override
    protected void doStop() throws Exception {

        if (senderClientWrapper != null) {
            // shutdown async client
            senderClientWrapper.close();
        }

        super.doStop();
    }

    @Override
    public ServiceBusEndpoint getEndpoint() {
        return (ServiceBusEndpoint) super.getEndpoint();
    }

    public ServiceBusConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private BiConsumer<Exchange, AsyncCallback> receiveMessages() {
        return ((exchange, callback) -> {
            //final Flux<ServiceBusReceivedMessage> receivedMessage =
        });
    }

    private <T> void subscribeToFlux(
            final Flux<T> inputFlux, final Exchange exchange, final Consumer<T> resultsCallback, final AsyncCallback callback) {
        inputFlux
                .subscribe(resultsCallback, error -> {
                    // error but we continue
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Error processing async exchange with error: {}", error.getMessage());
                    }
                    exchange.setException(error);
                    callback.done(false);
                }, () -> {
                    // we are done from everything, so mark it as sync done
                    LOG.trace("All events with exchange have been sent successfully.");
                    callback.done(false);
                });
    }
}
