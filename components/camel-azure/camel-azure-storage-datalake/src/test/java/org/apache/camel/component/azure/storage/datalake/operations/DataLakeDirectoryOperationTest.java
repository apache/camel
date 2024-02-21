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
package org.apache.camel.component.azure.storage.datalake.operations;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.ResponseBase;
import com.azure.storage.file.datalake.DataLakeFileClient;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeDirectoryClientWrapper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class DataLakeDirectoryOperationTest extends CamelTestSupport {
    private DataLakeConfiguration configuration;

    @Mock
    private DataLakeDirectoryClientWrapper client;

    @Mock
    private DataLakeFileClient fileClient;

    @BeforeEach
    public void setup() {
        configuration = new DataLakeConfiguration();
        configuration.setAccountName("cameltesting");
    }

    @Test
    void testCreateFile() {

        final HttpHeaders httpHeaders = new HttpHeaders();
        when(client.createFileWithResponse(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ResponseBase<>(null, 200, httpHeaders, fileClient, null));

        final DataLakeDirectoryOperations operations = new DataLakeDirectoryOperations(configuration, client);
        final DataLakeOperationResponse response = operations.createFile(null);

        assertNotNull(response);
    }

    @Test
    void testDeleteDirectory() {
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("x-ms-request-id", "12345");
        when(client.deleteWithResponse(any(), any(), any()))
                .thenReturn(new ResponseBase<>(null, 200, httpHeaders, null, null));

        final DataLakeDirectoryOperations operations = new DataLakeDirectoryOperations(configuration, client);
        final DataLakeOperationResponse response = operations.deleteDirectory(null);

        assertNotNull(response);
        assertEquals(true, response.getBody());
    }

}
