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
package org.apache.camel.component.azure.storage.datalake;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.models.DataLakeFileOpenInputStreamResult;
import com.azure.storage.file.datalake.models.DataLakeStorageException;
import com.azure.storage.file.datalake.models.PathItem;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With deleteAfterRead, the consumer deletes each file once its exchange has completed, and keeps it when the exchange
 * fails.
 */
class DataLakeConsumerDeleteAfterReadTest extends CamelTestSupport {

    private final Set<String> files = new ConcurrentSkipListSet<>();
    private final List<String> deleteRequests = new CopyOnWriteArrayList<>();
    private final Set<String> deleteFails = ConcurrentHashMap.newKeySet();
    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("client", serviceClient());
        context.getRegistry().bind("recordErrors", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(String.valueOf(exception.getMessage()));
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(message);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(message);
            }
        });
        return context;
    }

    @Test
    void filesAreDeletedAfterTheyAreConsumed() throws Exception {
        files.add("a.txt");
        files.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a.txt", "b.txt");

        addConsumerRoute("&deleteAfterRead=true", false);

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(files::isEmpty);
        assertTrue(deleteRequests.containsAll(List.of("a.txt", "b.txt")), "deleted: " + deleteRequests);
        // each file is consumed once, as it is gone at the next poll
        await().during(200, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .until(() -> mock.getReceivedCounter() == 2);
    }

    @Test
    void theFileOfTheExchangeIsDeletedWhenTheRouteChangesTheHeader() throws Exception {
        files.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-datalake:account/fs?serviceClient=#client&initialDelay=0&delay=10&deleteAfterRead=true")
                        .setHeader(DataLakeConstants.FILE_NAME, constant("other.txt"))
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(files::isEmpty);
        assertEquals("a.txt", deleteRequests.get(0));
    }

    @Test
    void aFailedExchangeKeepsTheFile() throws Exception {
        files.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        // read again at the next polls, as it is not deleted
        mock.expectedMinimumMessageCount(3);

        addConsumerRoute("&deleteAfterRead=true&exceptionHandler=#recordErrors", true);

        mock.assertIsSatisfied();
        assertTrue(deleteRequests.isEmpty(), "nothing is deleted: " + deleteRequests);
        assertTrue(files.contains("a.txt"));
    }

    @Test
    void withoutTheOptionNothingIsDeleted() throws Exception {
        files.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        addConsumerRoute("", false);

        mock.assertIsSatisfied();
        assertTrue(deleteRequests.isEmpty(), "nothing is deleted: " + deleteRequests);
    }

    @Test
    void aFailedDeleteGoesToTheExceptionHandler() throws Exception {
        files.add("a.txt");
        deleteFails.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        addConsumerRoute("&deleteAfterRead=true&exceptionHandler=#recordErrors", false);

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> handled.stream().anyMatch(m -> m.contains("Error deleting file a.txt from file system fs")));
    }

    private void addConsumerRoute(String options, boolean fail) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-datalake:account/fs?serviceClient=#client&initialDelay=0&delay=10" + options)
                        .convertBodyTo(String.class)
                        .to("mock:result")
                        .process(exchange -> {
                            if (fail) {
                                throw new IllegalStateException("Forced");
                            }
                        });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private DataLakeServiceClient serviceClient() {
        DataLakeServiceClient serviceClient = mock(DataLakeServiceClient.class);
        DataLakeFileSystemClient fileSystemClient = mock(DataLakeFileSystemClient.class);
        when(serviceClient.getFileSystemClient(anyString())).thenReturn(fileSystemClient);

        PagedIterable<PathItem> listing = mock(PagedIterable.class);
        when(listing.stream()).thenAnswer(i -> files.stream()
                .map(name -> new PathItem(null, null, 0L, null, false, name, null, null))
                .toList().stream());
        when(fileSystemClient.listPaths(any(), any())).thenReturn(listing);

        when(fileSystemClient.getFileClient(anyString())).thenAnswer(i -> fileClient(i.getArgument(0)));
        return serviceClient;
    }

    @SuppressWarnings("unchecked")
    private DataLakeFileClient fileClient(String name) {
        DataLakeFileClient fileClient = mock(DataLakeFileClient.class);
        when(fileClient.openInputStream()).thenAnswer(i -> {
            DataLakeFileOpenInputStreamResult result = mock(DataLakeFileOpenInputStreamResult.class);
            when(result.getInputStream()).thenReturn(new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
            return result;
        });
        when(fileClient.deleteWithResponse(any(), any(), any())).thenAnswer(i -> {
            deleteRequests.add(name);
            if (deleteFails.contains(name)) {
                HttpResponse response = mock(HttpResponse.class);
                when(response.getStatusCode()).thenReturn(500);
                throw new DataLakeStorageException("Internal error", response, null);
            }
            files.remove(name);
            return mock(Response.class);
        });
        return fileClient;
    }
}
