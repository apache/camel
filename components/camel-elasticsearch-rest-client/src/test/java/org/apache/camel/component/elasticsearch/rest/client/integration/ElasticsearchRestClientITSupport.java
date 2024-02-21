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
package org.apache.camel.component.elasticsearch.rest.client.integration;

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
public class ElasticsearchRestClientITSupport extends CamelTestSupport {

    @RegisterExtension
    protected static ElasticSearchService service = ElasticSearchServiceFactory.createSingletonService();

    protected static RestClient restClient;

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchRestClientITSupport.class);

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
    }

    @Override
    protected void cleanupResources() throws Exception {
        super.cleanupResources();
        if (restClient != null) {
            restClient.close();
        }
    }

}
