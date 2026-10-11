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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A listed object that cannot be got (it no longer exists, its get is refused, its download fails) does not fail the
 * poll: the consumer skips it, consumes the other objects of the poll, and does not leave it in the in-progress
 * repository, so a later poll consumes it (or a new object with the same key).
 * <p>
 * The scheduler of the consumer is not started: the tests call {@code poll()} themselves.
 */
class AWS2S3ConsumerDeletedObjectTest extends CamelTestSupport {

    protected FakeS3Server s3;

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

    private String s3Uri(String options) {
        return "aws2-s3://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&overrideEndpoint=true&forcePathStyle=true"
               + "&uriEndpointOverride=" + s3.endpoint() + "&startScheduler=false" + options;
    }

    protected AWS2S3Consumer startRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(s3Uri(options)).routeId("s3")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
        return (AWS2S3Consumer) context.getRoute("s3").getConsumer();
    }

    @Test
    void listedObjectThatNoLongerExistsIsSkipped() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        // deleted after the listing has returned it and before the consumer gets it
        s3.removeWhenListed("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "c");
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 2);
        AWS2S3Consumer consumer = startRoute("");

        int polled = consumer.poll();

        // the object that is gone is not counted either
        assertEquals(2, polled);
        mock.assertIsSatisfied();
        assertFalse(consumer.getEndpoint().getInProgressRepository().contains("b.txt"), "b.txt is still in progress");
    }

    @Test
    void objectCreatedAgainAfterItWasSkippedIsConsumed() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.removeWhenListed("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        AWS2S3Consumer consumer = startRoute("&includeBody=false");

        assertEquals(1, consumer.poll());
        mock.assertIsSatisfied();

        // a new object with the key of the one that was skipped
        mock.reset();
        mock.expectedBodiesReceived("b2");
        s3.put("b.txt", "b2");

        assertEquals(1, consumer.poll());
        mock.assertIsSatisfied();
    }

    @Test
    void objectWhoseGetIsRefusedDoesNotStopTheOtherObjects() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        // as an object that cannot be read at all (archived, access denied): every get fails
        s3.refuseGets("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "c");
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 2);
        AWS2S3Consumer consumer = startRoute("");

        // the poll does not fail, and the objects listed before and after the refused one are consumed
        assertEquals(2, consumer.poll());
        mock.assertIsSatisfied();
        assertEquals(1, s3.refusedGets("b.txt"));
        assertFalse(consumer.getEndpoint().getInProgressRepository().contains("b.txt"), "b.txt is still in progress");

        // the following poll consumes a new object, and tries the refused one again
        mock.reset();
        mock.expectedBodiesReceived("d");
        s3.put("d.txt", "d");
        assertEquals(1, consumer.poll());
        mock.assertIsSatisfied();
        assertEquals(2, s3.refusedGets("b.txt"));

        // and the object is consumed once it can be got
        mock.reset();
        mock.expectedBodiesReceived("b");
        s3.allowGets("b.txt");
        assertEquals(1, consumer.poll());
        mock.assertIsSatisfied();
    }

    @Test
    void skippedObjectOfATruncatedListingIsTriedAgainWhenTheListingStartsOver() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        s3.put("d.txt", "d");
        s3.refuseGets("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        AWS2S3Consumer consumer = startRoute("&maxMessagesPerPoll=2");

        // first page (a, b): the skipped object is not counted
        assertEquals(1, consumer.poll());
        mock.assertIsSatisfied();

        // the marker has moved on as for any page: the second page (c, d) is consumed, and b is not tried
        mock.reset();
        mock.expectedBodiesReceived("c", "d");
        assertEquals(2, consumer.poll());
        mock.assertIsSatisfied();
        assertEquals(1, s3.refusedGets("b.txt"));

        // the listing starts over: b is tried again
        assertEquals(0, consumer.poll());
        assertEquals(2, s3.refusedGets("b.txt"));

        mock.reset();
        mock.expectedBodiesReceived("b");
        s3.allowGets("b.txt");
        assertEquals(1, consumer.poll());
        mock.assertIsSatisfied();
    }

    @Test
    void objectWhoseDownloadFailsIsSkippedAndNotLeftInProgress() throws Exception {
        AWS2S3Endpoint endpoint = context.getEndpoint(s3Uri(""), AWS2S3Endpoint.class);
        AtomicBoolean closed = new AtomicBoolean();
        // the download of the object fails while its content is read into the body (includeBody=true)
        InputStream content = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        AWS2S3Consumer consumer = new AWS2S3Consumer(endpoint, exchange -> {
        }) {
            @Override
            protected S3Client getAmazonS3Client() {
                return oneObject("a.txt", content);
            }
        };
        // not started, but the exchange factory of the consumer must be ready
        consumer.build();

        assertEquals(0, consumer.poll());

        assertTrue(closed.get(), "the object stream of a.txt was not closed");
        assertFalse(endpoint.getInProgressRepository().contains("a.txt"), "a.txt is still in progress");
    }

    @Test
    void objectStreamIsClosedWhenIncludeS3ObjectFails() throws Exception {
        AWS2S3Endpoint endpoint = context.getEndpoint(s3Uri(""), AWS2S3Endpoint.class);
        AtomicBoolean closed = new AtomicBoolean();
        InputStream content = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        AWS2S3Consumer consumer = new AWS2S3Consumer(endpoint, exchange -> {
        }) {
            @Override
            protected S3Client getAmazonS3Client() {
                return oneObject("a.txt", content);
            }

            @Override
            protected boolean includeS3Object(ResponseInputStream<GetObjectResponse> s3Object) {
                throw new IllegalStateException("cannot decide");
            }
        };
        // not started, but the exchange factory of the consumer must be ready
        consumer.build();

        assertEquals(0, consumer.poll());

        assertTrue(closed.get(), "the object stream of a.txt was not closed");
        assertFalse(endpoint.getInProgressRepository().contains("a.txt"), "a.txt is still in progress");
    }

    /**
     * A client whose listing has the one object, and whose get returns the content.
     */
    private static S3Client oneObject(String key, InputStream content) {
        return new S3Client() {
            @Override
            public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
                return ListObjectsV2Response.builder().contents(S3Object.builder().key(key).build())
                        .isTruncated(false).build();
            }

            @Override
            public <T> T getObject(GetObjectRequest request, ResponseTransformer<GetObjectResponse, T> transformer) {
                @SuppressWarnings("unchecked")
                T answer = (T) new ResponseInputStream<>(GetObjectResponse.builder().eTag("tag").build(), content);
                return answer;
            }

            @Override
            public String serviceName() {
                return SERVICE_NAME;
            }

            @Override
            public void close() {
                // noop
            }
        };
    }
}
