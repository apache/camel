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
package org.apache.camel.component.elasticsearch;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.Base58;

public class ElasticsearchBaseTest extends CamelTestSupport {
    public static final String ELASTICSEARCH_IMAGE = "elasticsearch:7.3.2";
    public static final int ELASTICSEARCH_DEFAULT_PORT = 9200;
    public static final int ELASTICSEARCH_DEFAULT_TCP_PORT = 9300;

    @ClassRule
    public static GenericContainer elasticsearch = new GenericContainer<>(ELASTICSEARCH_IMAGE)
        .withNetworkAliases("elasticsearch-" + Base58.randomString(6))
        .withEnv("discovery.type", "single-node")
        .withExposedPorts(ELASTICSEARCH_DEFAULT_PORT, ELASTICSEARCH_DEFAULT_TCP_PORT)
        .waitingFor(new HttpWaitStrategy()
                .forPort(ELASTICSEARCH_DEFAULT_PORT)
                .forStatusCodeMatching(response -> response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_UNAUTHORIZED)
                .withStartupTimeout(Duration.ofMinutes(2)));

    protected static String clusterName = "docker-cluster";
    protected static RestClient restClient;
    protected static RestHighLevelClient client;

    @BeforeClass
    public static void setUpOnce() throws Exception {
        HttpHost host = new HttpHost(elasticsearch.getContainerIpAddress(),  elasticsearch.getMappedPort(ELASTICSEARCH_DEFAULT_PORT));

        client = new RestHighLevelClient(RestClient.builder(host));
        restClient = client.getLowLevelClient();
    }

    @AfterClass
    public static void teardownOnce() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // let's speed up the tests using the same context
        return true;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final ElasticsearchComponent elasticsearchComponent = new ElasticsearchComponent();
        elasticsearchComponent.setHostAddresses(elasticsearch.getContainerIpAddress() + ":" + elasticsearch.getMappedPort(ELASTICSEARCH_DEFAULT_PORT));

        CamelContext context = super.createCamelContext();
        context.addComponent("elasticsearch-rest", elasticsearchComponent);

        return context;
    }

    /**
     * As we don't delete the {@code target/data} folder for <b>each</b> test
     * below (otherwise they would run much slower), we need to make sure
     * there's no side effect of the same used data through creating unique
     * indexes.
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
        log.info("Creating indexed data using the key/value pair {} => {}", key, value);

        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    String createPrefix() {
        // make use of the test method name to avoid collision
        return getTestMethodName().toLowerCase() + "-";
    }

    RestClient getClient() {
        return restClient;
    }
}
