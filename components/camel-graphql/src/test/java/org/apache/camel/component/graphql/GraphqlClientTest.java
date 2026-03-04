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
package org.apache.camel.component.graphql;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.AuthenticationValidationHandler;
import org.apache.camel.spi.Registry;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.POST;

/**
 * Verify 'HttpClient' instances registered with Basic credentials.
 */
public class GraphqlClientTest extends BaseGraphqlTest {

    private final String user = "camel";
    private final String password = "password";
    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .register("/graphql",
                        new AuthenticationValidationHandler(
                                POST.name(), null, null,
                                getExpectedContent(), user, password))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {
        if (localServer != null) {
            localServer.stop();
        }
    }

    // verify that a httpClient configured with the correct credentials passes authentication
    @Test
    public void shouldPassAuthentication() {
        Exchange exchange = template.request("direct:start1", exchange1 -> {
        });

        assertExchange(exchange);
    }

    // verify that a httpClient configured with wrong credentials fails authentication
    @Test
    public void shouldFailAuthorisation() {
        Exchange exchange = template.request("direct:start2", exchange1 -> {
        });

        assertUnauthorizedResponse(exchange);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            // multiple routes to verify registration of multiple 'httpClient' instances
            @Override
            public void configure() {
                from("direct:start1")
                        .to("graphql://http://localhost:" + localServer.getLocalPort()
                            + "/graphql?query={books{id name}}"
                            + "&httpClient=#httpClient");

                from("direct:start2")
                        .to("graphql://http://localhost:" + localServer.getLocalPort()
                            + "/graphql?query={books{id name}}"
                            + "&httpClient=#httpClientWrongPassword");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("httpClient", createHttpClient(user, password));
        registry.bind("httpClientWrongPassword", createHttpClient(user, "wrongPassword"));
    }

    // create a HttpClient instance with the provided credentials
    private CloseableHttpClient createHttpClient(String user, String password) {
        CredentialsStore credentialsProvider = new BasicCredentialsProvider();
        // set credentials on the http-client instead of the endpoint for purpose of this test
        credentialsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials(user, password.toCharArray()));
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

        return httpClientBuilder.build();
    }
}
