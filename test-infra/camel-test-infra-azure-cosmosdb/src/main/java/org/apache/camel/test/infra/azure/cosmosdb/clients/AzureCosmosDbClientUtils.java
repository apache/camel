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

package org.apache.camel.test.infra.azure.cosmosdb.clients;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.apache.camel.test.infra.azure.cosmosdb.common.AzureCosmosDbProperties;

public final class AzureCosmosDbClientUtils {
    /**
     * The switch of the Cosmos DB SDK that skips the validation of the TLS certificate of an emulator
     */
    public static final String EMULATOR_CERTIFICATE_VALIDATION_DISABLED
            = "COSMOS.EMULATOR_SERVER_CERTIFICATE_VALIDATION_DISABLED";
    /**
     * The host of the emulator, for the SDK, when it is not a local one
     */
    public static final String EMULATOR_HOST = "COSMOS.EMULATOR_HOST";

    // the hosts that the SDK takes as an emulator without COSMOS.EMULATOR_HOST
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private AzureCosmosDbClientUtils() {

    }

    /**
     * Builds a client of the account the tests run against: the local Floci AZ container, unless the
     * {@link AzureCosmosDbProperties#INSTANCE_TYPE} system property is {@code remote}. Like the clients the
     * azure-cosmosdb component builds by default, it returns the items it writes.
     * <p>
     * For Floci AZ, the client works in gateway mode, the only one that Floci AZ emulates (the component builds a
     * direct-mode client, so a route uses the service through a client like this one, set as its
     * {@code cosmosAsyncClient}), keeps to the endpoint whatever locations the emulator advertises, and does not
     * validate the certificate that Floci AZ issues itself for its TLS.
     */
    public static CosmosAsyncClient createAsyncClient() {
        String endpoint = Objects.requireNonNull(System.getProperty(AzureCosmosDbProperties.ENDPOINT),
                "The system property " + AzureCosmosDbProperties.ENDPOINT + " gives the endpoint of the account");

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(System.getProperty(AzureCosmosDbProperties.ACCESS_KEY))
                .contentResponseOnWriteEnabled(true);

        String instanceType = System.getProperty(AzureCosmosDbProperties.INSTANCE_TYPE);
        if (instanceType == null || instanceType.equals("local-azure-container")) {
            trustEmulator(endpoint);
            builder.gatewayMode()
                    .endpointDiscoveryEnabled(false);
        }

        return builder.buildAsyncClient();
    }

    /*
     * The Cosmos DB SDK does not validate the certificate of an emulator, the host localhost, 127.0.0.1, [::1] or the
     * one of COSMOS.EMULATOR_HOST, when COSMOS.EMULATOR_SERVER_CERTIFICATE_VALIDATION_DISABLED is true. It reads both
     * when a client is built, so they apply to the clients built afterwards, and to the emulator host only.
     */
    private static void trustEmulator(String endpoint) {
        System.setProperty(EMULATOR_CERTIFICATE_VALIDATION_DISABLED, "true");
        String host = URI.create(endpoint).getHost();
        if (!LOCAL_HOSTS.contains(host)) {
            System.setProperty(EMULATOR_HOST, host);
        }
    }
}
