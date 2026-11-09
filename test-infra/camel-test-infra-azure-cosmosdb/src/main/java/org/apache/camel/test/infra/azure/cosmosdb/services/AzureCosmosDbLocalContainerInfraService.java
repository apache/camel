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

package org.apache.camel.test.infra.azure.cosmosdb.services;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.azure.common.AzureCredentialsHolder;
import org.apache.camel.test.infra.azure.common.services.FlociAzContainer;
import org.apache.camel.test.infra.azure.common.services.FlociAzInfraService;
import org.apache.camel.test.infra.azure.cosmosdb.common.AzureCosmosDbProperties;

/**
 * A local Cosmos DB for NoSQL account: Floci AZ emulates the gateway API of the account in process (databases,
 * containers, items and their SQL queries). The Java SDK connects to the gateway over TLS only, so the container serves
 * HTTPS next to HTTP on its port, with a certificate it issues when it starts.
 * <p>
 * A client of the service works in gateway mode, and skips the validation of the certificate of the emulator with the
 * {@code COSMOS.EMULATOR_SERVER_CERTIFICATE_VALIDATION_DISABLED} system property, or the
 * {@code COSMOS_EMULATOR_SERVER_CERTIFICATE_VALIDATION_DISABLED} environment variable, set to {@code true}:
 * {@code AzureCosmosDbClientUtils} builds such a client. The azure-cosmosdb component builds a direct-mode client,
 * which Floci AZ does not serve, so a route uses the service through a client of its own, set as its
 * {@code cosmosAsyncClient}.
 * <p>
 * Floci AZ does not emulate the change feed, the throughput of the databases and containers, nor the queries of the
 * databases and containers.
 */
@InfraService(service = AzureCosmosDbInfraService.class,
              description = "Local Azure Cosmos DB service with Floci AZ",
              serviceAlias = "azure", serviceImplementationAlias = "cosmos-db")
public class AzureCosmosDbLocalContainerInfraService extends FlociAzInfraService implements AzureCosmosDbInfraService {
    /**
     * The well-known key of the Cosmos DB emulator (Floci AZ accepts any key)
     */
    public static final String EMULATOR_KEY
            = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";

    @Override
    protected FlociAzContainer initContainer(String imageName) {
        return super.initContainer(imageName)
                .withEnv("FLOCI_AZ_TLS_ENABLED", "true");
    }

    @Override
    public void registerProperties() {
        super.registerProperties();
        System.setProperty(AzureCosmosDbProperties.ENDPOINT, endpoint());
        System.setProperty(AzureCosmosDbProperties.ACCESS_KEY, accessKey());
    }

    /**
     * The endpoint of the account, for example {@code https://localhost:32768/devstoreaccount1-cosmos}. The Java SDK
     * sends its requests to the root path of the endpoint, which Floci AZ serves for this account too.
     */
    @Override
    public String endpoint() {
        return String.format("https://%s:%d/%s-cosmos", host(), port(), accountName());
    }

    @Override
    public String accessKey() {
        return EMULATOR_KEY;
    }

    @Override
    public AzureCredentialsHolder azureCredentials() {
        String accountName = accountName();
        return new AzureCredentialsHolder() {
            @Override
            public String accountName() {
                return accountName;
            }

            @Override
            public String accountKey() {
                return EMULATOR_KEY;
            }
        };
    }
}
