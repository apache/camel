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

public class JcloudsBlobStoreProducerTest extends CamelTestSupport {

    private static final String TEST_CONTAINER = "testContainer";
    private static final String TEST_BLOB = "testBlob";

    BlobStoreContextFactory contextFactory = new BlobStoreContextFactory();
    BlobStoreContext blobStoreContext = contextFactory.createContext("transient", "identity", "credential");
    BlobStore blobStore = blobStoreContext.getBlobStore();

    @Test
    public void testBlobStorePut() throws InterruptedException {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);
        template.sendBody("direct:put", "Some message");
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testBlobStorePutAndGet() throws InterruptedException {
        String message = "Some message";
        template.sendBody("direct:put-and-get", message);
        Object result = template.requestBodyAndHeader("direct:put-and-get", null, JcloudsConstants.OPERATION, JcloudsConstants.GET);
        assertEquals(message, result);
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        blobStore.createContainerInLocation(null, TEST_CONTAINER);
        ((JcloudsComponent)context.getComponent("jclouds")).setBlobStores(Lists.newArrayList(blobStore));

        return new RouteBuilder() {
            public void configure() {
                from("direct:put")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient").to("mock:results");

                from("direct:put-and-get")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient");
            }
        };
    }
}
