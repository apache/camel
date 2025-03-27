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
package org.apache.camel.tracing.decorators;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.tracing.MockSpanAdapter;
import org.apache.camel.tracing.TagConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AzureStorageDataLakeSpanDecoratorTest {

    @Test
    public void testGetOperationNameFromHeader() {
        String operation = "upload";
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.OPERATION, String.class)).thenReturn(operation);

        AbstractSpanDecorator decorator = new AzureStorageDataLakeSpanDecorator();

        assertEquals(operation, decorator.getOperationName(exchange, null));
    }

    @Test
    public void testGetOperationNameFromHeaderWithEnum() {
        operationEnum operation = operationEnum.upload;

        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.OPERATION, String.class))
                .thenReturn(operation.toString());

        AbstractSpanDecorator decorator = new AzureStorageDataLakeSpanDecorator();

        assertEquals(operation.toString(), decorator.getOperationName(exchange, null));
    }

    @Test
    public void testGetOperationNameFromQueryParameter() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("azure-storage-datalake:myAccount/myFileSystem?operation=upload");
        Mockito.when(exchange.getIn()).thenReturn(message);

        AbstractSpanDecorator decorator = new AzureStorageDataLakeSpanDecorator();

        assertEquals("upload", decorator.getOperationName(exchange, endpoint));
    }

    @Test
    public void testPre() {
        String fileSystemName = "myFileSystem";
        String directoryName = "myDirectory";
        String fileName = "myFile";
        String path = "myPath";
        String expression = "myExpression";
        String contentType = "myContentType";
        Duration timeout = Duration.ofDays(7);
        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
        OffsetDateTime lastModified = OffsetDateTime.now();
        Long position = 21L;

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("azure-storage-datalake:account/myFileSystem");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.FILESYSTEM_NAME, String.class))
                .thenReturn(fileSystemName);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.DIRECTORY_NAME, String.class))
                .thenReturn(directoryName);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.FILE_NAME, String.class)).thenReturn(fileName);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.PATH, String.class)).thenReturn(path);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.EXPRESSION, String.class)).thenReturn(expression);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.CONTENT_TYPE, String.class)).thenReturn(contentType);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.TIMEOUT, Duration.class)).thenReturn(timeout);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.METADATA, Map.class))
                .thenReturn(metadata);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.LAST_MODIFIED, OffsetDateTime.class))
                .thenReturn(lastModified);
        Mockito.when(message.getHeader(AzureStorageDataLakeSpanDecorator.POSITION, Long.class)).thenReturn(position);

        AbstractSpanDecorator decorator = new AzureStorageDataLakeSpanDecorator();

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, exchange, endpoint);

        assertEquals("azure-storage-datalake", span.tags().get(TagConstants.DB_SYSTEM));
        assertEquals(fileSystemName, span.tags().get(TagConstants.DB_NAME));
        assertEquals(directoryName, span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_DIRECTORY_NAME));
        assertEquals(fileName, span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_FILE_NAME));
        assertEquals(path, span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_PATH));
        assertEquals(expression, span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_EXPRESSION));
        assertEquals(contentType, span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_CONTENT_TYPE));
        assertEquals(timeout.toString(), span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_TIMEOUT));
        assertEquals(metadata.toString(), span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_METADATA));
        assertEquals(lastModified.toString(),
                span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_LAST_MODIFIED));
        assertEquals(position, span.tags().get(AzureStorageDataLakeSpanDecorator.STORAGE_DATALAKE_POSITION));
    }

    enum operationEnum {
        upload
    }

}
