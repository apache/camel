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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer gets each listed object right before its exchange is routed, with the S3 client that Camel builds and an
 * S3 fake over HTTP: the objects of a poll are not all in memory, or all holding a connection of the client, while the
 * first exchanges are routed. With ignoreBody=true the consumer sends a HEAD request instead of a GET, so the content
 * of the object is not downloaded.
 * <p>
 * The scheduler of the consumer is not started: the tests call {@code poll()} themselves.
 */
class AWS2S3ConsumerGetEachObjectTest extends CamelTestSupport {

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

    private String s3Uri(String options) {
        return "aws2-s3://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&overrideEndpoint=true&forcePathStyle=true"
               + "&uriEndpointOverride=" + s3.endpoint() + "&startScheduler=false" + options;
    }

    private AWS2S3Consumer startRoute(String options) throws Exception {
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
    void eachObjectIsGotRightBeforeItsExchangeIsRouted() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        List<Integer> getsWhenRouted = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(s3Uri("")).routeId("s3")
                        .process(e -> getsWhenRouted.add(s3.gets()))
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
        AWS2S3Consumer consumer = (AWS2S3Consumer) context.getRoute("s3").getConsumer();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b", "c");

        assertEquals(3, consumer.poll());

        mock.assertIsSatisfied();
        // before, the three objects were got (and read into the body) before the first exchange was routed
        assertEquals(List.of(1, 2, 3), getsWhenRouted);
    }

    @Test
    void moreObjectsInAPollThanConnectionsOfTheClientWithIncludeBodyFalse() throws Exception {
        // the HTTP client of the S3 client that Camel builds has 50 connections: before, the consumer got every object
        // of the poll first, the get of the 51st object waited for a connection that none of the open object streams
        // gave back, and after 60 seconds the AWS SDK aborted the streams that had not been read yet
        int objects = 60;
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < objects; i++) {
            String content = "content" + i;
            s3.put(String.format("k%02d.txt", i), content);
            expected.add(content);
        }
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(expected);
        AWS2S3Consumer consumer = startRoute("&includeBody=false&maxMessagesPerPoll=" + objects);
        RecordingExceptionHandler handler = new RecordingExceptionHandler();
        consumer.setExceptionHandler(handler);

        assertEquals(objects, consumer.poll());

        mock.assertIsSatisfied();
        assertTrue(handler.messages.isEmpty(), "exception handler was called: " + handler.messages);
    }

    @Test
    void batchPropertiesCountTheListedObjects() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        // deleted after the listing has returned it: it is known to be gone only when it is got
        s3.removeWhenListed("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "c");
        AWS2S3Consumer consumer = startRoute("");

        // the poll counts the objects it routed
        assertEquals(2, consumer.poll());

        mock.assertIsSatisfied();
        // the batch properties count the listed objects: the index of c.txt is 2, and the size is 3
        Exchange a = mock.getReceivedExchanges().get(0);
        Exchange c = mock.getReceivedExchanges().get(1);
        assertEquals(0, a.getProperty(Exchange.BATCH_INDEX, Integer.class));
        assertEquals(3, a.getProperty(Exchange.BATCH_SIZE, Integer.class));
        assertFalse(a.getProperty(Exchange.BATCH_COMPLETE, Boolean.class));
        assertEquals(2, c.getProperty(Exchange.BATCH_INDEX, Integer.class));
        assertEquals(3, c.getProperty(Exchange.BATCH_SIZE, Integer.class));
        assertTrue(c.getProperty(Exchange.BATCH_COMPLETE, Boolean.class));
    }

    @Test
    void ignoreBodySendsAHeadRequestForEachObject() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "bb");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        AWS2S3Consumer consumer = startRoute("&ignoreBody=true");

        assertEquals(2, consumer.poll());

        mock.assertIsSatisfied();
        // the content of the objects was not downloaded
        assertEquals(0, s3.gets());
        assertEquals(1, s3.heads("a.txt"));
        assertEquals(1, s3.heads("b.txt"));
        Message b = mock.getReceivedExchanges().get(1).getMessage();
        assertNull(b.getBody());
        assertEquals("b.txt", b.getHeader(AWS2S3Constants.KEY));
        assertEquals("bkt", b.getHeader(AWS2S3Constants.BUCKET_NAME));
        assertEquals("\"0123456789abcdef0123456789abcdef\"", b.getHeader(AWS2S3Constants.E_TAG));
        assertEquals(2L, b.getHeader(AWS2S3Constants.CONTENT_LENGTH));
        assertEquals("text/plain", b.getHeader(AWS2S3Constants.CONTENT_TYPE));
        assertNotNull(b.getHeader(AWS2S3Constants.LAST_MODIFIED));
        assertNotNull(b.getHeader(AWS2S3Constants.MESSAGE_TIMESTAMP));
    }

    @Test
    void ignoreBodyWithFileNameSendsAHeadRequest() throws Exception {
        s3.put("a.txt", "a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(AWS2S3Constants.KEY, "a.txt");
        mock.expectedHeaderReceived(AWS2S3Constants.CONTENT_LENGTH, 1L);
        AWS2S3Consumer consumer = startRoute("&fileName=a.txt&ignoreBody=true");

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(0, s3.gets());
        assertEquals(1, s3.heads("a.txt"));
        assertNull(mock.getReceivedExchanges().get(0).getMessage().getBody());
    }

    @Test
    void ignoreBodySkipsAnObjectThatCannotBeHeaded() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.put("c.txt", "c");
        s3.put("d.txt", "d");
        // b.txt no longer exists when it is headed, and the head of c.txt is refused
        s3.removeWhenListed("b.txt");
        s3.refuseGets("c.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderValuesReceivedInAnyOrder(AWS2S3Constants.KEY, "a.txt", "d.txt");
        AWS2S3Consumer consumer = startRoute("&ignoreBody=true");
        RecordingExceptionHandler handler = new RecordingExceptionHandler();
        consumer.setExceptionHandler(handler);

        assertEquals(2, consumer.poll());

        mock.assertIsSatisfied();
        // as with a get: the object that is gone is not an error, the refused one is given to the exception handler
        assertEquals(1, handler.exceptions.size());
        S3Exception refused = assertInstanceOf(S3Exception.class, handler.exceptions.get(0));
        assertEquals(403, refused.statusCode());
        assertTrue(handler.messages.get(0).contains("c.txt"), handler.messages.get(0));
        assertFalse(consumer.getEndpoint().getInProgressRepository().contains("b.txt"), "b.txt is still in progress");
        assertFalse(consumer.getEndpoint().getInProgressRepository().contains("c.txt"), "c.txt is still in progress");
    }

    @Test
    void ignoreBodyPollFailsWhenNoneOfTheListedObjectsCanBeHeaded() throws Exception {
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        s3.refuseGets("a.txt");
        s3.refuseGets("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        AWS2S3Consumer consumer = startRoute("&ignoreBody=true");

        S3Exception e = assertThrows(S3Exception.class, consumer::poll);

        assertEquals(403, e.statusCode());
        mock.assertIsSatisfied();
        assertEquals(1, s3.refusedGets("a.txt"));
        assertEquals(1, s3.refusedGets("b.txt"));
        assertEquals(0, s3.gets());
    }

    /**
     * Records what the consumer gives to its exception handler.
     */
    private static final class RecordingExceptionHandler implements ExceptionHandler {
        final List<String> messages = new CopyOnWriteArrayList<>();
        final List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        @Override
        public void handleException(Throwable exception) {
            handleException(null, null, exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(message, null, exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            messages.add(String.valueOf(message));
            exceptions.add(exception);
        }
    }
}
