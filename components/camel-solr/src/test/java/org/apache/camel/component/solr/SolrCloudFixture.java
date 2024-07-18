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
package org.apache.camel.component.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.camel.util.IOHelper;
import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.params.CollectionParams.CollectionAction;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.embedded.JettySolrRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrCloudFixture {

    private static final Logger LOG;
    private static final Path TEMP_DIR;

    /**
     * Create a temp dir under the maven target folder
     */
    static {
        LOG = LoggerFactory.getLogger(SolrCloudFixture.class);
        TEMP_DIR = Paths.get("target", "tmp");
        try {
            Files.createDirectories(TEMP_DIR);
            LOG.info("Created: {}", TEMP_DIR);
        } catch (IOException e) {
            LOG.error("Unable to create {}", TEMP_DIR, e);
        }

    }

    MiniSolrCloudCluster miniCluster;
    File testDir;
    SolrZkClient zkClient;

    CloudSolrClient solrClient;

    public SolrCloudFixture(String solrHome) throws Exception {
        String xml = IOHelper.loadText(new FileInputStream(new File(solrHome, "solr-no-core.xml")));
        miniCluster = new MiniSolrCloudCluster(1, "/solr", TEMP_DIR, xml, null, null);
        String zkAddr = miniCluster.getZkServer().getZkAddress();
        String zkHost = miniCluster.getZkServer().getZkHost();

        buildZooKeeper(zkHost, zkAddr, new File(solrHome), "solrconfig.xml", "schema.xml");
        List<JettySolrRunner> jettys = miniCluster.getJettySolrRunners();
        for (JettySolrRunner jetty : jettys) {
            if (!jetty.isRunning()) {
                LOG.warn("JETTY NOT RUNNING!");
            } else {
                LOG.info("JETTY RUNNING AT {}:{}", jetty.getBaseUrl(), jetty.getLocalPort());
            }
        }

        solrClient = new CloudHttp2SolrClient.Builder(Arrays.asList(zkAddr), Optional.empty())
                .withDefaultCollection("collection1")
                .build();
        solrClient.connect();

        createCollection(solrClient, "collection1", 1, 1, "conf1");
        Thread.sleep(1000); // takes some time to setup the collection...
                           // otherwise you'll get no live solr servers

        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", "1");

        solrClient.add(doc);
        solrClient.commit();
    }

    public static void putConfig(String confName, SolrZkClient zkClient, File solrhome, final String name)
            throws Exception {
        putConfig(confName, zkClient, solrhome, name, name);
    }

    protected NamedList<Object> createCollection(
            CloudSolrClient server, String name, int numShards,
            int replicationFactor, String configName)
            throws Exception {
        ModifiableSolrParams modParams = new ModifiableSolrParams();
        modParams.set(CoreAdminParams.ACTION, CollectionAction.CREATE.name());
        modParams.set("name", name);
        modParams.set("numShards", numShards);
        modParams.set("replicationFactor", replicationFactor);
        modParams.set("collection.configName", configName);
        QueryRequest request = new QueryRequest(modParams);
        request.setPath("/admin/collections");
        return server.request(request);
    }

    public static void putConfig(
            String confName, SolrZkClient zkClient, File solrhome, final String srcName,
            String destName)
            throws Exception {
        File file = new File(solrhome, "collection1" + File.separator + "conf" + File.separator + srcName);
        if (!file.exists()) {
            LOG.info("zk skipping {} because it doesn't exist", file.getAbsolutePath());
            return;
        }

        String destPath = "/configs/" + confName + "/" + destName;
        LOG.info("zk put {} to {}", file.getAbsolutePath(), destPath);
        zkClient.makePath(destPath, file.toPath(), false, true);
    }

    // static to share with distrib test
    public void buildZooKeeper(String zkHost, String zkAddress, File solrhome, String config, String schema)
            throws Exception {
        zkClient = new SolrZkClient.Builder()
                .withUrl(zkAddress)
                .withTimeout(60000, TimeUnit.MILLISECONDS)
                .build();
        Map<String, Object> props = new HashMap<>();
        props.put("configName", "conf1");

        // for now, always upload the config and schema to the canonical names
        putConfig("conf1", zkClient, solrhome, config, "solrconfig.xml");
        putConfig("conf1", zkClient, solrhome, schema, "schema.xml");

        putConfig("conf1", zkClient, solrhome, "stopwords.txt");
        putConfig("conf1", zkClient, solrhome, "stopwords_en.txt");
        putConfig("conf1", zkClient, solrhome, "protwords.txt");
        putConfig("conf1", zkClient, solrhome, "currency.xml");
        putConfig("conf1", zkClient, solrhome, "enumsConfig.xml");
        putConfig("conf1", zkClient, solrhome, "open-exchange-rates.json");
        putConfig("conf1", zkClient, solrhome, "mapping-ISOLatin1Accent.txt");
        putConfig("conf1", zkClient, solrhome, "old_synonyms.txt");
        putConfig("conf1", zkClient, solrhome, "synonyms.txt");
        putConfig("conf1", zkClient, solrhome, "elevate.xml");
        zkClient.close();
    }

    public void teardown() throws Exception {
        solrClient.close();
        miniCluster.shutdown();
        try (Stream<File> stream = Files.walk(TEMP_DIR).sorted(Comparator.reverseOrder()).map(Path::toFile)) {
            stream.forEach(File::delete);
        }
        solrClient = null;
        miniCluster = null;
    }

}
