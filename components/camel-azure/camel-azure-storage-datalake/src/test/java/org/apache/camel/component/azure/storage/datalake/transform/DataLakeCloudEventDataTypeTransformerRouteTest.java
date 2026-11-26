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
package org.apache.camel.component.azure.storage.datalake.transform;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeFileSystemClient;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.models.DataLakeFileOpenInputStreamResult;
import com.azure.storage.file.datalake.models.PathItem;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The CloudEvent source of the files of the azure-storage-datalake consumer, when the transformer runs in another
 * route, as with the output data type of a Kamelet: that route gets a copy of the exchange with the headers of the
 * consumer, and its own from endpoint.
 */
class DataLakeCloudEventDataTypeTransformerRouteTest extends CamelTestSupport {

    @BindToRegistry("client")
    private final DataLakeServiceClient serviceClient = mock(DataLakeServiceClient.class);

    @Override
    @SuppressWarnings("unchecked")
    protected void doPreSetup() throws Exception {
        DataLakeFileSystemClient fileSystemClient = mock(DataLakeFileSystemClient.class);
        when(serviceClient.getFileSystemClient("reports")).thenReturn(fileSystemClient);

        AtomicBoolean listed = new AtomicBoolean();
        PagedIterable<PathItem> listing = mock(PagedIterable.class);
        when(listing.stream()).thenAnswer(i -> {
            // two files on the first poll, and none after
            List<String> names = listed.getAndSet(true) ? List.of() : List.of("a.txt", "b.txt");
            return names.stream().map(name -> new PathItem(null, null, 0L, null, false, name, null, null));
        });
        when(fileSystemClient.listPaths(any(), any())).thenReturn(listing);

        when(fileSystemClient.getFileClient(anyString())).thenAnswer(i -> {
            String name = i.getArgument(0);
            DataLakeFileClient fileClient = mock(DataLakeFileClient.class);
            when(fileClient.openInputStream()).thenAnswer(o -> {
                DataLakeFileOpenInputStreamResult result = mock(DataLakeFileOpenInputStreamResult.class);
                when(result.getInputStream()).thenReturn(new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
                return result;
            });
            return fileClient;
        });
    }

    @Test
    void theSourceIsTheFileSystemOfTheConsumer() throws Exception {
        // CAMEL-27101: the source was built from the archive status of the file, which a file that is not being
        // rehydrated does not have
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedHeaderValuesReceivedInAnyOrder(DataLakeConstants.FILE_NAME, "a.txt", "b.txt");
        result.allMessages().header(DataLakeConstants.CONSUMED_FILE_SYSTEM_NAME).isEqualTo("reports");
        result.allMessages().header(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).isEqualTo("azure.storage.datalake.reports");
        // the file system name header of the producer is not set, so a producer in the route does not write to this
        // file system
        result.allMessages().header(DataLakeConstants.FILESYSTEM_NAME).isNull();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands a copy of the exchange to the next route, as the sink of a Kamelet does
                from("azure-storage-datalake:account/reports?serviceClient=#client&initialDelay=0&delay=50")
                        .to("seda:next");

                from("seda:next")
                        .transformDataType("azure-storage-datalake:application-cloudevents")
                        .to("mock:result");
            }
        };
    }
}
