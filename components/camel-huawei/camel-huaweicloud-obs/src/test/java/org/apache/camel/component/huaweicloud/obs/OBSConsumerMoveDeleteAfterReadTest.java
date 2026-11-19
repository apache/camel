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
package org.apache.camel.component.huaweicloud.obs;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.BucketMetadataInfoRequest;
import com.obs.services.model.BucketMetadataInfoResult;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.obs.constants.OBSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With moveAfterRead and deleteAfterRead, the consumer moves and deletes the object it consumed, whatever the route did
 * with the bucket name and object key headers.
 */
class OBSConsumerMoveDeleteAfterReadTest extends CamelTestSupport {

    private static final String BUCKET = "bucket";
    private static final String DESTINATION = "processed";

    private final Set<String> objects = new ConcurrentSkipListSet<>();
    // the copies and deletes the client got, as bucket/key or bucket/key->bucket/key
    private final List<String> copies = new CopyOnWriteArrayList<>();
    private final List<String> deletes = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("obsClient", obsClient());
        return context;
    }

    @Test
    void movesAndDeletesTheConsumedObjectWhenTheRouteSetTheHeaders() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");
        // consumed once: deleted, so the later polls must not route it again
        mock.setAssertPeriod(500);

        // a route that changes the bucket and key headers (for example for a later step of the route)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(obsUri())
                        .convertBodyTo(String.class)
                        .setHeader(OBSHeaders.BUCKET_NAME, constant("archive"))
                        .setHeader(OBSHeaders.OBJECT_KEY, constant("copy-of-a.txt"))
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !deletes.isEmpty());
        assertEquals(List.of("bucket/a.txt->processed/a.txt"), copies);
        assertEquals(List.of("bucket/a.txt"), deletes);
        assertTrue(objects.isEmpty());
    }

    @Test
    void movesAndDeletesTheConsumedObjectWhenTheRouteRemovedTheHeaders() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");
        // consumed once: deleted, so the later polls must not route it again
        mock.setAssertPeriod(500);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(obsUri())
                        .convertBodyTo(String.class)
                        .removeHeaders("*")
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !deletes.isEmpty());
        assertEquals(List.of("bucket/a.txt->processed/a.txt"), copies);
        assertEquals(List.of("bucket/a.txt"), deletes);
        assertTrue(objects.isEmpty());
    }

    private String obsUri() {
        return "hwcloud-obs:?bucketName=" + BUCKET + "&obsClient=#obsClient&initialDelay=0&delay=10"
               + "&moveAfterRead=true&destinationBucket=" + DESTINATION + "&deleteAfterRead=true";
    }

    private ObsClient obsClient() {
        ObsClient client = Mockito.mock(ObsClient.class);
        // the source and the destination bucket exist, in the same location
        BucketMetadataInfoResult metadata = Mockito.mock(BucketMetadataInfoResult.class);
        Mockito.when(metadata.getLocation()).thenReturn("cn-north-4");
        Mockito.when(client.getBucketMetadata(Mockito.any(BucketMetadataInfoRequest.class))).thenReturn(metadata);
        Mockito.when(client.listObjects(Mockito.any(ListObjectsRequest.class))).thenAnswer(i -> {
            // listed objects carry no content type, so the consumer gets each of them
            List<ObsObject> listed = objects.stream().map(key -> {
                ObsObject summary = new ObsObject();
                summary.setBucketName(BUCKET);
                summary.setObjectKey(key);
                return summary;
            }).toList();
            ObjectListing listing = Mockito.mock(ObjectListing.class);
            Mockito.when(listing.getObjects()).thenReturn(listed);
            return listing;
        });
        Mockito.when(client.getObject(Mockito.eq(BUCKET), Mockito.anyString())).thenAnswer(i -> {
            String key = i.getArgument(1);
            if (!objects.contains(key)) {
                ObsException e = new ObsException("NoSuchKey");
                e.setResponseCode(404);
                throw e;
            }
            ObsObject object = new ObsObject();
            object.setBucketName(BUCKET);
            object.setObjectKey(key);
            ObjectMetadata metadata1 = new ObjectMetadata();
            metadata1.setContentType("text/plain");
            object.setMetadata(metadata1);
            object.setObjectContent(new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));
            return object;
        });
        Mockito.when(client.copyObject(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(i -> {
                    copies.add(i.getArgument(0) + "/" + i.getArgument(1) + "->" + i.getArgument(2) + "/" + i.getArgument(3));
                    return null;
                });
        Mockito.when(client.deleteObject(Mockito.anyString(), Mockito.anyString())).thenAnswer(i -> {
            deletes.add(i.getArgument(0) + "/" + i.getArgument(1));
            if (BUCKET.equals(i.getArgument(0))) {
                objects.remove(i.<String> getArgument(1));
            }
            return null;
        });
        return client;
    }
}
