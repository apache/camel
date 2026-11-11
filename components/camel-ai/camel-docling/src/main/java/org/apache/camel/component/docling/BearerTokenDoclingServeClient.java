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
package org.apache.camel.component.docling;

import java.net.http.HttpRequest;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.client.DoclingServeClient;
import ai.docling.serve.client.JsonReadException;
import ai.docling.serve.client.operations.RequestContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link DoclingServeClient} that sends the access token in the {@code Authorization: Bearer} header.
 * <p>
 * The docling-serve client only supports API key authentication, which it always sends in the {@code X-Api-Key} header,
 * and it offers no way to configure any other header. A bearer token - for example one resolved from the
 * {@code oauthProfile} option, or one issued by an OAuth proxy sitting in front of docling-serve - therefore never
 * reaches the server, and every request fails with a 401 even though the endpoint is configured with credentials.
 * <p>
 * The client leaves {@link #createRequestBuilder(RequestContext)} open for subclasses, which is where the header is
 * added here. Everything else, including the JSON codec, mirrors the client the builder itself would have created.
 */
final class BearerTokenDoclingServeClient extends DoclingServeClient {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String authorization;

    BearerTokenDoclingServeClient(DoclingServeClientBuilder<?, ?> builder, String token) {
        super(builder);
        this.authorization = "Bearer " + token;
    }

    @Override
    protected <T> T readValue(String value, Class<T> type) {
        try {
            return jsonMapper.readValue(value, type);
        } catch (JacksonException e) {
            throw new JsonReadException(e);
        }
    }

    @Override
    protected <T> String writeValueAsString(T value) {
        try {
            if (prettyPrintJson()) {
                return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            }
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected <I, O> HttpRequest.Builder createRequestBuilder(RequestContext<I, O> context) {
        return super.createRequestBuilder(context).header("Authorization", authorization);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DoclingServeApi, B extends DoclingServeApi.DoclingApiBuilder<T, B>> B toBuilder() {
        // the token is not part of DoclingServeApiConfig, so a builder cannot carry it over
        return (B) DoclingServeClient.builder().config(config());
    }
}
