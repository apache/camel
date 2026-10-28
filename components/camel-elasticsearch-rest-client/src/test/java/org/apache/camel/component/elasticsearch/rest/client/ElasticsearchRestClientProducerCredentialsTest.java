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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ElasticsearchRestClientProducerCredentialsTest {

    private static final List<HttpHost> NODES
            = List.of(HttpHost.create("es1.example.com:9200"), HttpHost.create("https://es2.example.com:9243"));

    @Test
    void offersTheConfiguredCredentialsOnlyToTheNodes() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            ElasticsearchRestClientEndpoint endpoint = endpoint(context, "elastic", "secret");

            CredentialsProvider provider = new ElasticsearchRestClientProducer(endpoint).createCredentialsProvider(() -> NODES);

            Credentials credentials = provider.getCredentials(scope("http", "es1.example.com", 9200));
            assertNotNull(credentials);
            assertEquals("elastic", credentials.getUserPrincipal().getName());
            assertEquals("secret", credentials.getPassword());
            assertNotNull(provider.getCredentials(scope("https", "es2.example.com", 9243)));

            assertNull(provider.getCredentials(scope("http", "other.example.com", 9200)));
            assertNull(provider.getCredentials(scope("http", "es1.example.com", 9201)));
            assertNull(provider.getCredentials(scope("http", "es2.example.com", 9243)));
        }
    }

    @Test
    void createsNoProviderWithoutCredentials() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            ElasticsearchRestClientEndpoint endpoint = endpoint(context, null, null);

            assertNull(new ElasticsearchRestClientProducer(endpoint).createCredentialsProvider(() -> NODES));
        }
    }

    private static ElasticsearchRestClientEndpoint endpoint(CamelContext context, String user, String password) {
        ElasticsearchRestClientComponent component = new ElasticsearchRestClientComponent();
        component.setHostAddressesList("es1.example.com:9200,https://es2.example.com:9243");
        component.setUser(user);
        component.setPassword(password);
        context.addComponent("elasticsearch-rest-client", component);
        return context.getEndpoint("elasticsearch-rest-client:camel?operation=CREATE_INDEX",
                ElasticsearchRestClientEndpoint.class);
    }

    private static AuthScope scope(String scheme, String host, int port) {
        return new AuthScope(new HttpHost(host, port, scheme), "realm", "basic");
    }
}
