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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpensearchProducerCredentialsTest {

    @Test
    void offersTheConfiguredCredentialsToEveryConfiguredHostOnly() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            OpensearchConfiguration configuration
                    = configuration(context, "os1.example.com:9200,os2.example.com:9201", "admin", "secret");

            CredentialsProvider provider = new OpensearchProducer(endpoint(context), configuration)
                    .createCredentialsProvider(configuration::getHostAddressesList);

            Credentials credentials = provider.getCredentials(scope("http", "os1.example.com", 9200), null);
            assertNotNull(credentials);
            assertEquals("admin", credentials.getUserPrincipal().getName());
            assertNotNull(provider.getCredentials(scope("http", "os2.example.com", 9201), null));

            assertNull(provider.getCredentials(scope("http", "other.example.com", 9200), null));
            assertNull(provider.getCredentials(scope("http", "os1.example.com", 9201), null));
            assertNull(provider.getCredentials(scope("https", "os1.example.com", 9200), null));
        }
    }

    @Test
    void createsNoProviderWithoutCredentials() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            OpensearchConfiguration configuration = configuration(context, "os1.example.com:9200", null, null);

            assertNull(new OpensearchProducer(endpoint(context), configuration)
                    .createCredentialsProvider(configuration::getHostAddressesList));
        }
    }

    private static OpensearchConfiguration configuration(
            CamelContext context, String hostAddresses, String user, String password) {
        OpensearchComponent component = new OpensearchComponent(context);
        component.setHostAddresses(hostAddresses);
        component.setUser(user);
        component.setPassword(password);
        context.addComponent("opensearch", component);
        return endpoint(context).getConfiguration();
    }

    private static OpensearchEndpoint endpoint(CamelContext context) {
        return context.getEndpoint("opensearch:test-cluster", OpensearchEndpoint.class);
    }

    private static AuthScope scope(String scheme, String host, int port) {
        return new AuthScope(new HttpHost(scheme, host, port), "realm", "Basic");
    }
}
