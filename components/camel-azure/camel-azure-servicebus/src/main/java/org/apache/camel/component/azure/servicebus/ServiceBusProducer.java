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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.servicebus.client.ServiceBusClientFactory;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class ServiceBusProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceBusProducer.class);

    private ServiceBusSenderAsyncClientWrapper senderClientWrapper;
    private ServiceBusConfigurationOptionsProxy configurationOptionsProxy;
    private final Map<ServiceBusProducerOperationDefinition, BiConsumer<Exchange, AsyncCallback>> operations = new HashMap<>();

    {
        //bind(ServiceBusProducerOperationDefinition.receiveMessages, receiveMessages());
    }

    public ServiceBusProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create the senderClient
        final ServiceBusSenderAsyncClient senderClient = getConfiguration().getSenderAsyncClient() != null
                ? getConfiguration().getSenderAsyncClient()
                : ServiceBusClientFactory.createServiceBusSenderAsyncClient(getConfiguration());

        // create the wrapper
        senderClientWrapper = new ServiceBusSenderAsyncClientWrapper(senderClient);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            //invokeOperation(ServiceBusProducerOperationDefinition.receiveMessages, exchange, callback);
            return false;
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

    private void bind(ServiceBusProducerOperationDefinition operation, BiConsumer<Exchange, AsyncCallback> fn) {
        operations.put(operation, fn);
    }

    /**
     * Entry method that selects the appropriate ServiceBusProducerOperationDefinition operation and executes it
     */
    private void invokeOperation(
            final ServiceBusProducerOperationDefinition operation, final Exchange exchange, final AsyncCallback callback) {
        final ServiceBusProducerOperationDefinition operationsToInvoke;

        // we put listDatabases operation as default in case no operation has been selected
        if (ObjectHelper.isEmpty(operation)) {
            operationsToInvoke = ServiceBusProducerOperationDefinition.sendMessages;
        } else {
            operationsToInvoke = operation;
        }

        final BiConsumer<Exchange, AsyncCallback> fnToInvoke = operations.get(operationsToInvoke);

        if (fnToInvoke != null) {
            fnToInvoke.accept(exchange, callback);
        } else {
            throw new RuntimeException("Operation not supported. Value: " + operationsToInvoke);
        }
    }

    private <T> void subscribeToMono(
            final Mono<T> inputMono, final Exchange exchange, final Consumer<T> resultsCallback, final AsyncCallback callback) {
        inputMono
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
