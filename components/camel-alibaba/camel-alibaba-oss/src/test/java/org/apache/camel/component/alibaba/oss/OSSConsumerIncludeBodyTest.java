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
package org.apache.camel.component.alibaba.oss;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.oss.constants.OSSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * By default the consumer reads the content of an object into a byte array. With includeBody=false the body is the
 * stream of the content, which the consumer does not read, and which is closed when the exchange is done, also when it
 * fails, or when the exchange is not processed as the consumer stops.
 */
class OSSConsumerIncludeBodyTest extends CamelTestSupport {

    // the content of the objects of the fake bucket, by key
    private final Map<String, byte[]> objects = new ConcurrentSkipListMap<>();
    // the streams the fake client gave for the gets of each object
    private final Map<String, List<TrackingInputStream>> streams = new ConcurrentHashMap<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("ossClient", ossClient());
        return context;
    }

    @Test
    void contentIsReadIntoBytesByDefault() throws Exception {
        objects.put("a.bin", new byte[] { (byte) 0xff, 0x00, 0x41 });
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10&deleteAfterRead=true")
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        Object body = mock.getExchanges().get(0).getIn().getBody();
        assertThat(body).isInstanceOf(byte[].class).isEqualTo(new byte[] { (byte) 0xff, 0x00, 0x41 });
        assertThat(stream("a.bin").isClosed()).as("the stream is closed once it is read").isTrue();
    }

    @Test
    void contentIsTheStreamWithoutIncludeBody() throws Exception {
        objects.put("a.bin", new byte[] { (byte) 0xff, 0x00, 0x41 });
        List<Object> bodies = new CopyOnWriteArrayList<>();
        List<Integer> readBeforeRoute = new CopyOnWriteArrayList<>();
        List<Boolean> closedInRoute = new CopyOnWriteArrayList<>();
        List<byte[]> contents = new CopyOnWriteArrayList<>();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(OSSHeaders.OBJECT_KEY, "a.bin");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10&deleteAfterRead=true"
                     + "&includeBody=false")
                        .streamCache(false)
                        .process(e -> {
                            InputStream body = e.getIn().getBody(InputStream.class);
                            bodies.add(e.getIn().getBody());
                            TrackingInputStream stream = stream("a.bin");
                            readBeforeRoute.add(stream.bytesRead());
                            closedInRoute.add(stream.isClosed());
                            contents.add(body.readAllBytes());
                        })
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        assertThat(bodies).hasSize(1);
        assertThat(bodies.get(0)).as("the body is the stream of the object").isSameAs(stream("a.bin"));
        assertThat(readBeforeRoute).as("the consumer did not read the stream").containsExactly(0);
        assertThat(closedInRoute).as("the stream is open while the exchange is processed").containsExactly(false);
        assertThat(contents.get(0)).isEqualTo(new byte[] { (byte) 0xff, 0x00, 0x41 });
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(stream("a.bin").isClosed()).as("the stream is closed when the exchange is done").isTrue());
    }

    @Test
    void streamIsClosedWhenTheExchangeFails() throws Exception {
        objects.put("a.bin", new byte[] { 0x41 });
        AtomicBoolean failed = new AtomicBoolean();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10&deleteAfterRead=true"
                     + "&includeBody=false")
                        .streamCache(false)
                        .process(e -> {
                            if (failed.compareAndSet(false, true)) {
                                throw new IllegalStateException("the route fails");
                            }
                        });
            }
        });

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(failed).isTrue();
            assertThat(stream("a.bin").isClosed()).as("the stream is closed when the exchange failed").isTrue();
        });
    }

    @Test
    void streamsOfTheExchangesThatAreNotProcessedAreClosed() throws Exception {
        objects.put("a.bin", new byte[] { 0x41 });
        objects.put("b.bin", new byte[] { 0x42 });
        objects.put("c.bin", new byte[] { 0x43 });
        AtomicBoolean first = new AtomicBoolean(true);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10&deleteAfterRead=true"
                     + "&includeBody=false").routeId("oss")
                        .streamCache(false)
                        .process(e -> {
                            if (first.compareAndSet(true, false)) {
                                // stop the route while the first exchange of the batch is processed, and wait until
                                // the consumer does not process the rest of the batch
                                OSSConsumer consumer = (OSSConsumer) context.getRoute("oss").getConsumer();
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        context.getRouteController().stopRoute("oss");
                                    } catch (Exception ex) {
                                        throw new RuntimeCamelException(ex);
                                    }
                                });
                                await().atMost(20, TimeUnit.SECONDS).until(() -> !consumer.isBatchAllowed());
                            }
                        })
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> context.getRouteController().getRouteStatus("oss") == ServiceStatus.Stopped);

        assertThat(streams.keySet()).containsExactlyInAnyOrder("a.bin", "b.bin", "c.bin");
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(streams.values()).allSatisfy(list -> assertThat(list)
                        .as("a stream of the batch is left open").allMatch(TrackingInputStream::isClosed)));
    }

    private TrackingInputStream stream(String key) {
        List<TrackingInputStream> list = streams.get(key);
        assertThat(list).as("the object %s was not got", key).isNotEmpty();
        return list.get(0);
    }

    private OSSClient ossClient() {
        OSSClient client = Mockito.mock(OSSClient.class);
        Mockito.when(client.listObjectsV2(Mockito.any(ListObjectsV2Request.class))).thenAnswer(i -> {
            List<ObjectSummary> contents = objects.keySet().stream().map(key -> {
                ObjectSummary summary = Mockito.mock(ObjectSummary.class);
                Mockito.when(summary.key()).thenReturn(key);
                return summary;
            }).toList();
            ListObjectsV2Result result = Mockito.mock(ListObjectsV2Result.class);
            Mockito.when(result.contents()).thenReturn(contents);
            return result;
        });
        Mockito.when(client.getObject(Mockito.any(GetObjectRequest.class))).thenAnswer(i -> {
            String key = i.<GetObjectRequest> getArgument(0).key();
            TrackingInputStream stream = new TrackingInputStream(objects.get(key));
            streams.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(stream);
            GetObjectResult result = Mockito.mock(GetObjectResult.class);
            Mockito.when(result.body()).thenReturn(stream);
            Mockito.when(result.contentLength()).thenReturn((long) objects.get(key).length);
            return result;
        });
        Mockito.when(client.deleteObject(Mockito.any(DeleteObjectRequest.class))).thenAnswer(i -> {
            objects.remove(i.<DeleteObjectRequest> getArgument(0).key());
            return null;
        });
        return client;
    }

    /**
     * The stream of the content of an object, which records how much of it was read and whether it was closed.
     */
    static final class TrackingInputStream extends ByteArrayInputStream {

        private volatile boolean closed;

        TrackingInputStream(byte[] content) {
            super(content);
        }

        int bytesRead() {
            return pos;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
