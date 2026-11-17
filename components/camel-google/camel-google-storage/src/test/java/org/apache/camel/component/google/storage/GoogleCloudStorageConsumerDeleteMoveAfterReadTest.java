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
package org.apache.camel.component.google.storage;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.simple;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * With deleteAfterRead or moveAfterRead the consumer deletes or moves the object it consumed once the exchange is done,
 * whatever the route did with the CamelGoogleCloudStorageObjectName and CamelGoogleCloudStorageBucketName headers in
 * the meantime: a route may set the object name to write a copy of the object under another name (the producer takes
 * the name from that header), or remove the Camel headers before it calls another system.
 * <p>
 * The scheduler of the consumer is not started: the tests call {@code poll()} themselves. The routes are synchronous,
 * so the delete or the move is done when {@code poll()} returns.
 */
class GoogleCloudStorageConsumerDeleteMoveAfterReadTest extends CamelTestSupport {

    private Storage storage;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        storage = LocalStorageHelper.getOptions().getService();
        storage.create(BucketInfo.of("in"));
        storage.create(BucketInfo.of("archive"));
        storage.create(BlobInfo.newBuilder("in", "todo/a.txt").build(), "hello".getBytes(StandardCharsets.UTF_8));
        context.getComponent("google-storage", GoogleCloudStorageComponent.class).getConfiguration()
                .setStorageClient(storage);
        return context;
    }

    @Test
    void deleteAfterReadDeletesTheConsumedObjectWhenTheRouteRenamesIt() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        GoogleCloudStorageConsumer consumer = startRoute("", this::writeCopy);

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(exists("in", "todo/a.txt")).as("the consumed object is deleted").isFalse();
        assertThat(exists("in", "out/todo/a.txt")).as("the copy the route wrote is kept").isTrue();
        assertThat(consumer.poll()).as("the next poll does not consume the object again").isZero();
    }

    @Test
    void deleteAfterReadDeletesTheConsumedObjectWhenTheRouteRemovesTheHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        GoogleCloudStorageConsumer consumer
                = startRoute("", route -> route.removeHeaders("CamelGoogleCloudStorage*"));

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(exists("in", "todo/a.txt")).as("the consumed object is deleted").isFalse();
        assertThat(consumer.poll()).as("the next poll does not consume the object again").isZero();
    }

    @Test
    void moveAfterReadMovesTheConsumedObjectWhenTheRouteRenamesIt() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        GoogleCloudStorageConsumer consumer = startRoute("&moveAfterRead=true&destinationBucket=archive", this::writeCopy);

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(exists("archive", "todo/a.txt")).as("the consumed object is copied to the destination bucket").isTrue();
        assertThat(exists("archive", "out/todo/a.txt")).as("the copy the route wrote is not moved").isFalse();
        assertThat(exists("in", "todo/a.txt")).as("the consumed object is deleted").isFalse();
        assertThat(exists("in", "out/todo/a.txt")).as("the copy the route wrote is kept").isTrue();
        assertThat(consumer.poll()).as("the next poll does not consume the object again").isZero();
    }

    @Test
    void deleteAfterReadDeletesTheConsumedObject() throws Exception {
        // control: the route keeps the headers the consumer set
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(GoogleCloudStorageConstants.OBJECT_NAME, "todo/a.txt");
        mock.expectedHeaderReceived(GoogleCloudStorageConstants.BUCKET_NAME, "in");
        GoogleCloudStorageConsumer consumer = startRoute("", route -> {
        });

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(exists("in", "todo/a.txt")).isFalse();
        assertThat(consumer.poll()).isZero();
    }

    @Test
    void moveAfterReadMovesTheConsumedObject() throws Exception {
        // control: the route keeps the headers the consumer set
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        GoogleCloudStorageConsumer consumer = startRoute("&moveAfterRead=true&destinationBucket=archive", route -> {
        });

        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(exists("archive", "todo/a.txt")).isTrue();
        assertThat(exists("in", "todo/a.txt")).isFalse();
    }

    @Test
    void objectIsKeptWithoutDeleteAfterRead() throws Exception {
        // control: without deleteAfterRead the object stays, and is consumed again
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        GoogleCloudStorageConsumer consumer = startRoute("&deleteAfterRead=false", route -> {
        });

        assertThat(consumer.poll()).isEqualTo(1);
        assertThat(consumer.poll()).isEqualTo(1);

        mock.assertIsSatisfied();
        assertThat(exists("in", "todo/a.txt")).isTrue();
    }

    /**
     * Writes a copy of the object to out/ in the same bucket: the producer takes the name of the object from the
     * header.
     */
    private void writeCopy(RouteDefinition route) {
        route.setHeader(GoogleCloudStorageConstants.OBJECT_NAME,
                simple("out/${header." + GoogleCloudStorageConstants.OBJECT_NAME + "}"))
                .to("google-storage://in");
    }

    private GoogleCloudStorageConsumer startRoute(String options, Consumer<RouteDefinition> steps) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from("google-storage://in?prefix=todo/&startScheduler=false" + options)
                        .routeId("gcs");
                steps.accept(route);
                route.to("mock:result");
            }
        });
        return (GoogleCloudStorageConsumer) context.getRoute("gcs").getConsumer();
    }

    private boolean exists(String bucket, String name) {
        Blob blob = storage.get(bucket, name);
        return blob != null && blob.exists();
    }
}
