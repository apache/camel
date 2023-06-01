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
package org.apache.camel.component.es.integration;

import java.util.HashMap;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.camel.CamelContext;
import org.apache.camel.component.es.ElasticsearchComponent;
import org.apache.camel.test.infra.elasticsearch.services.ElasticSearchService;
import org.apache.camel.test.infra.elasticsearch.services.ElasticSearchServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ElasticsearchTestSupport extends CamelTestSupport {

    @RegisterExtension
    protected static ElasticSearchService service = ElasticSearchServiceFactory.createSingletonService();

    protected static String clusterName = "docker-cluster";
    protected static RestClient restClient;
    protected static ElasticsearchClient client;
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchTestSupport.class);

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        HttpHost host
                = new HttpHost(service.getElasticSearchHost(), service.getPort(), "https");
        final RestClientBuilder builder = RestClient.builder(host);
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(service.getUsername(), service.getPassword()));
        builder.setHttpClientConfigCallback(
                httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    httpClientBuilder.setSSLContext(service.getSslContext().orElseThrow());
                    return httpClientBuilder;
                });
        restClient = builder.build();
        client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
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
        final ElasticsearchComponent elasticsearchComponent = new ElasticsearchComponent();
        elasticsearchComponent.setEnableSSL(true);
        elasticsearchComponent.setHostAddresses(service.getHttpHostAddress());
        elasticsearchComponent.setUser(service.getUsername());
        elasticsearchComponent.setPassword(service.getPassword());
        elasticsearchComponent.setCertificatePath("file:%s".formatted(service.getCertificatePath().orElseThrow()));

        CamelContext context = super.createCamelContext();
        context.addComponent("elasticsearch", elasticsearchComponent);

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
