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
package org.apache.camel.test.infra.openai.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mock server for OpenAI API testing. Implements JUnit 5 extension lifecycle methods.
 */
public class OpenAIMock implements BeforeEachCallback, AfterEachCallback {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAIMock.class);

    private HttpServer server;
    private final List<MockExpectation> expectations;
    private final List<EmbeddingExpectation> embeddingExpectations;
    private final OpenAIMockBuilder builder;
    private final ObjectMapper objectMapper;
    private ExecutorService executor;

    public OpenAIMock() {
        this.expectations = new ArrayList<>();
        this.embeddingExpectations = new ArrayList<>();
        this.objectMapper = new ObjectMapper();
        this.builder = new OpenAIMockBuilder(this, this.expectations, this.embeddingExpectations);
    }

    public OpenAIMockBuilder builder() {
        return this.builder;
    }

    public String getBaseUrl() {
        if (server == null) {
            throw new IllegalStateException("Mock server not started. Call beforeEach() first.");
        }
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", new OpenAIMockServerHandler(expectations, embeddingExpectations, objectMapper));

        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();

        LOG.info("Mock web server started on {}", server.getAddress());
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        if (server != null) {
            server.stop(0);
            executor.shutdownNow();
            LOG.info("Mock web server shut down");
        }
    }
}
