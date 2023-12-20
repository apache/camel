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
package org.apache.camel.component.azure.cosmosdb.client;

import java.util.stream.Stream;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.apache.camel.component.azure.cosmosdb.CosmosDbConfiguration;
import org.apache.camel.component.azure.cosmosdb.CredentialType;
import org.apache.camel.util.ObjectHelper;

public final class CosmosDbClientFactory {

    private CosmosDbClientFactory() {
    }

    public static CosmosAsyncClient createCosmosAsyncClient(final CosmosDbConfiguration configuration) {
        return createBasicClient(configuration)
                .buildAsyncClient();
    }

    public static CosmosClient createCosmosSyncClient(final CosmosDbConfiguration configuration) {
        return createBasicClient(configuration)
                .buildClient();
    }

    private static CosmosClientBuilder createBasicClient(final CosmosDbConfiguration configuration) {

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(configuration.getDatabaseEndpoint())
                .contentResponseOnWriteEnabled(configuration.isContentResponseOnWriteEnabled())
                .consistencyLevel(configuration.getConsistencyLevel())
                .connectionSharingAcrossClientsEnabled(configuration.isConnectionSharingAcrossClientsEnabled())
                .clientTelemetryEnabled(configuration.isClientTelemetryEnabled())
                .multipleWriteRegionsEnabled(configuration.isMultipleWriteRegionsEnabled())
                .readRequestsFallbackEnabled(configuration.isReadRequestsFallbackEnabled());
        if (ObjectHelper.isNotEmpty(configuration.getPreferredRegions())) {
            builder.preferredRegions(Stream.of(configuration.getPreferredRegions().split(","))
                    .map(String::trim)
                    .toList());
        }
        if (configuration.getCredentialType().equals(CredentialType.AZURE_IDENTITY)) {
            builder.credential(new DefaultAzureCredentialBuilder().build());
        } else {
            builder.key(configuration.getAccountKey());
        }
        return builder;
    }
}
