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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.opensearch.OpensearchComponent;
import org.apache.camel.test.infra.opensearch.services.OpenSearchService;
import org.apache.camel.test.infra.opensearch.services.OpenSearchServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
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
public class OpensearchTestSupport extends CamelTestSupport {

    @RegisterExtension
    protected static OpenSearchService service = OpenSearchServiceFactory.createSingletonService();

    protected static String clusterName = "docker-cluster";
    protected static RestClient restClient;
    protected static OpenSearchClient client;
    private static final Logger LOG = LoggerFactory.getLogger(OpensearchTestSupport.class);

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
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
    }

    @Override
    protected void cleanupResources() throws Exception {
        super.cleanupResources();
        if (restClient != null) {
            restClient.close();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final OpensearchComponent openSearchComponent = new OpensearchComponent();
        openSearchComponent.setHostAddresses(String.format("%s:%d", service.getOpenSearchHost(), service.getPort()));
        openSearchComponent.setUser(service.getUsername());
        openSearchComponent.setPassword(service.getPassword());

        CamelContext context = super.createCamelContext();
        context.addComponent("opensearch", openSearchComponent);

        return context;
    }

    /**
     * As we don't delete the {@code target/data} folder for <b>each</b> test below (otherwise they would run much
     * slower), we need to make sure there's no side effect of the same used data through creating unique indexes.
     */
    Map<String, String> createIndexedData(String... additionalPrefixes) {
        String prefix = createPrefix();

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

    String createPrefix() {
        // make use of the test method name to avoid collision
        return getCurrentTestName().toLowerCase() + "-";
    }

    RestClient getClient() {
        return restClient;
    }
}
