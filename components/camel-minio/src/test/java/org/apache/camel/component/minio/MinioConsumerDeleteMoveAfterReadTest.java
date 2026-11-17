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
package org.apache.camel.component.minio;

import java.util.List;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.simple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With deleteAfterRead or moveAfterRead the consumer deletes or moves the object it consumed once the exchange is done,
 * whatever the route did with the CamelMinioObjectName and CamelMinioBucketName headers in the meantime: a route may
 * set the object name to write a copy of the object under another name (the producer takes the name from that header),
 * or remove the Camel headers before it calls another system.
 * <p>
 * The scheduler of the consumer is not started: the tests call {@code poll()} themselves. The routes are synchronous,
 * so the delete or the move is done when {@code poll()} returns.
 */
class MinioConsumerDeleteMoveAfterReadTest extends CamelTestSupport {

    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("inbox");
        s3.put("todo/a.txt", "hello");
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
    void deleteAfterReadDeletesTheConsumedObjectWhenTheRouteRenamesIt() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        MinioConsumer consumer = startRoute("", this::writeCopy);

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("todo/a.txt"), s3.deletes());
        assertTrue(s3.contains("out/todo/a.txt"), "the copy the route wrote is kept");
        assertEquals(0, consumer.poll(), "the next poll does not consume the object again");
    }

    @Test
    void deleteAfterReadDeletesTheConsumedObjectWhenTheRouteRemovesTheHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        MinioConsumer consumer = startRoute("", route -> route.removeHeaders("CamelMinio*"));

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("todo/a.txt"), s3.deletes());
        assertEquals(0, consumer.poll(), "the next poll does not consume the object again");
    }

    @Test
    void moveAfterReadMovesTheConsumedObjectWhenTheRouteRenamesIt() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        MinioConsumer consumer = startRoute("&moveAfterRead=true&destinationBucketName=archive", this::writeCopy);

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("inbox/todo/a.txt -> archive/todo/a.txt"), s3.copies());
        assertEquals(List.of("todo/a.txt"), s3.deletes());
        assertTrue(s3.contains("out/todo/a.txt"), "the copy the route wrote is kept");
        assertEquals(0, consumer.poll(), "the next poll does not consume the object again");
    }

    @Test
    void deleteAfterReadDeletesTheConsumedObject() throws Exception {
        // control: the route keeps the headers the consumer set
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MinioConstants.OBJECT_NAME, "todo/a.txt");
        mock.expectedHeaderReceived(MinioConstants.BUCKET_NAME, "inbox");
        MinioConsumer consumer = startRoute("", route -> {
        });

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("todo/a.txt"), s3.deletes());
        assertEquals(0, consumer.poll());
    }

    @Test
    void moveAfterReadMovesTheConsumedObject() throws Exception {
        // control: the destination name is the configured one
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        MinioConsumer consumer = startRoute(
                "&moveAfterRead=true&destinationBucketName=archive&destinationObjectName=done.txt", route -> {
                });

        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of("inbox/todo/a.txt -> archive/done.txt"), s3.copies());
        assertEquals(List.of("todo/a.txt"), s3.deletes());
    }

    @Test
    void objectIsKeptWithoutDeleteAfterRead() throws Exception {
        // control: without deleteAfterRead the object stays, and is consumed again
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        MinioConsumer consumer = startRoute("&deleteAfterRead=false", route -> {
        });

        assertEquals(1, consumer.poll());
        assertEquals(1, consumer.poll());

        mock.assertIsSatisfied();
        assertEquals(List.of(), s3.deletes());
        assertEquals(List.of(), s3.copies());
    }

    /**
     * Writes a copy of the object to out/ in the same bucket: the producer takes the name of the object from the
     * header.
     */
    private void writeCopy(RouteDefinition route) {
        route.setHeader(MinioConstants.OBJECT_NAME, simple("out/${header." + MinioConstants.OBJECT_NAME + "}"))
                .to("minio://inbox?" + credentials());
    }

    private MinioConsumer startRoute(String options, Consumer<RouteDefinition> steps) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from("minio://inbox?" + credentials() + "&prefix=todo/&startScheduler=false" + options)
                        .routeId("minio");
                steps.accept(route);
                route.to("mock:result");
            }
        });
        return (MinioConsumer) context.getRoute("minio").getConsumer();
    }

    private String credentials() {
        return "accessKey=xxx&secretKey=yyy&region=us-east-1&endpoint=" + s3.endpoint();
    }
}
