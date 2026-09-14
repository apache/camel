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
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A request received by the mock server, recorded so that a test can assert on what was sent after the call returns, on
 * the test thread.
 *
 * @param method the HTTP method
 * @param path   the request path, for example {@code /v1/responses}
 * @param body   the request body decoded as UTF-8
 */
public record RecordedRequest(String method, String path, String body) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Parses the request body as JSON.
     */
    public JsonNode bodyAsJson() {
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException("The request body is not JSON: " + body, e);
        }
    }
}
