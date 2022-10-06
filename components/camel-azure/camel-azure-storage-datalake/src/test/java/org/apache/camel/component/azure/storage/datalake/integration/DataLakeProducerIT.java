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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.azure.storage.file.datalake.models.FileSystemItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.azure.storage.datalake.DataLakeOperationsDefinition;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "azure.instance.type", matches = "remote")
public class DataLakeProducerIT extends Base {

    private String fileName;
    private byte[] fileContent;

    @BeforeAll
    public void setup() {
        final String randomSuffix = RandomStringUtils.randomAlphabetic(5).toLowerCase(Locale.ROOT);
        fileName = "file" + randomSuffix + ".txt";
        fileContent = ("Hello " + randomSuffix).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void testConsumer() {

        {
            @SuppressWarnings("unchecked")
            List<FileSystemItem> filesystems = template.requestBody(
                    componentUri(fileSystemName, DataLakeOperationsDefinition.listFileSystem),
                    null,
                    List.class);

            Assertions.assertThat(filesystems.stream().map(FileSystemItem::getName)).isNotEmpty();
            Assertions.assertThat(filesystems.stream().map(FileSystemItem::getName)).doesNotContain(fileSystemName);
        }

        template.sendBody(
                componentUri(fileSystemName, DataLakeOperationsDefinition.createFileSystem),
                null);

        {
            @SuppressWarnings("unchecked")
            List<FileSystemItem> filesystems = template.requestBody(
                    componentUri(fileSystemName, DataLakeOperationsDefinition.listFileSystem),
                    null,
                    List.class);

            Assertions.assertThat(filesystems.stream().map(FileSystemItem::getName)).contains(fileSystemName);
        }

        try {
            template.sendBodyAndHeader(
                    componentUri(fileSystemName, DataLakeOperationsDefinition.upload),
                    fileContent,
                    DataLakeConstants.FILE_NAME,
                    fileName);

            byte[] actual = template.requestBodyAndHeader(
                    componentUri(fileSystemName, DataLakeOperationsDefinition.getFile),
                    null,
                    DataLakeConstants.FILE_NAME,
                    fileName,
                    byte[].class);

            Assertions.assertThat(actual).containsExactly(fileContent);
        } finally {
            /* Cleanup */
            template.sendBody(
                    componentUri(fileSystemName, DataLakeOperationsDefinition.deleteFileSystem),
                    null);

            @SuppressWarnings("unchecked")
            List<FileSystemItem> filesystems = template.requestBody(
                    componentUri(fileSystemName, DataLakeOperationsDefinition.listFileSystem),
                    null,
                    List.class);

            Assertions.assertThat(filesystems.stream().map(FileSystemItem::getName)).isNotEmpty();
            Assertions.assertThat(filesystems.stream().map(FileSystemItem::getName)).doesNotContain(fileSystemName);
        }

    }

    @Test
    void testHeaderPreservation() {
        Exchange result = template.send(componentUri(fileSystemName, DataLakeOperationsDefinition.listFileSystem),
                exchange -> {
                    exchange.getIn().setHeader("DoNotDelete", "keep me");
                });
        assertEquals("keep me", result.getMessage().getHeader("DoNotDelete"));
    }

    private String componentUri(final String filesystem, final DataLakeOperationsDefinition operation) {
        return String.format("azure-storage-datalake://%s%s?operation=%s",
                service.azureCredentials().accountName(),
                filesystem == null ? "" : ("/" + filesystem),
                operation.name());
    }

}
