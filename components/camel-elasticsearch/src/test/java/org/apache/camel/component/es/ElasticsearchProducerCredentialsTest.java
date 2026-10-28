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

class ElasticsearchProducerCredentialsTest {

    @Test
    void offersTheConfiguredCredentialsOnlyToTheConfiguredHosts() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            ElasticsearchConfiguration configuration
                    = configuration(context, "es1.example.com:9200,es2.example.com:9201", "elastic", "secret");

            CredentialsProvider provider = new ElasticsearchProducer(endpoint(context), configuration)
                    .createCredentialsProvider(configuration::getHostAddressesList);

            Credentials credentials = provider.getCredentials(scope("http", "es1.example.com", 9200));
            assertNotNull(credentials);
            assertEquals("elastic", credentials.getUserPrincipal().getName());
            assertEquals("secret", credentials.getPassword());
            assertNotNull(provider.getCredentials(scope("http", "es2.example.com", 9201)));

            assertNull(provider.getCredentials(scope("http", "other.example.com", 9200)));
            assertNull(provider.getCredentials(scope("http", "es1.example.com", 9201)));
            assertNull(provider.getCredentials(scope("https", "es1.example.com", 9200)));
        }
    }

    @Test
    void createsNoProviderWithoutCredentials() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            ElasticsearchConfiguration configuration = configuration(context, "es1.example.com:9200", null, null);

            assertNull(new ElasticsearchProducer(endpoint(context), configuration)
                    .createCredentialsProvider(configuration::getHostAddressesList));
        }
    }

    private static ElasticsearchConfiguration configuration(
            CamelContext context, String hostAddresses, String user, String password) {
        ElasticsearchComponent component = new ElasticsearchComponent(context);
        component.setHostAddresses(hostAddresses);
        component.setUser(user);
        component.setPassword(password);
        context.addComponent("elasticsearch", component);
        return endpoint(context).getConfiguration();
    }

    private static ElasticsearchEndpoint endpoint(CamelContext context) {
        return context.getEndpoint("elasticsearch:test-cluster", ElasticsearchEndpoint.class);
    }

    private static AuthScope scope(String scheme, String host, int port) {
        return new AuthScope(new HttpHost(host, port, scheme), "realm", "basic");
    }
}
