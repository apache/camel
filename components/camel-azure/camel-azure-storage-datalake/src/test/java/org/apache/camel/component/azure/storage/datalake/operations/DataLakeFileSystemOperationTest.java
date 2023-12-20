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

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.file.datalake.models.PathItem;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileSystemClientWrapper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class DataLakeFileSystemOperationTest extends CamelTestSupport {
    private DataLakeConfiguration configuration;

    @Mock
    private DataLakeFileSystemClientWrapper client;

    @BeforeEach
    public void setup() {
        configuration = new DataLakeConfiguration();
        configuration.setAccountName("cameltesting");
        configuration.setFileSystemName("test");
    }

    @Test
    void testCreateFileSystem() {
        when(client.createFileSystem(any(), any(), any()))
                .thenReturn(createFileSystemHeaderMock());

        final DataLakeFileSystemOperations fileSystemOperations = new DataLakeFileSystemOperations(configuration, client);
        final DataLakeOperationResponse response = fileSystemOperations.createFileSystem(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(DataLakeConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) response.getBody());
    }

    @Test
    void testDeleteFileSystem() {
        when(client.deleteFileSystem(any(), any()))
                .thenReturn(deleteFileSystemHeaderMock());

        final DataLakeFileSystemOperations operations = new DataLakeFileSystemOperations(configuration, client);
        final DataLakeOperationResponse response = operations.deleteFileSystem(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(DataLakeConstants.RAW_HTTP_HEADERS));
        assertTrue((boolean) response.getBody());
    }

    @Test
    void testListPaths() {
        when(client.listPaths(any(), any()))
                .thenReturn(listPathsMock());

        final DataLakeFileSystemOperations operations = new DataLakeFileSystemOperations(configuration, client);
        final DataLakeOperationResponse response = operations.listPaths(null);

        assertNotNull(response);

        @SuppressWarnings("unchecked")
        final List<PathItem> body = (List<PathItem>) response.getBody();
        final List<String> pathNames = body.stream().map(PathItem::getName).toList();
        assertTrue(pathNames.contains("item1"));
        assertTrue(pathNames.contains("item2"));

        final List<PathItem> directories = body.stream().filter(PathItem::isDirectory).toList();
        final List<PathItem> files = body.stream().filter(pathItem -> !pathItem.isDirectory()).toList();
        assertEquals(1, directories.size());
        assertEquals(1, files.size());
    }

    @Test
    void testListPathWithRegex() {
        configuration.setRegex(".*\\.pdf");

        when(client.listPaths(any(), any()))
                .thenReturn(listPathsForRegexMock());

        final DataLakeFileSystemOperations operations = new DataLakeFileSystemOperations(configuration, client);
        final DataLakeOperationResponse response = operations.listPaths(null);

        assertNotNull(response);

        @SuppressWarnings("unchecked")
        final List<PathItem> body = (List<PathItem>) response.getBody();
        final List<String> items = body.stream().map(PathItem::getName).collect(Collectors.toList());

        assertEquals(3, items.size());
        assertTrue(items.contains("file1.pdf"));
        assertTrue(items.contains("file2.pdf"));
        assertTrue(items.contains("file5.pdf"));
    }

    private List<PathItem> listPathsMock() {
        final List<PathItem> paths = new LinkedList<>();

        final PathItem item1 = new PathItem("testTag1", OffsetDateTime.now(), 0, null, false, "item1", null, null);
        final PathItem item2 = new PathItem("testTag1", OffsetDateTime.now(), 0, null, true, "item2", null, null);

        paths.add(item1);
        paths.add(item2);

        return paths;
    }

    private List<PathItem> listPathsForRegexMock() {
        final List<PathItem> paths = listPathsMock();

        final PathItem itemPdf1 = new PathItem("testTag1", OffsetDateTime.now(), 0, null, false, "file1.pdf", null, null);
        final PathItem itemPdf2 = new PathItem("testTag2", OffsetDateTime.now(), 0, null, false, "file2.pdf", null, null);
        final PathItem itemPdf3 = new PathItem("testTag3", OffsetDateTime.now(), 0, null, false, "file5.pdf", null, null);
        final PathItem itemExe1 = new PathItem("testTag4", OffsetDateTime.now(), 0, null, false, "a.exe", null, null);
        final PathItem itemExe2 = new PathItem("testTag5", OffsetDateTime.now(), 0, null, false, "b.exe", null, null);
        final PathItem itemExe3 = new PathItem("testTag6", OffsetDateTime.now(), 0, null, false, "c.exe", null, null);

        paths.add(itemPdf1);
        paths.add(itemPdf2);
        paths.add(itemPdf3);
        paths.add(itemExe1);
        paths.add(itemExe2);
        paths.add(itemExe3);

        return paths;

    }

    private HttpHeaders deleteFileSystemHeaderMock() {
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("x-ms-request-id", "12345");
        return httpHeaders;
    }

    private HttpHeaders createFileSystemHeaderMock() {
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("x-ms-request-id", "12345");
        return httpHeaders;
    }
}
