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

public class JcloudsMultipleBlobStoreTest extends CamelTestSupport {

    private static final String TEST_CONTAINER = "testContainer";
    private static final String TEST_BLOB1 = "testBlob1";
    private static final String TEST_BLOB2 = "testBlob2";

    BlobStoreContext blobStoreContext1 = ContextBuilder.newBuilder("transient").name("b1").credentials("identity", "credential").build(BlobStoreContext.class);
    BlobStore blobStore1 = blobStoreContext1.getBlobStore();

    BlobStoreContext blobStoreContext2 = ContextBuilder.newBuilder("transient").name("b2").credentials("identity", "credential").build(BlobStoreContext.class);
    BlobStore blobStore2 = blobStoreContext2.getBlobStore();

    @Test
    public void testWithMultipleServices() throws InterruptedException {
        String message1 = "Blob 1";
        String message2 = "Blob 2";

        MockEndpoint mockEndpoint1 = resolveMandatoryEndpoint("mock:results1", MockEndpoint.class);
        mockEndpoint1.expectedBodiesReceived(message1);

        MockEndpoint mockEndpoint2 = resolveMandatoryEndpoint("mock:results2", MockEndpoint.class);
        mockEndpoint2.expectedBodiesReceived(message2);

        JcloudsBlobStoreHelper.writeBlob(blobStore1, TEST_CONTAINER, TEST_BLOB1, new StringPayload(message1));
        JcloudsBlobStoreHelper.writeBlob(blobStore2, TEST_CONTAINER, TEST_BLOB2, new StringPayload(message2));

        mockEndpoint1.assertIsSatisfied();
        mockEndpoint2.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        blobStore1.createContainerInLocation(null, TEST_CONTAINER);
        blobStore2.createContainerInLocation(null, TEST_CONTAINER);
        ((JcloudsComponent) context.getComponent("jclouds")).setBlobStores(Lists.newArrayList(blobStore1, blobStore2));

        return new RouteBuilder() {
            public void configure() {
                from("jclouds:blobstore:b1?container=" + TEST_CONTAINER)
                        .convertBodyTo(String.class)
                        .to("mock:results1");

                from("jclouds:blobstore:b2?container=" + TEST_CONTAINER)
                        .convertBodyTo(String.class)
                        .to("mock:results2");
            }
        };
    }
}
