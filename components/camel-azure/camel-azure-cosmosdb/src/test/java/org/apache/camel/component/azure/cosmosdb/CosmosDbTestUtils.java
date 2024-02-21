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
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public final class CosmosDbTestUtils {

    private CosmosDbTestUtils() {
    }

    public static Properties loadAzurePropertiesFile() throws IOException {
        final Properties properties = new Properties();
        final String fileName = "azure_key.properties";

        final InputStream inputStream
                = Objects.requireNonNull(CosmosDbTestUtils.class.getClassLoader().getResourceAsStream(fileName));

        properties.load(inputStream);

        return properties;
    }

    public static Properties loadAzureAccessFromJvmEnv() throws Exception {
        final Properties properties = new Properties();
        if (System.getProperty("endpoint") == null || System.getProperty("accessKey") == null) {
            throw new Exception(
                    "Make sure to supply azure CosmosDB endpoint and accessKey, e.g:  mvn verify -Dendpoint=myacc-azure.com -DaccessKey=mykey");
        }
        properties.setProperty("endpoint", System.getProperty("endpoint"));
        properties.setProperty("access_key", System.getProperty("accessKey"));

        return properties;
    }

    public static CosmosAsyncClient createAsyncClient() throws Exception {
        final Properties properties = loadAzureAccessFromJvmEnv();

        return new CosmosClientBuilder()
                .key(properties.getProperty("access_key"))
                .endpoint(properties.getProperty("endpoint"))
                .contentResponseOnWriteEnabled(true)
                .buildAsyncClient();
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
