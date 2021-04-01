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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbDatabaseOperations;
import org.apache.camel.support.DefaultAsyncProducer;

public class CosmosDbProducer extends DefaultAsyncProducer {

    private CosmosDbDatabaseOperations producerOperations;

    public CosmosDbProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create the client
        //producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(getEndpoint().getConfiguration());

        // create our operations
        //producerOperations = new CosmosDbDatabaseOperations(getConfiguration());
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            //return producerOperations.sendEvents(exchange, callback);
            return false;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

    }

    @Override
    protected void doStop() throws Exception {
        /*if (producerAsyncClient != null) {
            // shutdown async client
            producerAsyncClient.close();
        }*/

        super.doStop();
    }

    @Override
    public CosmosDbEndpoint getEndpoint() {
        return (CosmosDbEndpoint) super.getEndpoint();
    }

    public CosmosDbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
