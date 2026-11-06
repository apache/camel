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
package org.apache.camel.component.aws2.s3;

import java.io.InputStream;
import java.time.Instant;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The getObject operation with ignoreBody=true sends a HEAD request, so the content of the object is not downloaded,
 * and sets the same headers as with a GET.
 */
class AWS2S3ProducerGetObjectIgnoreBodyTest extends CamelTestSupport {

    // the Last-Modified header that the fake sends for every object
    private static final Instant LAST_MODIFIED = Instant.parse("2026-10-09T00:00:00Z");

    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        s3.start();
        return super.createCamelContext();
    }

    @AfterEach
    void stopServer() {
        if (s3 != null) {
            s3.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri = "aws2-s3://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&overrideEndpoint=true"
                             + "&forcePathStyle=true&uriEndpointOverride=" + s3.endpoint() + "&operation=getObject";
                from("direct:get").to(uri);
                from("direct:ignoreBody").to(uri + "&ignoreBody=true");
                from("direct:ignoreBodyPojo").to(uri + "&ignoreBody=true&pojoRequest=true");
            }
        };
    }

    @Test
    void getObjectWithIgnoreBodySendsAHeadRequest() {
        s3.put("a.txt", "abc");

        Exchange exchange = template.request("direct:ignoreBody", e -> {
            e.getIn().setHeader(AWS2S3Constants.KEY, "a.txt");
            e.getIn().setBody("unchanged");
        });

        assertEquals(0, s3.gets());
        assertEquals(1, s3.heads("a.txt"));
        Message message = exchange.getMessage();
        assertEquals("unchanged", message.getBody());
        assertEquals("\"0123456789abcdef0123456789abcdef\"", message.getHeader(AWS2S3Constants.E_TAG));
        assertEquals(3L, message.getHeader(AWS2S3Constants.CONTENT_LENGTH));
        assertEquals("text/plain", message.getHeader(AWS2S3Constants.CONTENT_TYPE));
        assertEquals(200, message.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("a.txt", message.getHeader(AWS2S3Constants.PRODUCED_KEY));
        assertEquals("bkt", message.getHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME));
        assertEquals(LAST_MODIFIED, message.getHeader(AWS2S3Constants.LAST_MODIFIED));
        assertEquals(LAST_MODIFIED.toEpochMilli(), message.getHeader(AWS2S3Constants.MESSAGE_TIMESTAMP));
    }

    @Test
    void getObjectWithIgnoreBodyAndAPojoRequestSendsAHeadRequest() {
        s3.put("a.txt", "abc");
        GetObjectRequest request = GetObjectRequest.builder().bucket("bkt").key("a.txt").build();

        Exchange exchange = template.request("direct:ignoreBodyPojo", e -> e.getIn().setBody(request));

        assertEquals(0, s3.gets());
        assertEquals(1, s3.heads("a.txt"));
        Message message = exchange.getMessage();
        assertSame(request, message.getBody());
        assertEquals(3L, message.getHeader(AWS2S3Constants.CONTENT_LENGTH));
        assertEquals(200, message.getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(LAST_MODIFIED, message.getHeader(AWS2S3Constants.LAST_MODIFIED));
        assertEquals(LAST_MODIFIED.toEpochMilli(), message.getHeader(AWS2S3Constants.MESSAGE_TIMESTAMP));
    }

    @Test
    void getObjectWithIgnoreBodyOfAnObjectThatDoesNotExistFails() {
        Exchange exchange = template.request("direct:ignoreBody", e -> e.getIn().setHeader(AWS2S3Constants.KEY, "a.txt"));

        // as with a get
        assertInstanceOf(NoSuchKeyException.class, exchange.getException());
    }

    @Test
    void getObjectWithoutIgnoreBodySendsAGetRequest() throws Exception {
        s3.put("a.txt", "abc");

        Exchange exchange = template.request("direct:get", e -> e.getIn().setHeader(AWS2S3Constants.KEY, "a.txt"));

        assertEquals(1, s3.gets());
        assertEquals(0, s3.heads("a.txt"));
        try (InputStream body = exchange.getMessage().getBody(InputStream.class)) {
            assertEquals("abc", context.getTypeConverter().convertTo(String.class, body));
        }
        assertEquals(3L, exchange.getMessage().getHeader(AWS2S3Constants.CONTENT_LENGTH));
        // the same as with ignoreBody=true
        assertEquals(LAST_MODIFIED, exchange.getMessage().getHeader(AWS2S3Constants.LAST_MODIFIED));
        assertEquals(LAST_MODIFIED.toEpochMilli(), exchange.getMessage().getHeader(AWS2S3Constants.MESSAGE_TIMESTAMP));
    }
}
