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

package org.apache.camel.component.clickup.util;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickUpMockRoutes extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ClickUpMockRoutes.class);

    private final int port;
    private final List<MockProcessor<?>> mocks = new ArrayList<>();

    public ClickUpMockRoutes(int port) {
        this.port = port;
    }

    public ClickUpMockRoutes addEndpoint(
            String path,
            String method,
            boolean pathExactMatch,
            Class<?> returnType,
            MockProcessorResponseBodyProvider mockProcessorResponseBodyProvider) {
        this.mocks.add(
                new MockProcessor<>(method, path, pathExactMatch, returnType, mockProcessorResponseBodyProvider));
        return this;
    }

    public ClickUpMockRoutes addErrorEndpoint(String path, String method, boolean pathExactMatch, int errorCode) {
        this.mocks.add(new MockProcessor<>(method, path, pathExactMatch, errorCode));
        return this;
    }

    @Override
    public void configure() {

        mocks.forEach(processor -> from("netty-http:http://localhost:" + port + "/clickup-api-mock/" + processor.path
                        + "?httpMethodRestrict=" + processor.method
                        + (processor.pathExactMatch ? "" : "&matchOnUriPrefix=true"))
                .process(processor));
    }

    public static class MockProcessor<T> implements Processor {
        private final String method;
        private final String path;
        private final boolean pathExactMatch;
        private final List<T> recordedMessages = new ArrayList<>();
        private int errorCode;
        private final MockProcessorResponseBodyProvider responseBodyProvider;
        private final Object lock = new Object();
        private final Class<?> returnType;

        public MockProcessor(
                String method,
                String path,
                boolean pathExactMatch,
                Class<T> returnType,
                MockProcessorResponseBodyProvider responseBodyProvider) {
            this.method = method;
            this.path = path;
            this.pathExactMatch = pathExactMatch;
            this.returnType = returnType;
            this.responseBodyProvider = responseBodyProvider;
        }

        public MockProcessor(String method, String path, boolean pathExactMatch, int errorCode) {
            this.method = method;
            this.path = path;
            this.pathExactMatch = pathExactMatch;
            this.returnType = String.class;
            this.responseBodyProvider = null;
            this.errorCode = errorCode;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            assert responseBodyProvider != null;

            synchronized (lock) {
                final Message m = exchange.getMessage();
                if (errorCode > 0) {
                    m.setHeader(Exchange.HTTP_RESPONSE_CODE, errorCode);
                } else {
                    if (returnType == byte[].class) {
                        recordedMessages.add((T) m.getBody(byte[].class));
                    } else {
                        final String rawBody = m.getBody(String.class);
                        LOG.debug("Recording {} {} body {}", method, path, rawBody);
                        @SuppressWarnings("unchecked")
                        final T body = returnType != String.class
                                ? (T) new ObjectMapper().readValue(rawBody, returnType)
                                : (T) rawBody;
                        recordedMessages.add(body);

                        final byte[] bytes = responseBodyProvider.provide().getBytes(StandardCharsets.UTF_8);
                        m.setBody(bytes);
                        m.setHeader("Content-Length", bytes.length);
                        m.setHeader("Content-Type", "application/json; charset=UTF-8");
                    }
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
                return new ArrayList<>(recordedMessages);
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
    public <T> MockProcessor<T> getMock(String method, String path) {
        Optional<MockProcessor<?>> mock = mocks.stream()
                .filter(processor -> processor.path.equals(path) && processor.method.equals(method))
                .findFirst();

        if (mock.isEmpty()) {
            throw new RuntimeException("Could not find mock for method " + method + " and path " + path);
        }

        return (MockProcessor<T>) mock.get();
    }

    public interface MockProcessorResponseBodyProvider {
        String provide();
    }
}
