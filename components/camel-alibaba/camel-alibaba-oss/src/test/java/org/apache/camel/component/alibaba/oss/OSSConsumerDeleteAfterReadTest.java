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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.exceptions.OperationException;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.oss.constants.OSSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * With deleteAfterRead, the consumer deletes the object it consumed, whatever the route did with the bucket and key
 * headers.
 */
class OSSConsumerDeleteAfterReadTest extends CamelTestSupport {

    private final Set<String> objects = new ConcurrentSkipListSet<>();
    // the deletes the bucket got, as bucket/key
    private final List<String> deletes = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("ossClient", ossClient());
        return context;
    }

    @Test
    void deletesTheConsumedObjectWhenTheRouteSetTheHeaders() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");
        // consumed once: deleted, so the later polls must not route it again
        mock.setAssertPeriod(500);

        // as a route does that writes a copy of the object to another bucket with the producer
        addConsumerRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10&deleteAfterRead=true")
                        .convertBodyTo(String.class)
                        .setHeader(OSSHeaders.BUCKET_NAME, constant("archive"))
                        .setHeader(OSSHeaders.OBJECT_KEY, constant("copy-of-a.txt"))
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !deletes.isEmpty());
        assertThat(deletes).containsExactly("bucket/a.txt");
        assertThat(objects).isEmpty();
    }

    @Test
    void deletesTheConsumedObjectWhenTheRouteRemovedTheHeaders() throws Exception {
        objects.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");
        // consumed once: deleted, so the later polls must not route it again
        mock.setAssertPeriod(500);

        addConsumerRoute(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10&deleteAfterRead=true")
                        .convertBodyTo(String.class)
                        .removeHeaders("*")
                        .to("mock:result");
            }
        });

        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !deletes.isEmpty());
        assertThat(deletes).containsExactly("bucket/a.txt");
        assertThat(objects).isEmpty();
    }

    private void addConsumerRoute(RouteBuilder builder) throws Exception {
        context.addRoutes(builder);
    }

    private OSSClient ossClient() {
        OSSClient client = Mockito.mock(OSSClient.class);
        Mockito.when(client.listObjectsV2(Mockito.any(ListObjectsV2Request.class))).thenAnswer(i -> {
            List<ObjectSummary> contents = objects.stream().map(key -> {
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
            if (!objects.contains(key)) {
                throw new OperationException(
                        "GetObject", ServiceException.newBuilder().statusCode(404).build());
            }
            GetObjectResult result = Mockito.mock(GetObjectResult.class);
            Mockito.when(result.body()).thenReturn(new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));
            return result;
        });
        Mockito.when(client.deleteObject(Mockito.any(DeleteObjectRequest.class))).thenAnswer(i -> {
            DeleteObjectRequest request = i.getArgument(0);
            deletes.add(request.bucket() + "/" + request.key());
            if ("bucket".equals(request.bucket())) {
                objects.remove(request.key());
            }
            return null;
        });
        return client;
    }
}
