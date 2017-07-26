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
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner.newConfigs;

public class ElasticsearchClusterBaseTest extends CamelTestSupport {

    public static ElasticsearchClusterRunner runner;
    public static String clusterName;
    public static TransportClient client;

    protected static final int ES_BASE_TRANSPORT_PORT = AvailablePortFinder.getNextAvailable();
    protected static final int ES_FIRST_NODE_TRANSPORT_PORT = AvailablePortFinder.getNextAvailable(ES_BASE_TRANSPORT_PORT + 1);
    protected static final int ES_BASE_HTTP_PORT = AvailablePortFinder.getNextAvailable(ES_BASE_TRANSPORT_PORT + 10);

    @SuppressWarnings("resource")
    @BeforeClass
    public static void cleanUpOnce() throws Exception {
        deleteDirectory("target/testcluster/");
        clusterName = "es-cl-run-" + System.currentTimeMillis();
        // create runner instance

        runner = new ElasticsearchClusterRunner();
        // create ES nodes
        runner.onBuild(new ElasticsearchClusterRunner.Builder() {
            @Override
            public void build(final int number, final Builder settingsBuilder) {
                settingsBuilder.put("http.cors.enabled", true);
                settingsBuilder.put("http.cors.allow-origin", "*");
            }
        }).build(newConfigs()
                 .clusterName(clusterName)
                 .numOfNode(3)
                 .baseTransportPort(ES_BASE_TRANSPORT_PORT)
                 .baseHttpPort(ES_BASE_HTTP_PORT)
                 .basePath("target/testcluster/")
                 .disableESLogger());

        // wait for green status
        runner.ensureGreen();

        client = new PreBuiltTransportClient(getSettings()).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), ES_FIRST_NODE_TRANSPORT_PORT));
    }

    private static Settings getSettings() {
        return Settings.builder()
            .put("cluster.name", clusterName)
            .put("http.enabled", true)
            .put("client.transport.ignore_cluster_name", false)
            .put("client.transport.sniff", true)
            .build();
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        if (client != null) {
            client.close();
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

        Map<String, String> map = new HashMap<String, String>();
        map.put(key, value);
        return map;
    }

    String createPrefix() {
        // make use of the test method name to avoid collision
        return getTestMethodName().toLowerCase() + "-";
    }
}
