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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;

import com.azure.storage.file.datalake.options.FileParallelUploadOptions;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileClientWrapper;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileSystemClientWrapper;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeServiceClientWrapper;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeFileOperations;
import org.apache.camel.component.azure.storage.datalake.operations.DataLakeOperationResponse;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "azure.instance.type", matches = "remote")
public class DataLakeFileOperationIT extends Base {
    private DataLakeFileSystemClientWrapper fileSystemClientWrapper;
    private String randomFileName;

    @BeforeAll
    public void setup() throws Exception {
        randomFileName = RandomStringUtils.randomAlphabetic(10);

        fileSystemClientWrapper = new DataLakeServiceClientWrapper(serviceClient)
                .getDataLakeFileSystemClientWrapper(configuration.getFileSystemName());
        fileSystemClientWrapper.createFileSystem(null, null, null);

        final InputStream inputStream = new ByteArrayInputStream("testing".getBytes(Charset.defaultCharset()));
        final FileParallelUploadOptions options
                = new FileParallelUploadOptions(inputStream);
        fileSystemClientWrapper.getDataLakeFileClientWrapper(randomFileName).uploadWithResponse(options, null);
    }

    @AfterAll
    public void delete() {
        fileSystemClientWrapper.deleteFileSystem(null, null);
    }

    @Test
    void testGetFile(@TempDir Path testDir) throws Exception {
        final DataLakeFileClientWrapper fileClientWrapper
                = fileSystemClientWrapper.getDataLakeFileClientWrapper(randomFileName);
        final DataLakeFileOperations operations = new DataLakeFileOperations(configuration, fileClientWrapper);

        final Exchange exchange = new DefaultExchange(context);
        final DataLakeOperationResponse response = operations.getFile(exchange);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());

        final InputStream inputStream = (InputStream) response.getBody();
        final String bufferedText = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset())).readLine();

        assertEquals("testing", bufferedText);

        final File testFile = new File(testDir.toFile(), "test_file.txt");
        exchange.getIn().setBody(new FileOutputStream(testFile));

        final DataLakeOperationResponse responseWithFile = operations.getFile(exchange);
        final String fileContent = FileUtils.readFileToString(testFile, Charset.defaultCharset());

        assertNotNull(responseWithFile);
        assertNotNull(responseWithFile.getHeaders());
        assertNotNull(responseWithFile.getBody());
        assertTrue(fileContent.contains("testing"));
    }

    @Test
    void testDownloadToFile(@TempDir Path testDir) throws IOException {
        final DataLakeFileClientWrapper fileClientWrapper
                = fileSystemClientWrapper.getDataLakeFileClientWrapper(randomFileName);
        final DataLakeFileOperations operations = new DataLakeFileOperations(configuration, fileClientWrapper);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DataLakeConstants.FILE_DIR, testDir.toString());
        exchange.getIn().setHeader(DataLakeConstants.FILE_NAME, randomFileName);

        final DataLakeOperationResponse response = operations.downloadToFile(exchange);

        final File testFile = testDir.resolve(randomFileName).toFile();
        final String fileContent = FileUtils.readFileToString(testFile, Charset.defaultCharset());

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getHeaders().get(DataLakeConstants.FILE_NAME));
        assertTrue(fileContent.contains("testing"));
    }

    @Test
    void testDownloadLink() {
        final DataLakeFileClientWrapper clientWrapper = fileSystemClientWrapper.getDataLakeFileClientWrapper(randomFileName);
        final DataLakeFileOperations fileOperations = new DataLakeFileOperations(configuration, clientWrapper);

        final DataLakeOperationResponse response = fileOperations.downloadLink(null);
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders().get(DataLakeConstants.DOWNLOAD_LINK));
    }
}
