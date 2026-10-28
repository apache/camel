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

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NodeScopedCredentialsProviderTest {

    private static final Credentials CREDENTIALS = new UsernamePasswordCredentials("elastic", "secret");

    private final List<HttpHost> nodes = new ArrayList<>(
            List.of(new HttpHost("es1.example.com", 9200, "http"),
                    new HttpHost("es2.example.com", 9243, "https"),
                    new HttpHost("es3.example.com", -1, "https")));

    private final NodeScopedCredentialsProvider provider = new NodeScopedCredentialsProvider(CREDENTIALS, () -> nodes);

    @Test
    void offersTheCredentialsToEveryNode() {
        assertSame(CREDENTIALS, provider.getCredentials(scope("http", "es1.example.com", 9200)));
        assertSame(CREDENTIALS, provider.getCredentials(scope("https", "es2.example.com", 9243)));
    }

    @Test
    void usesTheDefaultPortOfTheSchemeWhenTheNodeHasNone() {
        assertSame(CREDENTIALS, provider.getCredentials(scope("https", "es3.example.com", 443)));
        assertNull(provider.getCredentials(scope("https", "es3.example.com", 9200)));
    }

    @Test
    void matchesTheHostNameRegardlessOfCase() {
        assertSame(CREDENTIALS, provider.getCredentials(scope("HTTP", "ES1.Example.COM", 9200)));
    }

    @Test
    void offersNoCredentialsToAHostThatIsNotANode() {
        assertNull(provider.getCredentials(scope("http", "other.example.com", 9200)));
    }

    @Test
    void offersNoCredentialsToAnotherPortOrSchemeOfANode() {
        assertNull(provider.getCredentials(scope("http", "es1.example.com", 9300)));
        assertNull(provider.getCredentials(scope("https", "es1.example.com", 9200)));
        assertNull(provider.getCredentials(scope("http", "es2.example.com", 9243)));
    }

    @Test
    void followsTheCurrentNodes() {
        HttpHost sniffed = new HttpHost("es4.example.com", 9200, "http");
        assertNull(provider.getCredentials(scope("http", "es4.example.com", 9200)));

        nodes.add(sniffed);
        assertSame(CREDENTIALS, provider.getCredentials(scope("http", "es4.example.com", 9200)));

        nodes.remove(sniffed);
        assertNull(provider.getCredentials(scope("http", "es4.example.com", 9200)));
    }

    @Test
    void offersNoCredentialsToAScopeWithoutAnOrigin() {
        assertNull(provider.getCredentials(AuthScope.ANY));
        assertNull(provider.getCredentials(new AuthScope("es1.example.com", 9200)));
        assertNull(provider.getCredentials(null));
    }

    @Test
    void ignoresCredentialsSetAfterwards() {
        provider.setCredentials(scope("http", "other.example.com", 9200), new UsernamePasswordCredentials("user", "pass"));
        assertNull(provider.getCredentials(scope("http", "other.example.com", 9200)));

        provider.clear();
        assertSame(CREDENTIALS, provider.getCredentials(scope("http", "es1.example.com", 9200)));
    }

    private static AuthScope scope(String scheme, String host, int port) {
        // the shape of the scope HttpClient looks the credentials up with, preemptively or after a challenge
        return new AuthScope(new HttpHost(host, port, scheme), "realm", "basic");
    }
}
