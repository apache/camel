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
package org.apache.camel.language.groovy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrent cache misses of the same script compile it once.
 */
public class GroovyCompileOnceTest {

    private static final int THREADS = 8;

    private CamelContext context;
    private final AtomicInteger compilations = new AtomicInteger();
    // counts down when a thread has reached the script compilation path
    private final CountDownLatch arrived = new CountDownLatch(THREADS);

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.getRegistry().bind("shellFactory", new GroovyShellFactory() {
            @Override
            public GroovyShell createGroovyShell(Exchange exchange) {
                return new GroovyShell() {
                    @Override
                    public GroovyClassLoader getClassLoader() {
                        return new CountingClassLoader();
                    }
                };
            }

            @Override
            public Map<String, Object> getVariables(Exchange exchange) {
                // called by every evaluation before the cache lookup
                arrived.countDown();
                return Map.of();
            }
        });
        context.start();
    }

    @AfterEach
    public void tearDown() {
        context.stop();
    }

    @Test
    public void testConcurrentMissesCompileOnce() throws Exception {
        Expression expression = context.resolveLanguage("groovy").createExpression("getClass()");
        expression.init(context);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Class<?>>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    Exchange exchange = new DefaultExchange(context);
                    return expression.evaluate(exchange, Class.class);
                }));
            }
            Class<?> first = futures.get(0).get(30, TimeUnit.SECONDS);
            for (Future<Class<?>> f : futures) {
                assertSame(first, f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, compilations.get());
    }

    private final class CountingClassLoader extends GroovyClassLoader {

        @Override
        public Class parseClass(GroovyCodeSource codeSource, boolean shouldCacheSource) {
            try {
                // every thread has missed the cache before the first compilation finishes
                assertTrue(arrived.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            compilations.incrementAndGet();
            return super.parseClass(codeSource, shouldCacheSource);
        }
    }
}
