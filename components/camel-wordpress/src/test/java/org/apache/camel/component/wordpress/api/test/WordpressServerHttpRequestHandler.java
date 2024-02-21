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
package org.apache.camel.component.wordpress.api.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import org.apache.cxf.helpers.IOUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.HttpRequestWrapper;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WordpressServerHttpRequestHandler implements HttpRequestHandler {

    public static final String USERNAME = "ben";
    public static final String PASSWORD = "password123";

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressServerHttpRequestHandler.class);

    private final Map<String, String> mockResourceJsonResponse;

    public WordpressServerHttpRequestHandler(String mockResourceJsonResponse) {
        this.mockResourceJsonResponse = Collections.singletonMap("GET", mockResourceJsonResponse);
    }

    public WordpressServerHttpRequestHandler(Map<String, String> mockResourceJsonResponse) {
        this.mockResourceJsonResponse = mockResourceJsonResponse;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
            throws IOException {
        LOGGER.debug("received request {}", request);
        final HttpRequestWrapper requestWrapper = new HttpRequestWrapper(request);
        // make sure that our writing operations have authentication header
        if (!authenticate(requestWrapper)) {
            response.setCode(HttpStatus.SC_FORBIDDEN);
            response.setEntity(new StringEntity("Forbidden", ContentType.TEXT_PLAIN));
            return;
        }
        final String responseBody = IOUtils
                .toString(this.getClass().getResourceAsStream(mockResourceJsonResponse.get(requestWrapper.getMethod())));
        response.setCode(HttpStatus.SC_OK);
        response.setEntity(new StringEntity(responseBody, ContentType.APPLICATION_JSON));
    }

    private boolean authenticate(HttpRequestWrapper request) {
        // read operations don't need to authenticate
        if (request.getMethod().contentEquals("GET")) {
            return true;
        }
        for (Header authorizationHeader : request.getHeaders("Authorization")) {
            // Authorization: Basic base64credentials
            String base64Credentials = authorizationHeader.getValue().substring("Basic".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            // credentials = username:password
            final String[] values = credentials.split(":", 2);
            return USERNAME.equals(values[0]) && PASSWORD.equals(values[1]);
        }
        return false;
    }
}
