/**
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
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.BlobStoreContextFactory;
import org.junit.Test;

public class JcloudsBlobStoreConsumerTest extends CamelTestSupport {

    private static final String TEST_CONTAINER = "testContainer";
    private static final String TEST_BLOB1 = "testBlob1";
    private static final String TEST_BLOB2 = "testBlob2";

    BlobStoreContextFactory contextFactory = new BlobStoreContextFactory();
    BlobStoreContext blobStoreContext = contextFactory.createContext("transient", "identity", "credential");
    BlobStore blobStore = blobStoreContext.getBlobStore();

    @Test
    public void testBlobStoreGetOneBlob() throws InterruptedException {
        String message = "Some message";
        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER, TEST_BLOB1, message);

        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        mockEndpoint.expectedBodiesReceived(message);

        mockEndpoint.assertIsSatisfied();

    }

    @Test
    public void testBlobStoreGetTwoBlobs() throws InterruptedException {
        String message1 = "Blob 1";
        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER, TEST_BLOB1, message1);

        String message2 = "Blob 2";
        JcloudsBlobStoreHelper.writeBlob(blobStore, TEST_CONTAINER, TEST_BLOB2, message2);

        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(2);
        mockEndpoint.expectedBodiesReceived(message1, message2);

        mockEndpoint.assertIsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        blobStore.createContainerInLocation(null, TEST_CONTAINER);
        ((JcloudsComponent) context.getComponent("jclouds")).setBlobStores(Lists.newArrayList(blobStore));

        return new RouteBuilder() {
            public void configure() {

                from("jclouds:blobstore:transient?container=" + TEST_CONTAINER)
                        .to("mock:results");
            }
        };
    }
}
