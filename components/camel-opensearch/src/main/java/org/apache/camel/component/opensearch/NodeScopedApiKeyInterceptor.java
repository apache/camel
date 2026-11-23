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
package org.apache.camel.component.opensearch;

import java.util.Collection;
import java.util.function.Supplier;

import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;

/**
 * Adds the API key in the Authorization header of the requests to the nodes of the cluster the client talks to: the
 * configured hosts, and the nodes found by the sniffer when it is enabled. A request to any other host, such as the
 * target of a redirect, does not get the key.
 * <p>
 * The key is not set as a default header of the client, because a default header is part of the request the client
 * redirects, whatever the host of the redirect. An interceptor runs for each request, with the host it is sent to.
 */
final class NodeScopedApiKeyInterceptor implements HttpRequestInterceptor {

    private final String authorization;
    private final Supplier<? extends Collection<HttpHost>> nodes;

    NodeScopedApiKeyInterceptor(String apiKey, Supplier<? extends Collection<HttpHost>> nodes) {
        this.authorization = "ApiKey " + apiKey;
        this.nodes = nodes;
    }

    @Override
    public void process(HttpRequest request, EntityDetails entity, HttpContext context) {
        if (isToNode(request, context)) {
            request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private boolean isToNode(HttpRequest request, HttpContext context) {
        if (context != null && HttpClientContext.castOrCreate(context).getHttpRoute() != null) {
            HttpHost target = HttpClientContext.castOrCreate(context).getHttpRoute().getTargetHost();
            return NodeScopedCredentialsProvider.isNode(nodes.get(), target.getSchemeName(), target.getHostName(),
                    target.getPort());
        }
        URIAuthority authority = request.getAuthority();
        return authority != null && NodeScopedCredentialsProvider.isNode(nodes.get(), request.getScheme(),
                authority.getHostName(), authority.getPort());
    }
}
