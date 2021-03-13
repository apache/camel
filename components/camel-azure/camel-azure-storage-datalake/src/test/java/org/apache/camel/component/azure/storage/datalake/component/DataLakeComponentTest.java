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
package org.apache.camel.component.azure.storage.datalake.component;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeEndpoint;
import org.apache.camel.component.azure.storage.datalake.DataLakeOperationsDefinition;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeClientFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataLakeComponentTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DataLakeComponentTest.class);

    @Test
    public void testWithServiceClient() {
        final DataLakeConfiguration configuration = new DataLakeConfiguration();
        configuration.setSharedKeyCredential(storageSharedKeyCredentials());
        final DataLakeServiceClient serviceClient = DataLakeClientFactory.createDataLakeServiceClient(configuration);
        context.getRegistry().bind("azureDataLakeClient", serviceClient);
        final DataLakeEndpoint endpoint = (DataLakeEndpoint) context
                .getEndpoint(
                        "azure-storage-datalake:cameltesting/abc?serviceClient=#azureDataLakeClient&operation=listPaths");
        assertEquals("cameltesting", endpoint.getConfiguration().getAccountName());
        assertEquals("abc", endpoint.getConfiguration().getFileSystemName());
        assertNotNull(endpoint.getConfiguration().getServiceClient());
        assertEquals(DataLakeOperationsDefinition.listPaths, endpoint.getConfiguration().getOperation());
        assertNull(endpoint.getConfiguration().getDirectoryName());
    }

    @Test
    public void testWithSharedKeyCredentials() {
        context.getRegistry().bind("credentials", storageSharedKeyCredentials());

        final DataLakeEndpoint endpoint = (DataLakeEndpoint) context
                .getEndpoint(
                        "azure-storage-datalake:cameltesting/abc?sharedKeyCredential=#credentials&operation=upload&fileName=test.txt");

        assertEquals("cameltesting", endpoint.getConfiguration().getAccountName());
        assertEquals("abc", endpoint.getConfiguration().getFileSystemName());
        assertNotNull(endpoint.getConfiguration().getSharedKeyCredential());
        assertNull(endpoint.getConfiguration().getServiceClient());
        assertEquals(DataLakeOperationsDefinition.upload, endpoint.getConfiguration().getOperation());
        assertEquals("test.txt", endpoint.getConfiguration().getFileName());
    }

    @Test
    public void testProducerWithoutFileName() {
        context.getRegistry().bind("credentials", storageSharedKeyCredentials());
        final DataLakeEndpoint endpoint = (DataLakeEndpoint) context
                .getEndpoint("azure-storage-datalake:cameltesting/abc?sharedKeyCredential=#credentials&operation=deleteFile");

        DefaultExchange exchange = new DefaultExchange(context);
        assertThrows(IllegalArgumentException.class, () -> endpoint.createProducer().process(exchange));
    }

    private StorageSharedKeyCredential storageSharedKeyCredentials() {
        final String accountKey = "";
        return new StorageSharedKeyCredential("cameltesting", accountKey);
    }
}
