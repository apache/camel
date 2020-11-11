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
package org.apache.camel.component.azure.storage.blob.integration;

import java.util.Properties;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.CamelContext;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobTestUtils;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BaseIT extends CamelTestSupport {
    private static final String ACCESS_KEY = "accessKey";
    private static final String ACCOUNT_NAME = "accountName";
    private static final String AZURITE_IMAGE_NAME = "mcr.microsoft.com/azure-storage/azurite:3.9.0";
    private static final int AZURITE_EXPOSED_PORT = 10000;
    private static String accountName;
    private static String accessKey;
    private static String endpoint;

    protected BlobServiceClient serviceClient;
    protected BlobConfiguration configuration;
    protected String containerName;

    static {
        // Start testcontainers as a singleton if needed
        initEndpoint();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("serviceClient", serviceClient);
        return context;
    }

    static void initEndpoint() {
        accountName = System.getProperty(ACCOUNT_NAME);
        accessKey = System.getProperty(ACCESS_KEY);
        endpoint = String.format("https://%s.blob.core.windows.net", accountName);

        // If everything is set, do not start Azurite
        if (StringUtils.isNotEmpty(accountName) && StringUtils.isNotEmpty(accessKey)) {
            return;
        }

        if ((StringUtils.isEmpty(accountName) && StringUtils.isNotEmpty(accessKey)) ||
                (StringUtils.isNotEmpty(accountName) && StringUtils.isEmpty(accessKey))) {
            throw new IllegalArgumentException(
                    "Make sure to supply both azure accessKey and accountName," +
                                               " e.g:  mvn verify -DaccountName=myacc -DaccessKey=mykey");
        }

        final GenericContainer<?> azurite = new GenericContainer<>(AZURITE_IMAGE_NAME)
                .withExposedPorts(AZURITE_EXPOSED_PORT);
        azurite.start();
        Properties azuriteProperties = BlobTestUtils.getAzuriteProperties();
        accountName = azuriteProperties.getProperty(ACCOUNT_NAME);
        accessKey = azuriteProperties.getProperty(ACCESS_KEY);
        endpoint = String.format("http://%s:%d/%s", azurite.getContainerIpAddress(),
                azurite.getMappedPort(AZURITE_EXPOSED_PORT), accountName);
    }

    @BeforeAll
    void initProperties() {
        containerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        configuration = new BlobConfiguration();
        configuration.setCredentials(new StorageSharedKeyCredential(accountName, accessKey));
        configuration.setContainerName(containerName);

        serviceClient = new BlobServiceClientBuilder()
                .credential(configuration.getCredentials())
                .endpoint(endpoint)
                .buildClient();
    }
}
