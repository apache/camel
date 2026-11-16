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
package org.apache.camel.component.alibaba.ots;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.alicloud.openservices.tablestore.SyncClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AlibabaOtsEndpointTest {

    @Test
    void initClientReturnsCachedInstance() throws Exception {
        AlibabaOtsEndpoint endpoint = new AlibabaOtsEndpoint();
        SyncClient client = mock(SyncClient.class);
        endpoint.setOtsClient(client);

        SyncClient first = endpoint.initClient();
        SyncClient second = endpoint.initClient();

        assertThat(first).isSameAs(second).isSameAs(client);
    }

    @Test
    void doStopSkipsShutdownForAutowiredClient() throws Exception {
        AlibabaOtsEndpoint endpoint = new AlibabaOtsEndpoint();
        SyncClient client = mock(SyncClient.class);
        endpoint.setOtsClient(client);

        endpoint.doStop();

        verify(client, never()).shutdown();
    }

    @Test
    void doStopShutsDownOwnedClient() throws Exception {
        AlibabaOtsEndpoint endpoint = new AlibabaOtsEndpoint();
        SyncClient client = mock(SyncClient.class);

        var clientField = AlibabaOtsEndpoint.class.getDeclaredField("otsClient");
        clientField.setAccessible(true);
        clientField.set(endpoint, client);

        var autowiredField = AlibabaOtsEndpoint.class.getDeclaredField("autowiredOtsClient");
        autowiredField.setAccessible(true);
        autowiredField.setBoolean(endpoint, false);

        endpoint.doStop();

        verify(client).shutdown();
        assertThat(endpoint.getOtsClient()).isNull();
    }

    @Test
    void concurrentInitClientCreatesOneClient() throws Exception {
        int callers = 8;
        Set<Thread> creators = ConcurrentHashMap.newKeySet();
        List<SyncClient> created = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        AlibabaOtsEndpoint endpoint = new AlibabaOtsEndpoint() {
            @Override
            SyncClient createClient() {
                creators.add(Thread.currentThread());
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                SyncClient client = mock(SyncClient.class);
                created.add(client);
                return client;
            }
        };

        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<SyncClient>> results = new ArrayList<>();
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

            List<SyncClient> clients = new ArrayList<>();
            for (Future<SyncClient> result : results) {
                clients.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(created).hasSize(1);
            assertThat(clients).hasSize(callers).allSatisfy(client -> assertThat(client).isSameAs(created.get(0)));
            assertThat(endpoint.getOtsClient()).isSameAs(created.get(0));

            endpoint.doStop();
            verify(created.get(0)).shutdown();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // InternalClient rejects it in its first checks, as it does not start with http:// or https://
            "my-instance.cn-hangzhou.ots.aliyuncs.com",
            // OTSUri rejects it after these checks, as it has a path
            "https://my-instance.cn-hangzhou.ots.aliyuncs.com/path" })
    void failedClientCreationLeavesNoSdkThread(String endpointValue) {
        Set<Thread> threadsBefore = sdkThreads();
        AlibabaOtsEndpoint endpoint = new AlibabaOtsEndpoint();
        endpoint.setEndpoint(endpointValue);
        endpoint.setInstanceName("my-instance");
        endpoint.setAccessKey("my-access-key");
        endpoint.setSecretKey("my-secret-key");

        // the endpoint keeps no client when the creation fails, so each message creates one again
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(endpoint::initClient).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(endpoint.getOtsClient()).isNull();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(sdkThreads()).isSubsetOf(threadsBefore));
    }

    private static boolean waitsForLockOf(Thread thread, Set<Thread> owners) {
        ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(thread.threadId());
        return info != null && owners.stream().anyMatch(owner -> owner.threadId() == info.getLockOwnerId());
    }

    /**
     * The live threads of the Tablestore SDK: they run code of the SDK, or of the HTTP client that the SDK shades.
     */
    private static Set<Thread> sdkThreads() {
        Set<Thread> threads = new HashSet<>();
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            if (isSdkClass(entry.getKey().getClass().getName())
                    || Arrays.stream(entry.getValue()).anyMatch(frame -> isSdkClass(frame.getClassName()))) {
                threads.add(entry.getKey());
            }
        }
        return threads;
    }

    private static boolean isSdkClass(String className) {
        return className.startsWith("com.alicloud.openservices.tablestore.") || className.startsWith("com.aliyun.ots.");
    }
}
