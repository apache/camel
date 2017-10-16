/**
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
package org.apache.camel.component.elasticsearch5;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpHost;
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;

public class ElasticsearchClusterBaseTest extends CamelTestSupport {

    public static ElasticsearchClusterRunner runner;
    public static String clusterName;
    public static RestClient restclient;
    public static RestHighLevelClient client;

    protected static final int ES_BASE_HTTP_PORT = AvailablePortFinder.getNextAvailable();
    protected static final int ES_FIRST_NODE_TRANSPORT_PORT = AvailablePortFinder.getNextAvailable(ES_BASE_HTTP_PORT + 1);

    @SuppressWarnings("resource")
    @BeforeClass
    public static void cleanUpOnce() throws Exception {
        deleteDirectory("target/testcluster/");
        clusterName = "es-cl-run-" + System.currentTimeMillis();
        // create runner instance

        runner = new ElasticsearchClusterRunner();
        // create ES nodes
        runner.onBuild((number, settingsBuilder) -> {
            settingsBuilder.put("http.cors.enabled", true);
            settingsBuilder.put("http.cors.allow-origin", "*");
            settingsBuilder.put("discovery.zen.ping.unicast.hosts", "127.0.0.1:9301,127.0.0.1:9302,127.0.0.1:9303");
        }).build(newConfigs()
                 .clusterName(clusterName)
                 .numOfNode(3)
                 .baseHttpPort(ES_BASE_HTTP_PORT)
                 .basePath("target/testcluster/")
                 .disableESLogger());

        // wait for green status
        runner.ensureGreen();
        restclient = RestClient.builder(new HttpHost(InetAddress.getByName("localhost"), ES_FIRST_NODE_TRANSPORT_PORT)).build();
        client = new RestHighLevelClient(restclient);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        if (restclient != null) {
            restclient.close();
        }
        if (runner != null) {
            // close runner
            runner.close();
            // delete all files
            runner.clean();
        }
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // let's speed up the tests using the same context
        return true;
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
}
