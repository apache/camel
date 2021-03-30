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
package org.apache.camel.component.azure.cosmosdb.operations;

import org.apache.camel.component.azure.cosmosdb.CosmosDbConfiguration;
import org.apache.camel.component.azure.cosmosdb.CosmosDbConfigurationOptionsProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CosmosDbProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDbProducerOperations.class);

    private final CosmosDbConfigurationOptionsProxy configurationOptionsProxy;

    public CosmosDbProducerOperations(
                                      final CosmosDbConfiguration configuration) {
        //ObjectHelper.notNull(producerAsyncClient, "client cannot be null");

        //this.producerAsyncClient = producerAsyncClient;
        configurationOptionsProxy = new CosmosDbConfigurationOptionsProxy(configuration);
    }
}
