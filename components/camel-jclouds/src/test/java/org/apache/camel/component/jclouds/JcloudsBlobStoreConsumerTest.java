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
package org.apache.camel.component.jclouds;

import com.google.common.collect.Lists;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.io.payloads.StringPayload;
import org.junit.Test;

public class JcloudsBlobStoreConsumerTest extends CamelTestSupport {

    private static final String TEST_CONTAINER = "testContainer";
    private static final String TEST_BLOB1 = "testBlob1";
    private static final String TEST_BLOB2 = "testBlob2";

    private static final String TEST_CONTAINER_WITH_DIR = "testContainerWithDirectories";
    private static final String TEST_BLOB_IN_DIR = "dir/testBlob";
    private static final String TEST_BLOB_IN_OTHER = "other/testBlob";

    BlobStore blobStore = ContextBuilder.newBuilder("transient").credentials("id", "credential").buildView(BlobStoreContext.class).getBlobStore();

    @Test
    public void testBlobStoreGetOneBlob() throws InterruptedException {
        String message = "Some message";

        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived(message);

        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER, TEST_BLOB1, new StringPayload(message));

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testBlobStoreGetTwoBlobs() throws InterruptedException {
        String message1 = "Blob 1";
        String message2 = "Blob 2";

        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived(message1, message2);

        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER, TEST_BLOB1, new StringPayload(message1));
        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER, TEST_BLOB2, new StringPayload(message2));

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testBlobStoreWithDirectory() throws InterruptedException {
        String message1 = "Blob in directory";

        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results-in-dir", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived(message1);

        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER_WITH_DIR, TEST_BLOB_IN_DIR, new StringPayload(message1));

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testBlobStoreWithMultipleDirectories() throws InterruptedException {
        String message1 = "Blob in directory";
        String message2 = "Blob in other directory";

        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results-in-dir", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived(message1);

        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER_WITH_DIR, TEST_BLOB_IN_DIR, new StringPayload(message1));
        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER_WITH_DIR, TEST_BLOB_IN_OTHER, new StringPayload(message2));

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        blobStore.createContainerInLocation(null, TEST_CONTAINER);
        blobStore.createContainerInLocation(null, TEST_CONTAINER_WITH_DIR);
        ((JcloudsComponent) context.getComponent("jclouds")).setBlobStores(Lists.newArrayList(blobStore));

        return new RouteBuilder() {
            public void configure() {
                from("jclouds:blobstore:transient?container=" + TEST_CONTAINER)
                        .convertBodyTo(String.class)
                        .to("mock:results");

                from("jclouds:blobstore:transient?container=" + TEST_CONTAINER_WITH_DIR + "&directory=dir")
                        .convertBodyTo(String.class)
                        .to("mock:results-in-dir");
            }
        };
    }
}
