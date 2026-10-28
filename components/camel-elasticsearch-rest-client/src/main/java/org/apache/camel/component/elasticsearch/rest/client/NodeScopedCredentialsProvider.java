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
package org.apache.camel.component.elasticsearch.rest.client;

import java.util.Collection;
import java.util.function.Supplier;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;

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
    public Credentials getCredentials(AuthScope authScope) {
        HttpHost origin = authScope != null ? authScope.getOrigin() : null;
        if (origin != null) {
            for (HttpHost node : nodes.get()) {
                if (sameAuthority(node, origin)) {
                    return credentials;
                }
            }
        }
        return null;
    }

    /**
     * Ignored: the credentials come from the endpoint configuration only. HttpClient calls this for the user info of a
     * request URI.
     */
    @Override
    public void setCredentials(AuthScope authScope, Credentials credentials) {
        // read-only
    }

    @Override
    public void clear() {
        // read-only
    }

    private static boolean sameAuthority(HttpHost node, HttpHost origin) {
        return node.getSchemeName().equalsIgnoreCase(origin.getSchemeName())
                && node.getHostName().equalsIgnoreCase(origin.getHostName())
                && effectivePort(node) == effectivePort(origin);
    }

    private static int effectivePort(HttpHost host) {
        if (host.getPort() >= 0) {
            return host.getPort();
        }
        return "https".equalsIgnoreCase(host.getSchemeName()) ? 443 : 80;
    }
}
