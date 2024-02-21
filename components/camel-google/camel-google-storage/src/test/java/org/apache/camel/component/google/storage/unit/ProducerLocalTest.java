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
package org.apache.camel.component.google.storage.unit;

import java.io.ByteArrayInputStream;
import java.util.List;

import com.google.api.services.storage.model.Bucket;
import com.google.cloud.storage.Blob;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.apache.camel.component.google.storage.GoogleCloudStorageOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProducerLocalTest extends GoogleCloudStorageBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ProducerLocalTest.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                String endpoint = "google-storage://myCamelBucket?autoCreateBucket=true";

                from("direct:addObject").to(endpoint);
                from("direct:getObject").to(endpoint);
                from("direct:listBucket").to(endpoint);
                from("direct:listObjects").to(endpoint);
                from("direct:downloadLink").to(endpoint);
                from("direct:deleteObject").to(endpoint);
                from("direct:deleteBucket").to(endpoint).to("mock:result");

            }
        };
    }

    @SuppressWarnings("unchecked")
    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        final String fileName = "just_a_file.txt";

        //upload a file
        byte[] payload = "Hi, How are you ?".getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        Exchange addObjectExchange = template.request("direct:addObject", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, fileName);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, "text/plain");
            exchange.getIn().setBody(bais);
        });
        Blob addObject = addObjectExchange.getMessage().getBody(Blob.class);
        LOG.info("addObject {}", addObject);
        assertNotNull(addObject);
        assertEquals(fileName, addObject.getName());

        Exchange listBucketsExchange = template.request("direct:listBucket", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OPERATION,
                    GoogleCloudStorageOperations.listBuckets);
        });
        List<Bucket> bucketsList = listBucketsExchange.getMessage().getBody(List.class);
        LOG.info("bucketsList {}", bucketsList);
        assertEquals(1, bucketsList.size());

        Exchange listObjectsExchange = template.request("direct:listObjects", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OPERATION,
                    GoogleCloudStorageOperations.listObjects);
        });
        LOG.info("listObjectsExchange.body={}", listObjectsExchange.getMessage().getBody());
        List<Blob> resp = listObjectsExchange.getMessage().getBody(List.class);
        assertEquals(1, resp.size());
        assertEquals(fileName, resp.get(0).getName());

        Exchange getObjectExchange = template.request("direct:getObject", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OPERATION,
                    GoogleCloudStorageOperations.getObject);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, fileName);
        });
        String getObject = getObjectExchange.getMessage().getBody(String.class);
        LOG.info("getObject: {}", getObject);
        assertNotNull(getObject);
        assertEquals(fileName, getObjectExchange.getMessage().getHeader(GoogleCloudStorageConstants.OBJECT_NAME));

        /*
        //sign url
        Exchange downloadLinkExchange = template.request( "direct:downloadLink", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OPERATION, GoogleCloudStorageOperations.createDownloadLink);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, "readme.txt" );
            exchange.getIn().setHeader(GoogleCloudStorageConstants.DOWNLOAD_LINK_EXPIRATION_TIME, 86400000L); //1 day
        });
        URL downloadLink = downloadLinkExchange.getMessage().getBody(URL.class);
        LOG.info("downloadLink {}", downloadLink );
        assertNotNull( downloadLink );
        */

        Exchange deleteObjectExchange = template.send("direct:deleteObject", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OPERATION,
                    GoogleCloudStorageOperations.deleteObject);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, fileName);
        });

        boolean deleteObject = deleteObjectExchange.getMessage().getBody(Boolean.class).booleanValue();
        LOG.info("deleteObject {}", deleteObject);
        assertTrue(deleteObject);

        Exchange deleteBucketExchange = template.send("direct:deleteBucket", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OPERATION,
                    GoogleCloudStorageOperations.deleteBucket);
        });
        boolean deleteBucket = deleteBucketExchange.getMessage().getBody(Boolean.class).booleanValue();
        LOG.info("deleteBucket {}", deleteBucket);
        assertTrue(deleteBucket);

        MockEndpoint.assertIsSatisfied(context);
    }

}
