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
package org.apache.camel.component.azure.eventhubs;

import java.io.IOException;
import java.util.Properties;

import org.apache.camel.test.infra.azure.eventhubs.common.AzureEventHubsProperties;
import org.apache.camel.test.infra.azure.eventhubs.services.AzureEventHubsService;
import org.apache.camel.test.infra.azure.eventhubs.services.AzureEventHubsServiceFactory;
import org.apache.camel.test.junit6.TestSupport;

public final class TestUtils {

    public static final String CONNECTION_STRING = "connectionString";
    public static final String BLOB_ACCOUNT_NAME = "blobAccountName";
    public static final String BLOB_ACCESS_KEY = "blobAccessKey";

    private TestUtils() {
    }

    public static Properties loadAzurePropertiesFile() throws IOException {
        return TestSupport.loadExternalProperties(TestUtils.class, "azure_key.properties");
    }

    /**
     * The service of the integration tests: an event hub on a local Floci AZ container, unless the connectionString,
     * blobAccountName and blobAccessKey system properties are set: then the event hub of that connection string, with
     * the checkpoints in that storage account.
     */
    public static AzureEventHubsService createService() {
        String connectionString = System.getProperty(CONNECTION_STRING);
        if (connectionString != null) {
            if (System.getProperty(BLOB_ACCOUNT_NAME) == null || System.getProperty(BLOB_ACCESS_KEY) == null) {
                throw new IllegalArgumentException(
                        "Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -DconnectionString=string"
                                                   + " -DblobAccountName=blob -DblobAccessKey=key");
            }
            System.setProperty(AzureEventHubsProperties.CONNECTION_STRING, connectionString);
            System.setProperty(AzureEventHubsProperties.BLOB_ACCOUNT_NAME, System.getProperty(BLOB_ACCOUNT_NAME));
            System.setProperty(AzureEventHubsProperties.BLOB_ACCESS_KEY, System.getProperty(BLOB_ACCESS_KEY));
            System.setProperty("azure.instance.type", "remote");
        }

        return AzureEventHubsServiceFactory.createSingletonService();
    }
}
