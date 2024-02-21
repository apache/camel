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
package org.apache.camel.component.opensearch.integration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.opensearch.OpensearchComponent;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.test.infra.opensearch.services.OpenSearchService;
import org.apache.camel.test.infra.opensearch.services.OpenSearchServiceFactory;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class OpensearchTestSupport implements CamelTestSupportHelper, ConfigurableRoute, ConfigurableContext {

    @Order(1)
    @RegisterExtension
    public static final OpenSearchService service = OpenSearchServiceFactory.createSingletonService();

    @Order(2)
    @RegisterExtension
    public static final CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    protected static String clusterName = "docker-cluster";
    private static final Logger LOG = LoggerFactory.getLogger(OpensearchTestSupport.class);

    protected RestClient restClient;
    protected OpenSearchClient client;

    private String prefix;

    @BeforeEach
    public void beforeEach(TestInfo testInfo) {
        HttpHost host
                = new HttpHost(service.getOpenSearchHost(), service.getPort(), "http");
        final RestClientBuilder builder = RestClient.builder(host);
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(service.getUsername(), service.getPassword()));
        builder.setHttpClientConfigCallback(
                httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    return httpClientBuilder;
                });
        restClient = builder.build();
        client = new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));

        // make use of the test method name to avoid collision
        prefix = testInfo.getDisplayName().toLowerCase() + "-";
    }

    @AfterEach
    public void afterEach() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return contextExtension;
    }

    protected String getPrefix() {
        return prefix;
    }

    protected CamelContext camelContext() {
        return getCamelContextExtension().getContext();
    }

    protected ProducerTemplate template() {
        return getCamelContextExtension().getProducerTemplate();
    }

    /**
     * As we don't delete the {@code target/data} folder for <b>each</b> test below (otherwise they would run much
     * slower), we need to make sure there's no side effect of the same used data through creating unique indexes.
     */
    Map<String, String> createIndexedData(String... additionalPrefixes) {
        String prefix = getPrefix();

        // take over any potential prefixes we may have been asked for
        if (additionalPrefixes.length > 0) {
            StringBuilder sb = new StringBuilder(prefix);
            for (String additionalPrefix : additionalPrefixes) {
                sb.append(additionalPrefix).append("-");
            }
            prefix = sb.toString();
        }

        String key = prefix + "key";
        String value = prefix + "value";
        LOG.info("Creating indexed data using the key/value pair {} => {}", key, value);

        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    RestClient getClient() {
        return restClient;
    }

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) {
        final OpensearchComponent openSearchComponent = new OpensearchComponent();
        openSearchComponent.setHostAddresses(String.format("%s:%d", service.getOpenSearchHost(), service.getPort()));
        openSearchComponent.setUser(service.getUsername());
        openSearchComponent.setPassword(service.getPassword());

        context.addComponent("opensearch", openSearchComponent);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    protected abstract RouteBuilder createRouteBuilder();
}
