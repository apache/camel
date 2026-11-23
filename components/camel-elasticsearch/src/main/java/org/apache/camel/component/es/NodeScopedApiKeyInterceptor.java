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
package org.apache.camel.component.es;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.function.Supplier;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

/**
 * Adds the API key in the Authorization header of the requests to the nodes of the cluster the client talks to: the
 * configured hosts, and the nodes found by the sniffer when it is enabled. A request to any other host, such as the
 * target of a redirect, does not get the key.
 * <p>
 * The key is not set as a default header of the client, because HttpClient copies the headers of the original request
 * onto a redirect, whatever its host. An interceptor runs for each request, with the host it is sent to.
 */
final class NodeScopedApiKeyInterceptor implements HttpRequestInterceptor {

    private final String authorization;
    private final Supplier<? extends Collection<HttpHost>> nodes;

    NodeScopedApiKeyInterceptor(String apiKey, Supplier<? extends Collection<HttpHost>> nodes) {
        this.authorization = "ApiKey " + encode(apiKey);
        this.nodes = nodes;
    }

    @Override
    public void process(HttpRequest request, HttpContext context) {
        HttpHost target = HttpCoreContext.adapt(context).getTargetHost();
        if (NodeScopedCredentialsProvider.isNode(nodes.get(), target)) {
            request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    /**
     * The key as Elasticsearch shows it when it is created is already encoded: base64 never contains a colon. A key
     * given as id:key is encoded here.
     */
    static String encode(String apiKey) {
        if (apiKey.indexOf(':') >= 0) {
            return Base64.getEncoder().encodeToString(apiKey.getBytes(StandardCharsets.UTF_8));
        }
        return apiKey;
    }
}
