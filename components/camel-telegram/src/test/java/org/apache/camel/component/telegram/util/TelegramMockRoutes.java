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
package org.apache.camel.component.telegram.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelegramMockRoutes extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramMockRoutes.class);

    private final int port;
    private final Map<String, MockProcessor<?>> mocks = new LinkedHashMap<>();

    public TelegramMockRoutes(int port) {
        super();
        this.port = port;
    }

    public TelegramMockRoutes addEndpoint(String path, String method, Class<?> returnType, String... responseBodies) {
        this.mocks.put(path, new MockProcessor<>(method, path, returnType, responseBodies));
        return this;
    }

    @Override
    public void configure() throws Exception {

        mocks.forEach((key, value) -> {
            from("netty-http:http://localhost:" + port + "/botmock-token/" + key + "?httpMethodRestrict=" + value.method)
                .process(value);
        });

    }

    public static class MockProcessor<T> implements Processor {
        private final String method;
        private final String path;
        private final List<T> recordedMessages = new ArrayList<>();
        private final String[] responseBodies;
        private final Object lock = new Object();
        private final Class<?> returnType;

        public MockProcessor(String method, String path, Class<T> returnType, String... responseBodies) {
            this.method = method;
            this.path = path;
            this.returnType = returnType;
            this.responseBodies = responseBodies;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            synchronized (lock) {
                final Message m = exchange.getMessage();
                final int responseIndex = Math.min(recordedMessages.size(), responseBodies.length - 1);
                if (returnType == byte[].class) {
                    recordedMessages.add((T) m.getBody(byte[].class));
                } else {
                    final String rawBody = m.getBody(String.class);
                    LOG.debug("Recording {} {} body {}", method, path, rawBody);
                    @SuppressWarnings("unchecked")
                    final T body = returnType != String.class ? (T) new ObjectMapper().readValue(rawBody, returnType) : (T) rawBody;
                    recordedMessages.add(body);
                    final byte[] bytes = responseBodies[responseIndex].getBytes(StandardCharsets.UTF_8);
                    m.setBody(bytes);
                    m.setHeader("Content-Length", bytes.length);
                    m.setHeader("Content-Type", "application/json; charset=UTF-8");
                }
            }
        }

        public void clearRecordedMessages() {
            synchronized (lock) {
                recordedMessages.clear();
            }
        }
        public List<T> getRecordedMessages() {
            synchronized (lock) {
                return new ArrayList<T>(recordedMessages);
            }
        }

        public List<T> awaitRecordedMessages(int count, long timeoutMillis) {
            return Awaitility.await()
                    .atMost(timeoutMillis, TimeUnit.MILLISECONDS)
                    .until(this::getRecordedMessages, msgs -> msgs.size() >= count);
        }

    }

    public int getPort() {
        return port;
    }

    @SuppressWarnings("unchecked")
    public <T> MockProcessor<T> getMock(String path) {
        return (MockProcessor<T>) mocks.get(path);
    }

}
