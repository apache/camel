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
package org.apache.camel.component.http;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpConcurrentTest extends BaseHttpTest {

    private final AtomicInteger counter = new AtomicInteger();


    private HttpServer localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/", (request, response, context) -> {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    response.setStatusCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("" + counter.incrementAndGet()));
                }).create();
        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<String>> responses = new HashMap<>();
        for (int i = 0; i < files; i++) {
            Future<String> out = executor.submit(() -> template.requestBody("http://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort(), null, String.class));
            responses.put(i, out);
        }

        assertEquals(files, responses.size());

        // get all responses
        Set<String> unique = new HashSet<>();
        for (Future<String> future : responses.values()) {
            unique.add(future.get());
        }

        // should be 'files' unique responses
        assertEquals("Should be " + files + " unique responses", files, unique.size());
        executor.shutdownNow();
    }

}
