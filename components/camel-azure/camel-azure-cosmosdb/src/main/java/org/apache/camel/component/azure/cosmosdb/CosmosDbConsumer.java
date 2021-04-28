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
package org.apache.camel.component.azure.cosmosdb;

import java.util.function.Consumer;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbContainerOperations;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;

public class CosmosDbConsumer extends DefaultConsumer {

    private Synchronization onCompletion;
    private CosmosAsyncClientWrapper clientWrapper;

    public CosmosDbConsumer(final CosmosDbEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.clientWrapper = new CosmosAsyncClientWrapper(getEndpoint().getCosmosAsyncClient());
        this.onCompletion = new ConsumerOnCompletion();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start consuming events
        if (ObjectHelper.isNotEmpty(getConfiguration().getItemId())
                && ObjectHelper.isNotEmpty(getConfiguration().getItemPartitionKey())) {
            // if we have only itemId, just read that
            readItem(this::onEventListener, this::onErrorListener);
        } else if (ObjectHelper.isNotEmpty(getConfiguration().getItemPartitionKey())) {
            // we have partitionKey, we just get all items in a container
            readAllItems(this::onEventListener, this::onErrorListener);
        } else if (ObjectHelper.isNotEmpty(getConfiguration().getQuery())) {
            // if we have query, we run the query instead
            queryItems(this::onEventListener, this::onErrorListener);
        } else {
            throw new IllegalArgumentException(
                    "To consume you need to either set itemId/partitionKey or partitionKey or query.");
        }
    }

    public CosmosDbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public CosmosDbEndpoint getEndpoint() {
        return (CosmosDbEndpoint) super.getEndpoint();
    }

    private void onEventListener(final Exchange exchange) {
        // add exchange callback
        exchange.adapt(ExtendedExchange.class).addOnCompletion(onCompletion);
        // use default consumer callback
        getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
    }

    private void onErrorListener(final Throwable error) {
        getExceptionHandler().handleException("Error processing exchange", error);
    }

    private void readAllItems(final Consumer<Exchange> resultCallback, final Consumer<Throwable> errorCallback) {
        getContainerOperations()
                .readAllItemsAsFeed(getConfiguration().getItemPartitionKey(), getConfiguration().getQueryRequestOptions(),
                        Object.class)
                .subscribe(tFeedResponse -> resultCallback.accept(createAzureCosmosDbExchange(tFeedResponse)), errorCallback);
    }

    private void queryItems(final Consumer<Exchange> resultCallback, final Consumer<Throwable> errorCallback) {
        getContainerOperations()
                .queryItemsAsFeed(getConfiguration().getQuery(), getConfiguration().getQueryRequestOptions(), Object.class)
                .subscribe(tFeedResponse -> resultCallback.accept(createAzureCosmosDbExchange(tFeedResponse)), errorCallback);
    }

    private void readItem(final Consumer<Exchange> resultCallback, final Consumer<Throwable> errorCallback) {
        getContainerOperations()
                .readItem(getConfiguration().getItemId(), getConfiguration().getItemPartitionKey(), null, Object.class)
                .subscribe(itemResponse -> resultCallback.accept(createAzureCosmosDbExchange(itemResponse)), errorCallback);
    }

    private <T> Exchange createAzureCosmosDbExchange(final FeedResponse<T> tFeedResponse) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();
        // set body
        message.setBody(tFeedResponse.getResults());
        // set headers
        message.setHeader(CosmosDbConstants.RESPONSE_HEADERS, tFeedResponse.getResponseHeaders());

        return exchange;
    }

    private <T> Exchange createAzureCosmosDbExchange(final CosmosItemResponse<T> itemResponse) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();
        // set body
        message.setBody(itemResponse.getItem());
        // set headers
        message.setHeader(CosmosDbConstants.RESPONSE_HEADERS, itemResponse.getResponseHeaders());
        message.setHeader(CosmosDbConstants.E_TAG, itemResponse.getETag());

        return exchange;
    }

    private CosmosDbContainerOperations getContainerOperations() {
        return CosmosDbUtils.getContainerOperations(null, new CosmosDbConfigurationOptionsProxy(getConfiguration()),
                clientWrapper);
    }

    private class ConsumerOnCompletion extends SynchronizationAdapter {

        @Override
        public void onFailure(Exchange exchange) {
            final Exception cause = exchange.getException();
            if (cause != null) {
                getExceptionHandler().handleException("Error during processing exchange.", exchange, cause);
            }
        }
    }
}
