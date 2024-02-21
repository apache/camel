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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.http.HttpCredentialsHelper;
import org.apache.camel.util.json.Jsoner;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.WWWFormCodec;

public class OAuth2TokenRequestHandler implements HttpRequestHandler {

    private final String clientId;
    private final String clientSecret;
    private final String expectedToken;

    public OAuth2TokenRequestHandler(String expectedToken, String clientId, String clientSecret) {
        this.expectedToken = expectedToken;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
            throws HttpException, IOException {

        String requestBody = EntityUtils.toString(request.getEntity());
        WWWFormCodec.parse(requestBody, StandardCharsets.UTF_8).stream()
                .filter(pair -> pair.getName().equals("grant_type") && pair.getValue().equals("client_credentials"))
                .findAny().orElseThrow(() -> new HttpException("Invalid or missing grant_type"));

        if (request.getHeader(HttpHeaders.AUTHORIZATION) == null || !request.getHeader(HttpHeaders.AUTHORIZATION).getValue()
                .equals(HttpCredentialsHelper.generateBasicAuthHeader(clientId, clientSecret)))
            throw new HttpException("Invalid credentials");

        Map<String, String> responseEntity = new HashMap<>();
        responseEntity.put("access_token", expectedToken);

        response.setEntity(new StringEntity(Jsoner.serialize(responseEntity), ContentType.APPLICATION_JSON));
    }

}
