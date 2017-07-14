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
package org.apache.camel.component.solr;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrServer;


public class SolrFixtures {
    static Logger log = Logger.getLogger(SolrFixtures.class);

    private static JettySolrRunner solrRunner;
    private static JettySolrRunner solrHttpsRunner;
    private static HttpSolrClient solrServer;
    private static HttpSolrClient solrHttpsServer;
    private static SolrCloudFixture cloudFixture;

    private static int port;
    private static int httpsPort;

    public enum TestServerType {
        USE_HTTP, USE_HTTPS, USE_CLOUD
    }

    TestServerType serverType;

    SolrFixtures(TestServerType serverType) {
        this.serverType = serverType;
    }

    String solrRouteUri() {
        if (serverType == TestServerType.USE_HTTPS) {
            return "solrs://localhost:" + httpsPort + "/solr";
        } else if (serverType == TestServerType.USE_CLOUD) {
            String zkAddrStr = cloudFixture.miniCluster.getZkServer().getZkAddress();
            return "solrCloud://localhost:" + httpsPort + "/solr?zkHost=" + zkAddrStr
                   + "&collection=collection1";
        } else {
            return "solr://localhost:" + port + "/solr";
        }
    }

    SolrClient getServer() {
        if (serverType == TestServerType.USE_HTTPS) {
            return solrHttpsServer;
        } else if (serverType == TestServerType.USE_CLOUD) {
            return cloudFixture.solrClient;
        } else {
            return solrServer;
        }
    }

    static void createSolrFixtures() throws Exception {
        solrHttpsRunner = JettySolrFactory.createJettyTestFixture(true);
        httpsPort = solrHttpsRunner.getLocalPort();
        log.info("Started Https Test Server: " + solrHttpsRunner.getBaseUrl());
        solrHttpsServer = new HttpSolrServer("https://localhost:" + httpsPort + "/solr");
        solrHttpsServer.setConnectionTimeout(60000);

        solrRunner = JettySolrFactory.createJettyTestFixture(false);
        port = solrRunner.getLocalPort();

        solrServer = new HttpSolrServer("http://localhost:" + port + "/solr");

        log.info("Started Test Server: " + solrRunner.getBaseUrl());
        cloudFixture = new SolrCloudFixture("src/test/resources/solr");
    }

    public static void teardownSolrFixtures() throws Exception {
        if (solrRunner != null) {
            solrRunner.stop();
        }
        if (solrHttpsRunner != null) {
            solrHttpsRunner.stop();
        }
        if (cloudFixture != null) {
            cloudFixture.teardown();
        }
    }

    public static void clearIndex() throws SolrServerException, IOException {
        if (solrServer != null) {
            // Clear the Solr index.
            solrServer.deleteByQuery("*:*");
            solrServer.commit();
        }
        if (solrHttpsServer != null) {
            solrHttpsServer.deleteByQuery("*:*");
            solrHttpsServer.commit();
        }
        if (cloudFixture != null) {
            cloudFixture.solrClient.deleteByQuery("*:*");
            cloudFixture.solrClient.commit();
        }
    }
}
