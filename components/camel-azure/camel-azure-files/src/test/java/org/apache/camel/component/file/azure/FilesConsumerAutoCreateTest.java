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
package org.apache.camel.component.file.azure;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.Context;
import com.azure.storage.file.share.ShareClient;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareServiceClient;
import com.azure.storage.file.share.models.ShareFileItem;
import com.azure.storage.file.share.models.ShareStorageException;
import com.azure.storage.file.share.options.ShareDirectoryCreateOptions;
import com.azure.storage.file.share.options.ShareListFilesAndDirectoriesOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer creates its starting directory as the route starts (autoCreate, the default). When the share refuses the
 * connection or the creation of the directory, the route still starts, as with the ftp and smb consumers, and each poll
 * tries again until it succeeds.
 */
class FilesConsumerAutoCreateTest extends CamelTestSupport {

    private final ShareServiceClient service = mock(ShareServiceClient.class);
    private final ShareClient share = mock(ShareClient.class);
    private final ShareDirectoryClient root = mock(ShareDirectoryClient.class);
    private final ShareDirectoryClient inbox = mock(ShareDirectoryClient.class);
    private final AtomicBoolean connectRefused = new AtomicBoolean();
    private final AtomicBoolean createRefused = new AtomicBoolean();
    private final AtomicBoolean inboxExists = new AtomicBoolean();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("azure-files", new MockedShareComponent());
        return context;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void mockShare() {
        when(service.getShareClient("share")).thenReturn(share);
        when(share.getRootDirectoryClient()).thenReturn(root);
        when(root.existsWithResponse(nullable(Duration.class), any(Context.class))).thenAnswer(inv -> {
            if (connectRefused.get()) {
                throw forbidden("AuthenticationFailed");
            }
            return new SimpleResponse<>(null, 200, null, true);
        });
        when(root.getSubdirectoryClient("inbox")).thenReturn(inbox);
        when(inbox.existsWithResponse(nullable(Duration.class), any(Context.class)))
                .thenAnswer(inv -> new SimpleResponse<>(null, inboxExists.get() ? 200 : 404, null, inboxExists.get()));
        when(root.createSubdirectoryIfNotExistsWithResponse(eq("inbox"), any(ShareDirectoryCreateOptions.class),
                nullable(Duration.class), any(Context.class))).thenAnswer(inv -> {
                    if (createRefused.get()) {
                        throw forbidden("AuthorizationPermissionMismatch");
                    }
                    inboxExists.set(true);
                    return new SimpleResponse<>(null, 201, null, inbox);
                });
        PagedIterable<ShareFileItem> empty = mock(PagedIterable.class);
        when(empty.stream()).thenAnswer(inv -> Stream.empty());
        when(inbox.listFilesAndDirectories(any(ShareListFilesAndDirectoriesOptions.class), nullable(Duration.class),
                any(Context.class))).thenReturn(empty);
    }

    @Test
    void routeStartsWhenTheStartingDirectoryCannotBeCreated() throws Exception {
        createRefused.set(true);

        ScheduledPollConsumer consumer = startRoute();
        verifyCreateAttempts(1);

        // each poll tries again, and is skipped while the directory cannot be created
        consumer.run();
        verifyCreateAttempts(2);
        verifyListings(0);

        // permission granted: the next poll creates the directory and lists it
        createRefused.set(false);
        consumer.run();
        verifyCreateAttempts(3);
        verifyListings(1);
    }

    @Test
    void routeStartsWhenTheShareRefusesTheConnection() throws Exception {
        connectRefused.set(true);

        ScheduledPollConsumer consumer = startRoute();
        verifyCreateAttempts(0);

        // each poll connects again, and is skipped while the connection is refused
        consumer.run();
        verifyCreateAttempts(0);
        verifyListings(0);

        // credentials fixed: the next poll connects, creates the directory and lists it
        connectRefused.set(false);
        consumer.run();
        verifyCreateAttempts(1);
        verifyListings(1);
    }

    @Test
    void routeStartsWhenTheStartingDirectoryExists() throws Exception {
        inboxExists.set(true);

        ScheduledPollConsumer consumer = startRoute();
        consumer.run();

        verifyCreateAttempts(0);
        verifyListings(1);
    }

    @Test
    void refusedCreationIsAnOperationFailureCausedByTheAzureError() {
        createRefused.set(true);
        FilesEndpoint endpoint = context.getEndpoint("azure-files://account/share/inbox", FilesEndpoint.class);
        FilesOperations operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);

        GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                () -> operations.buildDirectory("/inbox", true));
        assertEquals(403, assertInstanceOf(ShareStorageException.class, e.getCause()).getStatusCode());
    }

    @Test
    void producerFailureToCreateTheDirectoryStillMatchesOnExceptionOfTheAzureError() throws Exception {
        // the producer builds directories with the same operations: the error handler finds the Azure error as the cause
        createRefused.set(true);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(ShareStorageException.class).handled(true).to("mock:refused");
                from("direct:write").to("azure-files://account/share/inbox");
            }
        });
        getMockEndpoint("mock:refused").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:write", "hello", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
        verifyCreateAttempts(1);
    }

    @Test
    void refusedConnectionIsAnOperationFailureCausedByTheAzureError() {
        connectRefused.set(true);
        FilesEndpoint endpoint = context.getEndpoint("azure-files://account/share/inbox", FilesEndpoint.class);
        FilesOperations operations = endpoint.createRemoteFileOperations();

        GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                () -> operations.connect(endpoint.getConfiguration(), null));
        assertEquals(403, assertInstanceOf(ShareStorageException.class, e.getCause()).getStatusCode());
    }

    private ScheduledPollConsumer startRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-files://account/share/inbox?startScheduler=false").routeId("inbox")
                        .to("mock:result");
            }
        });
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("inbox"));
        return (ScheduledPollConsumer) context.getRoute("inbox").getConsumer();
    }

    private void verifyCreateAttempts(int attempts) {
        verify(root, times(attempts)).createSubdirectoryIfNotExistsWithResponse(anyString(),
                any(ShareDirectoryCreateOptions.class), nullable(Duration.class), any(Context.class));
    }

    private void verifyListings(int listings) {
        verify(inbox, times(listings)).listFilesAndDirectories(any(ShareListFilesAndDirectoriesOptions.class),
                nullable(Duration.class), any(Context.class));
    }

    private static ShareStorageException forbidden(String errorCode) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.getStatusCode()).thenReturn(403);
        return new ShareStorageException("Status code 403, " + errorCode, response, null);
    }

    /**
     * Its endpoints use the mocked share instead of connecting to Azure.
     */
    private final class MockedShareComponent extends FilesComponent {

        @Override
        protected FilesEndpoint buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters)
                throws Exception {
            return new FilesEndpoint(uri, this, new FilesConfiguration(FilesURIStrings.getBaseURI(uri))) {
                @Override
                public FilesOperations createRemoteFileOperations() {
                    return new FilesOperations(this) {
                        @Override
                        public ShareServiceClient getClient() {
                            return service;
                        }
                    };
                }
            };
        }
    }
}
