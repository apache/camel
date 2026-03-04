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
package org.apache.camel.component.graphql.handler;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

public class BearerTokenValidationHandler implements HttpRequestHandler {

    private final String expectedToken;
    private final String content;

    public BearerTokenValidationHandler(String expectedToken, String content) {
        this.expectedToken = expectedToken;
        this.content = content;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
            throws HttpException {

        Header header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String auth = header != null ? header.getValue() : null;
        if (auth == null || !auth.equals("Bearer " + expectedToken)) {
            response.setCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }
        response.setCode(HttpStatus.SC_OK);
        response.setEntity(new StringEntity(content, StandardCharsets.US_ASCII));
    }
}
