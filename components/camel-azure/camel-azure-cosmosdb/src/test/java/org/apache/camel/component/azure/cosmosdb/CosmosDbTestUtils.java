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

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.cosmos.CosmosAsyncClient;
import org.apache.camel.test.infra.azure.cosmosdb.clients.AzureCosmosDbClientUtils;
import org.apache.camel.test.infra.azure.cosmosdb.common.AzureCosmosDbProperties;
import org.apache.camel.test.infra.azure.cosmosdb.services.AzureCosmosDbService;
import org.apache.camel.test.infra.azure.cosmosdb.services.AzureCosmosDbServiceFactory;
import org.apache.camel.test.junit6.TestSupport;
import org.apache.camel.util.ObjectHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public final class CosmosDbTestUtils {

    private CosmosDbTestUtils() {
    }

    public static Properties loadAzurePropertiesFile() throws IOException {
        return TestSupport.loadExternalProperties(CosmosDbTestUtils.class, "azure_key.properties");
    }

    /**
     * The Cosmos DB account the integration tests run against: a local Floci AZ container, unless the endpoint and
     * accessKey system properties give an account on Azure, for example
     * {@code mvn verify -Dendpoint=https://myaccount.documents.azure.com:443/ -DaccessKey=mykey}
     */
    public static AzureCosmosDbService createService() {
        final String endpoint = System.getProperty("endpoint");
        final String accessKey = System.getProperty("accessKey");

        if (ObjectHelper.isNotEmpty(endpoint) && ObjectHelper.isNotEmpty(accessKey)) {
            System.setProperty(AzureCosmosDbProperties.ENDPOINT, endpoint);
            System.setProperty(AzureCosmosDbProperties.ACCESS_KEY, accessKey);
            System.setProperty(AzureCosmosDbProperties.INSTANCE_TYPE, "remote");
        }

        return AzureCosmosDbServiceFactory.createSingletonService();
    }

    /**
     * A client of the account of {@link #createService()}
     */
    public static CosmosAsyncClient createAsyncClient() {
        return AzureCosmosDbClientUtils.createAsyncClient();
    }

    public static void assertIllegalArgumentException(final Executable executable) {
        assertThrows(IllegalArgumentException.class, executable);
    }

    public static class Latch {
        private final AtomicBoolean doneFlag = new AtomicBoolean();

        public void done() {
            doneFlag.set(true);
        }

        public void await(long timeout) {
            Awaitility
                    .await()
                    .atMost(timeout, TimeUnit.MILLISECONDS)
                    .untilTrue(doneFlag);
        }
    }
}
