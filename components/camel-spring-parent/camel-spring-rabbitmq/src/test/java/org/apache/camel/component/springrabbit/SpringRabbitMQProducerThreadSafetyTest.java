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
package org.apache.camel.component.springrabbit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpringRabbitMQProducerThreadSafetyTest {

    @Test
    void concurrentGetInOnlyTemplateMustReturnSameInstance() throws Exception {
        CachingConnectionFactory cf = new CachingConnectionFactory("localhost");
        SpringRabbitMQComponent component = new SpringRabbitMQComponent();
        SpringRabbitMQEndpoint endpoint = new SpringRabbitMQEndpoint(
                "spring-rabbitmq:test", component, "test");
        endpoint.setConnectionFactory(cf);
        SpringRabbitMQProducer producer = new SpringRabbitMQProducer(endpoint);

        int threads = 8;
        CountDownLatch barrier = new CountDownLatch(threads);
        Set<RabbitTemplate> observed = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                barrier.countDown();
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                observed.add(producer.getInOnlyTemplate());
            });
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, observed.size(),
                "concurrent getInOnlyTemplate() must always return the same template");
    }
}
