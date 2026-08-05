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

import com.google.cloud.storage.Blob;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProducerBlobMetadataTest extends GoogleCloudStorageBaseTest {

    private static final String FILE_NAME = "metadata.txt";

    @EndpointInject
    private ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:store").to("google-storage://myCamelBucket?autoCreateBucket=true");
                from("direct:getObject")
                        .to("google-storage://myCamelBucket?autoCreateBucket=true&operation=getObject");
            }
        };
    }

    @Test
    public void contentEncodingAndCacheControlAreStoredAsSent() {
        Exchange exchange = template.request("direct:store", e -> {
            e.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, FILE_NAME);
            e.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_TYPE, "text/plain");
            e.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, "gzip");
            e.getIn().setHeader(GoogleCloudStorageConstants.CACHE_CONTROL, "max-age=3600");
            e.getIn().setBody(new ByteArrayInputStream("Hi, How are you ?".getBytes()));
        });

        assertNotNull(exchange);
        Blob blob = exchange.getMessage().getBody(Blob.class);
        assertNotNull(blob);
        // each field has to carry its own header value, they used to be overwritten with the content type
        assertEquals("text/plain", blob.getContentType());
        assertEquals("gzip", blob.getContentEncoding());
        assertEquals("max-age=3600", blob.getCacheControl());
    }

    @Test
    public void getObjectOnAMissingObjectReportsTheObjectName() {
        Exchange exchange = template.request("direct:getObject",
                e -> e.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, "there-is-no-such-object.txt"));

        assertNotNull(exchange);
        Exception exception = exchange.getException();
        assertNotNull(exception, "a missing object must fail the exchange");
        assertEquals("Object there-is-no-such-object.txt does not exist in bucket myCamelBucket",
                exception.getMessage());
    }
}
