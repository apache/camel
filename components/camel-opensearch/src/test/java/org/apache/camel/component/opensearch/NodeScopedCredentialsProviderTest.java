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

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NodeScopedCredentialsProviderTest {

    private static final Credentials CREDENTIALS = new UsernamePasswordCredentials("admin", "secret".toCharArray());

    private final List<HttpHost> nodes = new ArrayList<>(
            List.of(new HttpHost("http", "os1.example.com", 9200),
                    new HttpHost("https", "os2.example.com", 9243),
                    new HttpHost("https", "os3.example.com", -1)));

    private final NodeScopedCredentialsProvider provider = new NodeScopedCredentialsProvider(CREDENTIALS, () -> nodes);

    @Test
    void offersTheCredentialsToEveryNode() {
        assertSame(CREDENTIALS, credentialsFor("http", "os1.example.com", 9200));
        assertSame(CREDENTIALS, credentialsFor("https", "os2.example.com", 9243));
    }

    @Test
    void usesTheDefaultPortOfTheSchemeWhenTheNodeHasNone() {
        assertSame(CREDENTIALS, credentialsFor("https", "os3.example.com", 443));
        assertNull(credentialsFor("https", "os3.example.com", 9200));
    }

    @Test
    void matchesTheHostNameRegardlessOfCase() {
        assertSame(CREDENTIALS, credentialsFor("HTTP", "OS1.Example.COM", 9200));
    }

    @Test
    void offersNoCredentialsToAHostThatIsNotANode() {
        assertNull(credentialsFor("http", "other.example.com", 9200));
    }

    @Test
    void offersNoCredentialsToAnotherPortOrSchemeOfANode() {
        assertNull(credentialsFor("http", "os1.example.com", 9300));
        assertNull(credentialsFor("https", "os1.example.com", 9200));
        assertNull(credentialsFor("http", "os2.example.com", 9243));
    }

    @Test
    void followsTheCurrentNodes() {
        HttpHost sniffed = new HttpHost("http", "os4.example.com", 9200);
        assertNull(credentialsFor("http", "os4.example.com", 9200));

        nodes.add(sniffed);
        assertSame(CREDENTIALS, credentialsFor("http", "os4.example.com", 9200));

        nodes.remove(sniffed);
        assertNull(credentialsFor("http", "os4.example.com", 9200));
    }

    @Test
    void offersNoCredentialsToAScopeWithoutProtocolOrHost() {
        assertNull(provider.getCredentials(new AuthScope(null, null, -1, null, null), null));
        assertNull(provider.getCredentials(new AuthScope("os1.example.com", 9200), null));
        assertNull(provider.getCredentials(null, null));
    }

    private Credentials credentialsFor(String scheme, String host, int port) {
        // the shape of the scope HttpClient looks the credentials up with, preemptively or after a challenge
        return provider.getCredentials(new AuthScope(new HttpHost(scheme, host, port), "realm", "Basic"), null);
    }
}
