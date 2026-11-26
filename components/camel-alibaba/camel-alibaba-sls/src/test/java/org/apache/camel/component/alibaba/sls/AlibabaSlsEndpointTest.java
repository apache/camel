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
package org.apache.camel.component.alibaba.sls;

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

import com.aliyun.sls20201230.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

class AlibabaSlsEndpointTest {

    @Test
    void initClientReturnsCachedInstance() throws Exception {
        AlibabaSlsEndpoint endpoint = new AlibabaSlsEndpoint();
        Client client = mock(Client.class);
        endpoint.setSlsClient(client);

        Client first = endpoint.initClient();
        Client second = endpoint.initClient();

        assertThat(first).isSameAs(second).isSameAs(client);
    }

    @Test
    void doStopKeepsAutowiredClient() throws Exception {
        AlibabaSlsEndpoint endpoint = new AlibabaSlsEndpoint();
        Client client = mock(Client.class);
        endpoint.setSlsClient(client);

        endpoint.doStop();

        assertThat(endpoint.getSlsClient()).isSameAs(client);
    }

    @Test
    void doStopClearsOwnedClient() throws Exception {
        AlibabaSlsEndpoint endpoint = new AlibabaSlsEndpoint();
        Client client = mock(Client.class);

        var clientField = AlibabaSlsEndpoint.class.getDeclaredField("slsClient");
        clientField.setAccessible(true);
        clientField.set(endpoint, client);

        var autowiredField = AlibabaSlsEndpoint.class.getDeclaredField("autowiredSlsClient");
        autowiredField.setAccessible(true);
        autowiredField.setBoolean(endpoint, false);

        endpoint.doStop();

        assertThat(endpoint.getSlsClient()).isNull();
    }

    @Test
    void concurrentInitClientCreatesOneClient() throws Exception {
        int callers = 8;
        Set<Thread> creators = ConcurrentHashMap.newKeySet();
        List<Client> created = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        AlibabaSlsEndpoint endpoint = new AlibabaSlsEndpoint() {
            @Override
            Client createClient() {
                creators.add(Thread.currentThread());
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                Client client = mock(Client.class);
                created.add(client);
                return client;
            }
        };

        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Client>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    threads.add(Thread.currentThread());
                    return endpoint.initClient();
                }));
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> threads.size() == callers
                    && threads.stream().allMatch(thread -> creators.contains(thread) || waitsForLockOf(thread, creators)));
            release.countDown();

            List<Client> clients = new ArrayList<>();
            for (Future<Client> result : results) {
                clients.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(created).hasSize(1);
            assertThat(clients).hasSize(callers).allSatisfy(client -> assertThat(client).isSameAs(created.get(0)));
            assertThat(endpoint.getSlsClient()).isSameAs(created.get(0));
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
