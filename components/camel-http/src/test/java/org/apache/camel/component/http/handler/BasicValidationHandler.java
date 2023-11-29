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
package org.apache.camel.component.http.handler;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

public class BasicValidationHandler implements HttpRequestHandler {

    protected String expectedUri;
    protected final String expectedMethod;
    protected final String expectedQuery;
    protected final Object expectedContent;
    protected final String responseContent;

    public BasicValidationHandler(String expectedMethod, String expectedQuery,
                                  Object expectedContent, String responseContent) {
        this.expectedMethod = expectedMethod;
        this.expectedQuery = expectedQuery;
        this.expectedContent = expectedContent;
        this.responseContent = responseContent;
    }

    public BasicValidationHandler(String expectedUri, String expectedMethod, String expectedQuery,
                                  Object expectedContent, String responseContent) {
        this.expectedUri = expectedUri;
        this.expectedMethod = expectedMethod;
        this.expectedQuery = expectedQuery;
        this.expectedContent = expectedContent;
        this.responseContent = responseContent;
    }

    @Override
    public void handle(
            final ClassicHttpRequest request, final ClassicHttpResponse response,
            final HttpContext context)
            throws HttpException, IOException {

        if (expectedUri != null && !expectedUri.equals(request.getRequestUri())) {
            response.setCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (expectedMethod != null && !expectedMethod.equals(request.getMethod())) {
            response.setCode(HttpStatus.SC_METHOD_FAILURE);
            return;
        }

        if (!validateQuery(request)) {
            response.setCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (expectedContent != null) {
            HttpEntity entity = request.getEntity();
            String content = EntityUtils.toString(entity);

            if (!expectedContent.equals(content)) {
                response.setCode(HttpStatus.SC_BAD_REQUEST);
                return;
            }
        }

        response.setCode(HttpStatus.SC_OK);
        String content = buildResponse(request);
        if (content != null) {
            response.setEntity(new StringEntity(content, StandardCharsets.US_ASCII));
        }
    }

    protected boolean validateQuery(ClassicHttpRequest request) throws IOException {
        try {
            String query = request.getUri().getQuery();
            if (expectedQuery != null && !expectedQuery.equals(query)) {
                return false;
            }
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return true;
    }

    protected String buildResponse(ClassicHttpRequest request) {
        return responseContent;
    }

}
