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

import com.azure.storage.file.share.models.ShareFileItem;
import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.file.azure.transform.AzureFilesCloudEventDataTypeTransformer;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * The consumer sets the name of the share on each exchange, and the CloudEvent data type names the share as the source
 * (CAMEL-27538).
 */
class FilesConsumerShareNameTest extends CamelTestSupport {

    @Test
    void testConsumerSetsShareName() {
        FilesEndpoint endpoint = context.getEndpoint("azure-files://account/myshare/inbox", FilesEndpoint.class);
        TestConsumer consumer = new TestConsumer(endpoint, mock(FilesOperations.class));

        Exchange exchange = consumer.exchangeOf(remoteFile(endpoint, "inbox", "hello.txt"));

        assertEquals("myshare", exchange.getIn().getHeader(FilesConstants.SHARE_NAME));
        assertEquals("account.file.core.windows.net", exchange.getIn().getHeader(FilesConstants.FILE_HOST));

        new AzureFilesCloudEventDataTypeTransformer().transform(exchange.getIn(), DataType.ANY, DataType.ANY);
        assertEquals("azure.storage.files.myshare", exchange.getIn().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertEquals("hello.txt", exchange.getIn().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    void testConsumerSetsShareNameWithoutDirectory() {
        FilesEndpoint endpoint = context.getEndpoint("azure-files://account/othershare", FilesEndpoint.class);
        TestConsumer consumer = new TestConsumer(endpoint, mock(FilesOperations.class));

        Exchange exchange = consumer.exchangeOf(remoteFile(endpoint, "", "hello.txt"));

        assertEquals("othershare", exchange.getIn().getHeader(FilesConstants.SHARE_NAME));
    }

    private static RemoteFile<ShareFileItem> remoteFile(FilesEndpoint endpoint, String directory, String name) {
        RemoteFile<ShareFileItem> file = new RemoteFile<>();
        file.setEndpointPath(directory);
        file.setFileNameOnly(name);
        file.setFileName(name);
        file.setRelativeFilePath(name);
        file.setAbsoluteFilePath(directory.isEmpty() ? name : directory + "/" + name);
        file.setHostname(endpoint.getConfiguration().getHost());
        return file;
    }

    /**
     * Gives the test access to the exchange that the consumer creates for a polled file.
     */
    private static final class TestConsumer extends FilesConsumer {

        TestConsumer(FilesEndpoint endpoint, FilesOperations operations) {
            super(endpoint, exchange -> {
            }, operations, null);
        }

        Exchange exchangeOf(RemoteFile<ShareFileItem> file) {
            return createExchange(file);
        }
    }
}
