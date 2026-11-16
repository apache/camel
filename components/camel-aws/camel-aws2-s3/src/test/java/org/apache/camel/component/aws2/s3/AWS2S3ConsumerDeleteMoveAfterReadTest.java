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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;

import static org.apache.camel.builder.Builder.constant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * With deleteAfterRead or moveAfterRead the consumer deletes or moves the object it consumed once the exchange is done,
 * whatever the route did with the CamelAwsS3Key and CamelAwsS3BucketName headers in the meantime: a route may set them
 * to write a copy of the object under another key, or remove the Camel headers before it calls another system.
 * <p>
 * The scheduler of the consumer is not started: the tests call {@code poll()} themselves. The routes are synchronous,
 * so the delete or the move is done when {@code poll()} returns.
 */
class AWS2S3ConsumerDeleteMoveAfterReadTest extends CamelTestSupport {

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

    @Test
    void deleteAfterReadDeletesTheConsumedObjectWhenTheRouteChangesTheKeyHeader() throws Exception {
        s3.put("a.txt", "a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        // for example the key under which the route writes a copy of the object
        AWS2S3Consumer consumer = startRoute("", route -> route.setHeader(AWS2S3Constants.KEY, constant("out/a.txt")));

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("a.txt"), s3.deletes());
        // so the next poll does not consume it again
        assertEquals(0, consumer.poll());
    }

    @Test
    void deleteAfterReadDeletesTheConsumedObjectWhenTheRouteRemovesTheHeaders() throws Exception {
        s3.put("a.txt", "a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        AWS2S3Consumer consumer = startRoute("", route -> route.removeHeaders("CamelAwsS3*"));

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("a.txt"), s3.deletes());
        assertEquals(0, consumer.poll());
    }

    @Test
    void moveAfterReadMovesTheConsumedObjectWhenTheRouteChangesTheHeaders() throws Exception {
        s3.put("a.txt", "a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        AWS2S3Consumer consumer = startRoute("&moveAfterRead=true&destinationBucket=archive",
                route -> route.setHeader(AWS2S3Constants.BUCKET_NAME, constant("other"))
                        .setHeader(AWS2S3Constants.KEY, constant("out/a.txt")));

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("bkt/a.txt -> archive/a.txt"), s3.copies());
        assertEquals(List.of("a.txt"), s3.deletes());
    }

    @Test
    void deleteAfterReadDeletesTheConsumedObject() throws Exception {
        // control: the route keeps the headers the consumer set
        s3.put("a.txt", "a");
        s3.put("b.txt", "b");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a", "b");
        AWS2S3Consumer consumer = startRoute("", route -> {
        });

        assertEquals(2, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("a.txt", "b.txt"), s3.deletes());
        assertEquals(0, consumer.poll());
    }

    @Test
    void moveAfterReadMovesTheConsumedObject() throws Exception {
        // control: the destination key is built from the key of the consumed object
        s3.put("a.txt", "a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        AWS2S3Consumer consumer = startRoute(
                "&moveAfterRead=true&destinationBucket=archive&destinationBucketPrefix=done/&destinationBucketSuffix=.bak",
                route -> {
                });

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("bkt/a.txt -> archive/done/a.txt.bak"), s3.copies());
        assertEquals(List.of("a.txt"), s3.deletes());
    }

    @Test
    void deleteThatCannotBeSentIsReportedToTheExceptionHandler() throws Exception {
        s3.put("a.txt", "a");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a");
        // S3 cannot be reached when the exchange is done: the client fails without an answer from S3
        AWS2S3Consumer consumer = startRoute("", route -> route.process(exchange -> s3.stop()));
        RecordingExceptionHandler handler = new RecordingExceptionHandler();
        consumer.setExceptionHandler(handler);

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("Error occurred during moving or deleting object. This exception is ignored."),
                handler.messages);
        assertInstanceOf(SdkClientException.class, handler.exceptions.get(0));
    }

    private AWS2S3Consumer startRoute(String options, Consumer<RouteDefinition> steps) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route
                        = from(
                        "aws2-s3://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&overrideEndpoint=true"
                                             + "&forcePathStyle=true&uriEndpointOverride=" + s3.endpoint()
                                             + "&startScheduler=false" + options)
                        .routeId("s3");
                steps.accept(route);
                route.convertBodyTo(String.class).to("mock:result");
            }
        });
        return (AWS2S3Consumer) context.getRoute("s3").getConsumer();
    }

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
