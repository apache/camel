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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A listed object that cannot be got (access denied, an archived object that is not restored) must not stop the
 * consumer: the other listed objects are consumed, the next page of the listing is read, and the poll fails only when
 * none of the listed objects could be got. The fake bucket lists its keys in order, a page of maxKeys keys at a time,
 * with the last key of a page as continuation token, and answers the get of a forbidden object with 403.
 */
class OSSConsumerObjectThatCannotBeGotTest extends CamelTestSupport {

    private final Set<String> objects = new ConcurrentSkipListSet<>();
    private final Set<String> forbidden = ConcurrentHashMap.newKeySet();
    private final AtomicInteger failedPolls = new AtomicInteger();
    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("ossClient", ossClient());
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // noop
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPolls.incrementAndGet();
                return false;
            }
        });
        context.getRegistry().bind("recordErrors", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(String.valueOf(exception.getMessage()));
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(message);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(message);
            }
        });
        return context;
    }

    @Test
    void objectThatCannotBeGotDoesNotBlockTheOtherObjects() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        forbidden.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a.txt", "c.txt");

        addConsumerRoute("&deleteAfterRead=true&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        assertThat(objects).as("a.txt and c.txt are consumed and deleted, b.txt stays").containsExactly("b.txt");
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(handled).as("the skipped object is reported").anyMatch(m -> m.contains("b.txt")));
    }

    @Test
    void pageOfObjectsThatCannotBeGotDoesNotBlockTheNextPage() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        objects.add("c.txt");
        forbidden.add("a.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("b.txt", "c.txt");

        // one object per page: the first page holds only a.txt
        addConsumerRoute("&deleteAfterRead=true&maxMessagesPerPoll=1&pollStrategy=#countPolls");

        mock.assertIsSatisfied();
        assertThat(objects).as("b.txt and c.txt are consumed and deleted, a.txt stays").containsExactly("a.txt");
    }

    @Test
    void pollFailsWhenNoListedObjectCanBeGot() throws Exception {
        objects.add("a.txt");
        objects.add("b.txt");
        forbidden.add("a.txt");
        forbidden.add("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        addConsumerRoute("&deleteAfterRead=true&pollStrategy=#countPolls");

        // as for a failed listing (expired credentials, no permission to get objects): the poll fails, so the error
        // handling and the backoff of the scheduled consumer apply
        await().atMost(20, TimeUnit.SECONDS).until(() -> failedPolls.get() >= 2);
        mock.assertIsSatisfied();
    }

    private void addConsumerRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&initialDelay=0&delay=10" + options)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
    }

    private OSSClient ossClient() {
        OSSClient client = Mockito.mock(OSSClient.class);
        Mockito.when(client.listObjectsV2(Mockito.any(ListObjectsV2Request.class))).thenAnswer(i -> {
            ListObjectsV2Request request = i.getArgument(0);
            String after = request.continuationToken();
            long maxKeys = request.maxKeys() != null ? request.maxKeys() : 1000;
            List<String> remaining = objects.stream().filter(k -> after == null || k.compareTo(after) > 0).toList();
            List<String> page = remaining.stream().limit(maxKeys).toList();
            boolean truncated = remaining.size() > page.size();
            List<ObjectSummary> contents = page.stream().map(key -> {
                ObjectSummary summary = Mockito.mock(ObjectSummary.class);
                Mockito.when(summary.key()).thenReturn(key);
                return summary;
            }).toList();
            ListObjectsV2Result result = Mockito.mock(ListObjectsV2Result.class);
            Mockito.when(result.contents()).thenReturn(contents);
            Mockito.when(result.isTruncated()).thenReturn(truncated);
            Mockito.when(result.nextContinuationToken()).thenReturn(truncated ? page.get(page.size() - 1) : null);
            return result;
        });
        Mockito.when(client.getObject(Mockito.any(GetObjectRequest.class))).thenAnswer(i -> {
            String key = i.<GetObjectRequest> getArgument(0).key();
            if (!objects.contains(key)) {
                throw new OperationException("GetObject", ServiceException.newBuilder().statusCode(404).build());
            }
            if (forbidden.contains(key)) {
                throw new OperationException("GetObject", ServiceException.newBuilder().statusCode(403).build());
            }
            GetObjectResult result = Mockito.mock(GetObjectResult.class);
            Mockito.when(result.body()).thenReturn(new ByteArrayInputStream(key.getBytes(StandardCharsets.UTF_8)));
            return result;
        });
        Mockito.when(client.deleteObject(Mockito.any(DeleteObjectRequest.class))).thenAnswer(i -> {
            objects.remove(i.<DeleteObjectRequest> getArgument(0).key());
            return null;
        });
        return client;
    }
}
