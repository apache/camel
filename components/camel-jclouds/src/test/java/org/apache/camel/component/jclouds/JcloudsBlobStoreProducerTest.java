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

import java.io.ByteArrayInputStream;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import com.google.common.collect.Lists;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.StreamCacheConverter;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.junit.Test;

public class JcloudsBlobStoreProducerTest extends CamelTestSupport {

    private static final String TEST_CONTAINER = "testContainer";
    private static final String TEST_BLOB_IN_DIR = "/dir/testBlob";
    private static final String MESSAGE = "<test>This is a test</test>";
        
    BlobStoreContext blobStoreContext = ContextBuilder.newBuilder("transient").credentials("identity", "credential").build(BlobStoreContext.class);
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
        Object result = template.requestBodyAndHeader("direct:put-and-get", null, JcloudsConstants.OPERATION, JcloudsConstants.GET, String.class);
        assertEquals(message, result);
    }

    @Test
    public void testBlobStorePutWithStreamAndGet() throws InterruptedException, TransformerException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(MESSAGE.getBytes());
        Exchange exchange = new DefaultExchange(context);
        StreamCache streamCache = StreamCacheConverter.convertToStreamCache(new SAXSource(new InputSource(inputStream)), exchange);
        template.sendBody("direct:put-and-get", streamCache);
        Object result = template.requestBodyAndHeader("direct:put-and-get", null, JcloudsConstants.OPERATION, JcloudsConstants.GET, String.class);
        assertEquals(MESSAGE, result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        blobStore.createContainerInLocation(null, TEST_CONTAINER);
        ((JcloudsComponent) context.getComponent("jclouds")).setBlobStores(Lists.newArrayList(blobStore));

        return new RouteBuilder() {
            public void configure() {
                from("direct:put")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB_IN_DIR))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient").to("mock:results");

                from("direct:put-and-get")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB_IN_DIR))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient");
            }
        };
    }
}
