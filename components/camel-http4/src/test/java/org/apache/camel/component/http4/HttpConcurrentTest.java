/**
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
package org.apache.camel.component.http4;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.Test;

/**
 * @version $Revision$
 */
public class HttpConcurrentTest extends BaseHttpTest {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    protected void registerHandler(LocalTestServer server) {
        server.register("/", new HttpRequestHandler() {
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
                response.setStatusCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity("" + counter.incrementAndGet()));
            }
        });
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
        Map<Integer, Future<Object>> responses = new ConcurrentHashMap<Integer, Future<Object>>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future<Object> out = executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    return template.requestBody("http4://" + getHostName() + ":" + getPort(), null, String.class);
                }
            });
            responses.put(index, out);
        }

        assertEquals(files, responses.size());

        // get all responses
        Set<Object> unique = new HashSet<Object>();
        for (Future<Object> future : responses.values()) {
            unique.add(future.get());
        }

        // should be 10 unique responses
        assertEquals("Should be " + files + " unique responses", files, unique.size());
    }

}
