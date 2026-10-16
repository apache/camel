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

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Offers the configured credentials only to the nodes of the cluster the client talks to: the configured hosts, and the
 * nodes found by the sniffer when it is enabled. A node matches when the scheme, the host name and the port are the
 * same, the default port of the scheme standing in for a missing one.
 */
final class NodeScopedCredentialsProvider implements CredentialsProvider {

    private final Credentials credentials;
    private final Supplier<? extends Collection<HttpHost>> nodes;

    NodeScopedCredentialsProvider(Credentials credentials, Supplier<? extends Collection<HttpHost>> nodes) {
        this.credentials = credentials;
        this.nodes = nodes;
    }

    @Override
    public Credentials getCredentials(AuthScope authScope, HttpContext context) {
        if (authScope != null && authScope.getProtocol() != null && authScope.getHost() != null) {
            for (HttpHost node : nodes.get()) {
                if (sameAuthority(node, authScope)) {
                    return credentials;
                }
            }
        }
        return null;
    }

    private static boolean sameAuthority(HttpHost node, AuthScope authScope) {
        return node.getSchemeName().equalsIgnoreCase(authScope.getProtocol())
                && node.getHostName().equalsIgnoreCase(authScope.getHost())
                && effectivePort(node.getSchemeName(), node.getPort())
                   == effectivePort(authScope.getProtocol(), authScope.getPort());
    }

    private static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
