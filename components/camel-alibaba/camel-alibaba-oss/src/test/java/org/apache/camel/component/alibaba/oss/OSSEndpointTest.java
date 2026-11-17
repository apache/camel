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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aliyun.sdk.service.oss2.OSSClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OSSEndpointTest {

    @Test
    void initClientReturnsCachedInstance() {
        OSSEndpoint endpoint = new OSSEndpoint();
        endpoint.setAccessKey("ak");
        endpoint.setSecretKey("sk");
        endpoint.setRegion("cn-hangzhou");

        OSSClient first = endpoint.initClient();
        OSSClient second = endpoint.initClient();

        assertThat(first).isSameAs(second);
    }

    @Test
    void doStopSkipsCloseForAutowiredClient() throws Exception {
        OSSEndpoint endpoint = new OSSEndpoint();
        OSSClient client = mock(OSSClient.class);
        endpoint.setOssClient(client);

        endpoint.doStop();

        verify(client, never()).close();
    }

    @Test
    void doStopClosesOwnedClient() throws Exception {
        OSSEndpoint endpoint = new OSSEndpoint();
        OSSClient client = mock(OSSClient.class);

        var clientField = OSSEndpoint.class.getDeclaredField("ossClient");
        clientField.setAccessible(true);
        clientField.set(endpoint, client);

        var autowiredField = OSSEndpoint.class.getDeclaredField("autowiredOssClient");
        autowiredField.setAccessible(true);
        autowiredField.setBoolean(endpoint, false);

        endpoint.doStop();

        verify(client).close();
        assertThat(endpoint.getOssClient()).isNull();
    }

    @Test
    void concurrentInitClientCreatesOneClient() throws Exception {
        int callers = 8;
        Set<Thread> creators = ConcurrentHashMap.newKeySet();
        List<OSSClient> created = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        OSSEndpoint endpoint = new OSSEndpoint() {
            @Override
            OSSClient createClient() {
                creators.add(Thread.currentThread());
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                OSSClient client = mock(OSSClient.class);
                created.add(client);
                return client;
            }
        };

        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<OSSClient>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    threads.add(Thread.currentThread());
                    return endpoint.initClient();
                }));
            }
            // a creation is held until each caller has started one too, or waits for the lock of a caller that has,
            // so the result does not depend on how the threads are scheduled
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads.size() == callers
                    && threads.stream().allMatch(thread -> creators.contains(thread) || waitsForLockOf(thread, creators)));
            release.countDown();

            List<OSSClient> clients = new ArrayList<>();
            for (Future<OSSClient> result : results) {
                clients.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(created).hasSize(1);
            assertThat(clients).hasSize(callers).allSatisfy(client -> assertThat(client).isSameAs(created.get(0)));
            assertThat(endpoint.getOssClient()).isSameAs(created.get(0));

            endpoint.doStop();
            verify(created.get(0)).close();
            assertThat(endpoint.getOssClient()).isNull();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static boolean waitsForLockOf(Thread thread, Set<Thread> owners) {
        ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.threadId());
        return info != null && owners.stream().anyMatch(owner -> owner.threadId() == info.getLockOwnerId());
    }
}
