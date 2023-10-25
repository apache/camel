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
package org.apache.camel.component.http;

import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;

public class OAuth2ClientConfigurer implements HttpClientConfigurer {

    private final String clientId;
    private final String clientSecret;
    private final String tokenEndpoint;

    public OAuth2ClientConfigurer(String clientId, String clientSecret, String tokenEndpoint) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenEndpoint = tokenEndpoint;
    }

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        HttpClient httpClient = clientBuilder.build();
        clientBuilder.addRequestInterceptorFirst((HttpRequest request, EntityDetails entity, HttpContext context) -> {

            final HttpPost httpPost = new HttpPost(tokenEndpoint);

            httpPost.addHeader(HttpHeaders.AUTHORIZATION,
                    HttpCredentialsHelper.generateBasicAuthHeader(clientId, clientSecret));
            httpPost.setEntity(new StringEntity("grant_type=client_credentials", ContentType.APPLICATION_FORM_URLENCODED));

            httpClient.execute(httpPost, response -> {

                try {
                    String responseString = EntityUtils.toString(response.getEntity());

                    if (response.getCode() == 200) {
                        String accessToken = ((JsonObject) Jsoner.deserialize(responseString)).getString("access_token");
                        request.addHeader(HttpHeaders.AUTHORIZATION, accessToken);
                    } else {
                        throw new HttpException(
                                "Received error response from token request with Status Code: " + response.getCode());
                    }

                } catch (DeserializationException e) {
                    throw new HttpException("Something went wrong when reading token request response", e);
                }

                return null;
            });

        });
    }

}
