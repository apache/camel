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

import java.util.List;
import java.util.Map;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.implementation.apachecommons.lang.RandomStringUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbContainerOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbOperationsBuilder;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;

public class CosmosDbConsumer extends DefaultConsumer {

    private Synchronization onCompletion;
    private CosmosAsyncClientWrapper clientWrapper;
    private ChangeFeedProcessor changeFeedProcessor;

    public CosmosDbConsumer(final CosmosDbEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.clientWrapper = new CosmosAsyncClientWrapper(getEndpoint().getCosmosAsyncClient());
        this.onCompletion = new ConsumerOnCompletion();
        this.changeFeedProcessor = getContainerOperations().captureEventsWithChangeFeed(
                getLeaseContainerOperations().getContainer(), getHostName(),
                this::onEventListener, getConfiguration().getChangeFeedProcessorOptions());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start our changeFeedProcessor
        changeFeedProcessor.start()
                .subscribe(aVoid -> {
                }, this::onErrorListener);
    }

    @Override
    protected void doStop() throws Exception {
        if (changeFeedProcessor != null) {
            // we wait until it stops
            changeFeedProcessor.stop().block();
        }

        super.doStop();
    }

    public CosmosDbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public CosmosDbEndpoint getEndpoint() {
        return (CosmosDbEndpoint) super.getEndpoint();
    }

    private void onEventListener(final List<Map<String, ?>> recordList) {
        final Exchange exchange = createAzureCosmosDbExchange(recordList);

        // add exchange callback
        exchange.getExchangeExtension().addOnCompletion(onCompletion);
        // use default consumer callback
        getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
    }

    private Exchange createAzureCosmosDbExchange(final List<Map<String, ?>> recordList) {
        final Exchange exchange = createExchange(true);
        final Message message = exchange.getIn();

        message.setBody(recordList);

        return exchange;
    }

    private void onErrorListener(final Throwable error) {
        getExceptionHandler().handleException("Error processing exchange", error);
    }

    private CosmosDbContainerOperations getContainerOperations() {
        return CosmosDbOperationsBuilder.withClient(clientWrapper)
                .withDatabaseName(getConfiguration().getDatabaseName())
                .withCreateDatabaseIfNotExist(getConfiguration().isCreateDatabaseIfNotExists())
                .withContainerName(getConfiguration().getContainerName())
                .withContainerPartitionKeyPath(getConfiguration().getContainerPartitionKeyPath())
                .withCreateContainerIfNotExist(getConfiguration().isCreateContainerIfNotExists())
                .withThroughputProperties(getConfiguration().getThroughputProperties())
                .buildContainerOperations();
    }

    private CosmosDbContainerOperations getLeaseContainerOperations() {
        final String leaseDatabaseName;
        // Lease container need to be created with 'id' path
        final String leaseContainerPartitionKeyPath = "/id";

        if (ObjectHelper.isEmpty(getConfiguration().getLeaseDatabaseName())) {
            leaseDatabaseName = getConfiguration().getDatabaseName();
        } else {
            leaseDatabaseName = getConfiguration().getLeaseDatabaseName();
        }

        return CosmosDbOperationsBuilder.withClient(clientWrapper)
                .withDatabaseName(leaseDatabaseName)
                .withCreateDatabaseIfNotExist(getConfiguration().isCreateLeaseDatabaseIfNotExists())
                .withContainerName(getConfiguration().getLeaseContainerName())
                .withContainerPartitionKeyPath(leaseContainerPartitionKeyPath)
                .withCreateContainerIfNotExist(getConfiguration().isCreateLeaseContainerIfNotExists())
                .withThroughputProperties(getConfiguration().getThroughputProperties())
                .buildContainerOperations();
    }

    private String getHostName() {
        if (ObjectHelper.isEmpty(getConfiguration().getHostName())) {
            return RandomStringUtils.randomAlphabetic(10);
        }

        return getConfiguration().getHostName();
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
