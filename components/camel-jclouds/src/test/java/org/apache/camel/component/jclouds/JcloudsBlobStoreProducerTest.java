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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;

import com.google.common.collect.Lists;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.xml.StreamSourceConverter;
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
        StreamCache streamCache = StreamSourceConverter.convertToStreamCache(new SAXSource(new InputSource(inputStream)), exchange);
        template.sendBody("direct:put-and-get", streamCache);
        Object result = template.requestBodyAndHeader("direct:put-and-get", null, JcloudsConstants.OPERATION, JcloudsConstants.GET, String.class);
        assertEquals(MESSAGE, result);
    }
    
    @Test
    public void testBlobStorePutAndCount() throws InterruptedException {
        String message = "Some message";
        template.sendBody("direct:put-and-count", message);
        Object result = template.requestBodyAndHeader("direct:put-and-count", null, JcloudsConstants.OPERATION, JcloudsConstants.COUNT_BLOBS, Long.class);
        assertEquals(new Long(1), result);
    }
    
    @Test
    public void testBlobStorePutAndRemove() throws InterruptedException {
        String message = "Some message";
        template.sendBody("direct:put-and-remove", message);
        template.requestBodyAndHeader("direct:put-and-remove", null, JcloudsConstants.OPERATION, JcloudsConstants.REMOVE_BLOB);
        Object result = template.requestBodyAndHeader("direct:put-and-remove", null, JcloudsConstants.OPERATION, JcloudsConstants.COUNT_BLOBS, Long.class);
        assertEquals(new Long(0), result);
    }
    
    @Test
    public void testBlobStorePutAndClear() throws InterruptedException {
        String message = "Some message";
        template.sendBody("direct:put-and-clear", message);
        Object result = template.requestBodyAndHeader("direct:put-and-count", null, JcloudsConstants.OPERATION, JcloudsConstants.COUNT_BLOBS, Long.class);
        assertEquals(new Long(1), result);
        template.requestBodyAndHeader("direct:put-and-clear", null, JcloudsConstants.OPERATION, JcloudsConstants.CLEAR_CONTAINER);
        result = template.requestBodyAndHeader("direct:put-and-count", null, JcloudsConstants.OPERATION, JcloudsConstants.COUNT_BLOBS, Long.class);
        assertEquals(new Long(0), result);
    }
    
    @Test
    public void testBlobStorePutAndDeleteContainer() throws InterruptedException {
        String message = "Some message";
        template.sendBody("direct:put-and-delete-container", message);
        Object result = template.requestBodyAndHeader("direct:put-and-count", null, JcloudsConstants.OPERATION, JcloudsConstants.COUNT_BLOBS, Long.class);
        assertEquals(new Long(1), result);
        template.requestBodyAndHeader("direct:put-and-delete-container", null, JcloudsConstants.OPERATION, JcloudsConstants.DELETE_CONTAINER);
    }
    
    @Test
    public void testCheckContainerExists() throws InterruptedException {
        Object result = template.requestBodyAndHeader("direct:put-and-count", null, JcloudsConstants.OPERATION, JcloudsConstants.CONTAINER_EXISTS, Boolean.class);
        assertEquals(true, result);
        Map<String, Object> headers = new HashMap<>();
        headers.put(JcloudsConstants.OPERATION, JcloudsConstants.CONTAINER_EXISTS);
        headers.put(JcloudsConstants.CONTAINER_NAME, "otherTest");
        result = template.requestBodyAndHeaders("direct:container-exists", null, headers, Boolean.class);
        assertEquals(false, result);
    }
    
    @Test
    public void testRemoveBlobs() throws InterruptedException {
        template.sendBody("direct:put", "test message");
        Object result = template.requestBodyAndHeader("direct:put-and-count", null, JcloudsConstants.OPERATION, JcloudsConstants.COUNT_BLOBS, Long.class);
        assertEquals(new Long(1), result);
        List blobsToRemove = new ArrayList<>();
        blobsToRemove.add(TEST_BLOB_IN_DIR);
        Map<String, Object> headers = new HashMap<>();
        headers.put(JcloudsConstants.OPERATION, JcloudsConstants.REMOVE_BLOBS);
        headers.put(JcloudsConstants.CONTAINER_NAME, TEST_CONTAINER);
        headers.put(JcloudsConstants.BLOB_NAME_LIST, blobsToRemove);
        template.sendBodyAndHeaders("direct:remove-blobs", null, headers);
        result = template.requestBodyAndHeader("direct:put-and-count", null, JcloudsConstants.OPERATION, JcloudsConstants.COUNT_BLOBS, Long.class);
        assertEquals(new Long(0), result);
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
                
                from("direct:put-and-count")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB_IN_DIR))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient");
                
                from("direct:put-and-remove")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB_IN_DIR))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient");
                
                from("direct:put-and-clear")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB_IN_DIR))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient");
                
                from("direct:put-and-delete-container")
                        .setHeader(JcloudsConstants.BLOB_NAME, constant(TEST_BLOB_IN_DIR))
                        .setHeader(JcloudsConstants.CONTAINER_NAME, constant(TEST_CONTAINER))
                        .to("jclouds:blobstore:transient");
                
                from("direct:container-exists")
                        .to("jclouds:blobstore:transient");
                
                from("direct:remove-blobs")
                        .to("jclouds:blobstore:transient");
            }
        };
    }
}
