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
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.WWWFormCodec;

public class OAuth2TokenScopeRequestHandler extends OAuth2TokenRequestHandler {

    private final String expectedScope;
    private final boolean isScopeInForm;

    public OAuth2TokenScopeRequestHandler(String expectedToken, String clientId, String clientSecret, String expectedScope,
                                          boolean isScopeInForm) {
        super(expectedToken, clientId, clientSecret);
        this.expectedScope = expectedScope;
        this.isScopeInForm = isScopeInForm;
    }

    @Override
    public void handle(ClassicHttpRequest classicHttpRequest, ClassicHttpResponse classicHttpResponse, HttpContext httpContext)
            throws HttpException, IOException {
        if (isScopeInForm) {
            String requestBody = EntityUtils.toString(classicHttpRequest.getEntity());
            classicHttpRequest.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_FORM_URLENCODED));
            WWWFormCodec.parse(requestBody, StandardCharsets.UTF_8).stream()
                    .filter(pair -> pair.getName().equals("scope") && pair.getValue().equals(expectedScope))
                    .findAny().orElseThrow(() -> new HttpException("Invalid or missing scope"));
        } else {
            try {
                if (classicHttpRequest.getUri() == null
                        || !classicHttpRequest.getUri().getQuery().contains("scope=" + expectedScope)) {
                    throw new HttpException("Invalid or missing scope");
                }
            } catch (URISyntaxException e) {
                throw new HttpException("invalid uri syntax");
            }
        }
        super.handle(classicHttpRequest, classicHttpResponse, httpContext);
    }
}
