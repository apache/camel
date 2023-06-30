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
package org.apache.camel.component.jira.oauth;

import java.net.URI;
import java.util.regex.Pattern;

import com.atlassian.httpclient.apache.httpcomponents.DefaultRequest;
import com.atlassian.httpclient.api.HttpClient;
import com.atlassian.httpclient.api.Request;
import com.atlassian.httpclient.api.ResponsePromise;
import com.atlassian.httpclient.api.ResponseTransformation;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.DisposableHttpClient;

/**
 * Similar to AtlassianHttpClientDecorator, except this class exposes the URI and HTTP Method to the request builder
 */
public abstract class OAuthHttpClientDecorator implements DisposableHttpClient {

    private final HttpClient httpClient;
    private final AuthenticationHandler authenticationHandler;
    private URI uri;

    protected OAuthHttpClientDecorator(HttpClient httpClient, AuthenticationHandler authenticationHandler) {
        this.httpClient = httpClient;
        this.authenticationHandler = authenticationHandler;
    }

    @Override
    public void flushCacheByUriPattern(Pattern urlPattern) {
        httpClient.flushCacheByUriPattern(urlPattern);
    }

    @Override
    public Request.Builder newRequest() {
        return new OAuthAuthenticatedRequestBuilder();
    }

    @Override
    public Request.Builder newRequest(URI uri) {
        final Request.Builder builder = new OAuthAuthenticatedRequestBuilder();
        builder.setUri(uri);
        this.uri = uri;
        return builder;
    }

    @Override
    public Request.Builder newRequest(URI uri, String contentType, String entity) {
        final Request.Builder builder = new OAuthAuthenticatedRequestBuilder();
        this.uri = uri;
        builder.setUri(uri);
        builder.setContentType(contentType);
        builder.setEntity(entity);
        return builder;
    }

    @Override
    public Request.Builder newRequest(String uri) {
        final Request.Builder builder = new OAuthAuthenticatedRequestBuilder();
        this.uri = URI.create(uri);
        builder.setUri(this.uri);
        return builder;
    }

    @Override
    public Request.Builder newRequest(String uri, String contentType, String entity) {
        final Request.Builder builder = new OAuthAuthenticatedRequestBuilder();
        this.uri = URI.create(uri);
        builder.setUri(this.uri);
        builder.setContentType(contentType);
        builder.setEntity(entity);
        return builder;
    }

    @Override
    public <A> ResponseTransformation.Builder<A> transformation() {
        return httpClient.transformation();
    }

    @Override
    public ResponsePromise execute(Request request) {
        return httpClient.execute(request);
    }

    public class OAuthAuthenticatedRequestBuilder extends DefaultRequest.DefaultRequestBuilder {

        Request.Method method;

        OAuthAuthenticatedRequestBuilder() {
            super(httpClient);
        }

        @Override
        public ResponsePromise execute(Request.Method method) {
            if (authenticationHandler != null) {
                this.setMethod(method);
                this.method = method;
                authenticationHandler.configure(this);
            }
            return super.execute(method);
        }

        public URI getUri() {
            return uri;
        }

    }
}
