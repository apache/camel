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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.models.DataLakeFileOpenInputStreamResult;
import com.azure.storage.file.datalake.models.DataLakeStorageException;
import com.azure.storage.file.datalake.models.PathItem;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A listed file that cannot be got (access denied by its ACL, a file encrypted with a key the consumer does not have)
 * must not stop the consumer: the other listed files are consumed, and the poll fails only when none of the listed
 * files could be got. The consumer lists the whole file system at every poll, so before this one such file stopped the
 * consumer from consuming any file.
 */
class DataLakeConsumerFileThatCannotBeGotTest extends CamelTestSupport {

    private final Set<String> files = new ConcurrentSkipListSet<>();
    private final Set<String> forbidden = ConcurrentHashMap.newKeySet();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();
    private final CountDownLatch failedPolls = new CountDownLatch(2);
    private final CountDownLatch skippedReported = new CountDownLatch(1);
    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("client", serviceClient());
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // noop
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPolls.countDown();
                return false;
            }
        });
        context.getRegistry().bind("recordErrors", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(String.valueOf(exception.getMessage()));
            }

            @Override
            public void handleException(String message, Throwable exception) {
                record(message);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                record(message);
            }

            private void record(String message) {
                handled.add(message);
                if (message.contains("b.txt")) {
                    skippedReported.countDown();
                }
            }
        });
        return context;
    }

    @Test
    void fileThatCannotBeGotDoesNotBlockTheOtherFiles() throws Exception {
        files.add("a.txt");
        files.add("b.txt");
        files.add("c.txt");
        forbidden.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        addConsumerRoute("&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        List<String> bodies = mock.getReceivedExchanges().stream().map(e -> e.getIn().getBody(String.class)).toList();
        assertTrue(bodies.contains("a.txt") && bodies.contains("c.txt"), "a.txt and c.txt are consumed: " + bodies);
        assertFalse(bodies.contains("b.txt"), "b.txt cannot be got: " + bodies);
        assertTrue(skippedReported.await(20, TimeUnit.SECONDS), "the skipped file is reported: " + handled);
    }

    @Test
    void fileDeletedAfterTheListingIsSkipped() throws Exception {
        files.add("a.txt");
        files.add("b.txt");
        files.add("c.txt");
        deleted.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        addConsumerRoute("&exceptionHandler=#recordErrors");

        // before, the 404 of b.txt ended every poll with no exchange at all
        mock.assertIsSatisfied();
        List<String> bodies = mock.getReceivedExchanges().stream().map(e -> e.getIn().getBody(String.class)).toList();
        assertTrue(bodies.contains("a.txt") && bodies.contains("c.txt"), "a.txt and c.txt are consumed: " + bodies);
        assertFalse(bodies.contains("b.txt"), "b.txt no longer exists: " + bodies);
        assertTrue(handled.isEmpty(), "a file that no longer exists is not reported: " + handled);
    }

    @Test
    void pollFailsWhenNoListedFileCanBeGot() throws Exception {
        files.add("a.txt");
        files.add("b.txt");
        forbidden.add("a.txt");
        forbidden.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        addConsumerRoute("&pollStrategy=#countPolls");

        // as for a failed listing (expired credentials, no permission to read files): the poll fails, so the error
        // handling and the backoff of the scheduled consumer apply
        assertTrue(failedPolls.await(20, TimeUnit.SECONDS), "the polls fail");
        mock.assertIsSatisfied();
    }

    private void addConsumerRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-storage-datalake:account/fs?serviceClient=#client&initialDelay=0&delay=10" + options)
                        .convertBodyTo(String.class)
                        .to("mock:result");
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

    private DataLakeFileClient fileClient(String name) {
        DataLakeFileClient fileClient = mock(DataLakeFileClient.class);
        when(fileClient.openInputStream()).thenAnswer(i -> {
            if (forbidden.contains(name)) {
                // what the service answers to a read that the ACL of the file does not allow
                HttpResponse response = mock(HttpResponse.class);
                when(response.getStatusCode()).thenReturn(403);
                throw new DataLakeStorageException(
                        "This request is not authorized to perform this operation.", response,
                        null);
            }
            if (deleted.contains(name)) {
                // what the service answers to a read of a file deleted after the listing
                HttpResponse response = mock(HttpResponse.class);
                when(response.getStatusCode()).thenReturn(404);
                throw new DataLakeStorageException("The specified path does not exist.", response, null);
            }
            DataLakeFileOpenInputStreamResult result = mock(DataLakeFileOpenInputStreamResult.class);
            when(result.getInputStream()).thenReturn(new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
            return result;
        });
        return fileClient;
    }
}
