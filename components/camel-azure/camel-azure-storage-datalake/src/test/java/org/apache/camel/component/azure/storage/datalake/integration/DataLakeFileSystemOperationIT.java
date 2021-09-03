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
package org.apache.camel.component.azure.storage.datalake.integration;

import java.util.concurrent.TimeUnit;

import com.azure.storage.file.datalake.models.DataLakeStorageException;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileSystemClientWrapper;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeServiceClientWrapper;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeFileSystemOperations;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeOperationResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "azure.instance.type", matches = "remote")
public class DataLakeFileSystemOperationIT extends Base {

    private DataLakeServiceClientWrapper serviceClientWrapper;

    @BeforeAll
    public void setup() {
        serviceClientWrapper = new DataLakeServiceClientWrapper(serviceClient);
    }

    @Test
    void testCreateAndDeleteFileSystem() {
        final DataLakeFileSystemClientWrapper fileSystemClientWrapper
                = serviceClientWrapper.getDataLakeFileSystemClientWrapper("testcontainer");

        final DataLakeFileSystemOperations operations
                = new DataLakeFileSystemOperations(configuration, fileSystemClientWrapper);

        final DataLakeOperationResponse responseCreate = operations.createFileSystem(null);

        assertNotNull(responseCreate);
        assertNotNull(responseCreate.getHeaders().get(DataLakeConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) responseCreate.getBody());

        final DataLakeOperationResponse responseDelete = operations.deleteFileSystem(null);

        assertNotNull(responseDelete);
        assertNotNull(responseDelete.getHeaders().get(DataLakeConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) responseDelete.getBody());

        Awaitility.given().ignoreException(DataLakeStorageException.class).with()
                .pollInterval(100, TimeUnit.MILLISECONDS).atMost(60, TimeUnit.SECONDS)
                .until(() -> {
                    final DataLakeOperationResponse responseRecreate = operations.createFileSystem(null);
                    assertNotNull(responseRecreate);
                    assertNotNull(responseRecreate.getHeaders().get(DataLakeConstants.RAW_HTTP_HEADERS));
                    assertTrue((boolean) responseRecreate.getBody());

                    return (boolean) responseRecreate.getBody();
                });
    }

    @AfterAll
    public void delete() {
        serviceClientWrapper.getDataLakeFileSystemClientWrapper("testcontainer").deleteFileSystem(null, null);
    }
}
