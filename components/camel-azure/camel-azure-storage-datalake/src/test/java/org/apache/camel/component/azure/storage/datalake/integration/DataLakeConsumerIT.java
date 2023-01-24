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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "azure.instance.type", matches = "remote")
public class DataLakeConsumerIT extends Base {

    @TempDir
    static Path testDir;

    @EndpointInject("direct:createFile")
    private ProducerTemplate templateStart;
    private String batchFileSystemName;
    private String batchFileSystemName1;
    private String batchFileSystemName2;
    private String fileName;
    private final String baseURI = String.format("azure-storage-datalake:%s/", service.azureCredentials().accountName());
    private final String regex = ".*\\.pdf";

    private DataLakeFileSystemClient fileSystemClient;
    private DataLakeFileSystemClient batchFileSystemClient;
    private DataLakeFileSystemClient batchFileSystemClient1;
    private DataLakeFileSystemClient batchFileSystemClient2;

    @BeforeAll
    public void setup() {
        batchFileSystemName = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);
        batchFileSystemName1 = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);
        batchFileSystemName2 = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);
        fileName = RandomStringUtils.randomAlphabetic(5);
        fileSystemClient = serviceClient.getFileSystemClient(fileSystemName);
        batchFileSystemClient = serviceClient.getFileSystemClient(batchFileSystemName);
        batchFileSystemClient1 = serviceClient.getFileSystemClient(batchFileSystemName1);
        batchFileSystemClient2 = serviceClient.getFileSystemClient(batchFileSystemName2);

        configuration.setFileSystemName(batchFileSystemName);

        fileSystemClient.create();
        batchFileSystemClient.create();
        batchFileSystemClient1.create();
        batchFileSystemClient2.create();

        try {
            fileSystemClient.getFileClient(fileName).upload(new ByteArrayInputStream("file data".getBytes()), 9L);
            batchFileSystemClient.getFileClient("batch_file_1").upload(new ByteArrayInputStream("Batch file 1".getBytes()),
                    12L);
            batchFileSystemClient.getFileClient("batch_file_2").upload(new ByteArrayInputStream("Batch file 2".getBytes()),
                    12L);
            batchFileSystemClient1.getFileClient("batch_file_A").upload(new ByteArrayInputStream("Batch file A".getBytes()),
                    12L);
            batchFileSystemClient1.getFileClient("batch_file_B").upload(new ByteArrayInputStream("Batch file B".getBytes()),
                    12L);
            for (int i = 0; i < 3; i++) {
                final int index = i;
                batchFileSystemClient2.getFileClient(generateRandomFileName("pdf")).upload(
                        new ByteArrayInputStream(("PDF with regex :" + Integer.toString(index)).getBytes()),
                        17L);
            }

            for (int i = 0; i < 3; i++) {
                final int index = i;
                batchFileSystemClient2.getFileClient(generateRandomFileName("docx")).upload(
                        new ByteArrayInputStream(("DOCX with regex :" + Integer.toString(index)).getBytes()),
                        18L);
            }

        } catch (UncheckedIOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testPollingToFile() throws Exception {
        Files.deleteIfExists(new File(testDir + "/" + fileName).toPath());

        final MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);
        mockEndpoint.assertIsSatisfied();

        final File file = mockEndpoint.getExchanges().get(0).getIn().getBody(File.class);
        assertNotNull(file, "File must be set");
        assertEquals("file data", FileUtils.readFileToString(file, Charset.defaultCharset()));
    }

    @Test
    void testBatchPolling() throws Exception {
        final MockEndpoint mockEndpointForStreams = getMockEndpoint("mock:resultBatch");

        mockEndpointForStreams.expectedMessageCount(2);
        mockEndpointForStreams.assertIsSatisfied();

        final InputStream fileStream1
                = mockEndpointForStreams.getExchanges().get(0).getIn().getBody(InputStream.class);
        final InputStream fileStream2
                = mockEndpointForStreams.getExchanges().get(1).getIn().getBody(InputStream.class);

        final String text1 = context().getTypeConverter().convertTo(String.class, fileStream1).trim();
        final String text2 = context().getTypeConverter().convertTo(String.class, fileStream2).trim();

        final List<String> expectedList = Arrays.asList(text1, text2);
        final List<String> actualList = Arrays.asList("Batch file 1", "Batch file 2");
        assertTrue(expectedList.size() == actualList.size()
                && expectedList.containsAll(actualList) && actualList.containsAll(expectedList));
    }

    @Test
    void testBatchPollingFile() throws Exception {
        final MockEndpoint mockEndpointForFiles = getMockEndpoint("mock:resultBatchFile");
        mockEndpointForFiles.expectedMessageCount(2);
        mockEndpointForFiles.assertIsSatisfied();

        final File file1 = new File(testDir + "/batch_file_A");
        final File file2 = new File(testDir + "/batch_file_B");

        assertTrue(file1.exists(), "File was not created in local filesystem: batch_file_A");
        assertTrue(file2.exists(), "File was not created in local filesystem: batch_file_B");

        assertEquals("Batch file A", context().getTypeConverter().convertTo(String.class, file1).trim());
        assertEquals("Batch file B", context().getTypeConverter().convertTo(String.class, file2).trim());

    }

    @Test
    void testPollingWithRegex() throws Exception {
        final MockEndpoint endpoint = getMockEndpoint("mock:resultRegex");
        endpoint.expectedMessageCount(3);

        endpoint.assertIsSatisfied();

        Pattern pattern = Pattern.compile(regex);

        for (Exchange exchange : endpoint.getExchanges()) {
            String fileName = exchange.getIn().getHeader(DataLakeConstants.FILE_NAME, String.class);
            assertTrue(pattern.matcher(fileName).matches());
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {

                context.getRegistry().bind("openOptions", configuration.getOpenOptions());

                from(baseURI + fileSystemName + "?fileName=" + fileName
                     + "&dataLakeServiceClient=#serviceClient&fileDir=" + testDir + "&openOptions=#openOptions")
                        .to("mock:result");

                from(baseURI + batchFileSystemName + "?dataLakeServiceClient=#serviceClient&openOptions=#openOptions")
                        .to("mock:resultBatch");

                from(baseURI + batchFileSystemName1 + "?dataLakeServiceClient=#serviceClient&fileDir="
                     + testDir + "&openOptions=#openOptions").to("mock:resultBatchFile");

                from(baseURI + batchFileSystemName2 + "?dataLakeServiceClient=#serviceClient&regex=" + regex
                     + "&openOptions=#openOptions")
                        .idempotentConsumer(body(), new MemoryIdempotentRepository())
                        .to("mock:resultRegex");
            }
        };
    }

    @AfterAll
    public void delete() {
        fileSystemClient.delete();
        batchFileSystemClient.delete();
        batchFileSystemClient1.delete();
        batchFileSystemClient2.delete();
    }

    private String generateRandomFileName(String extension) {
        return RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT) + "." + extension;
    }

}
