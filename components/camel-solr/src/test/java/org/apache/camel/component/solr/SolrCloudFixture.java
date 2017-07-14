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

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.IOHelper;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.params.CollectionParams.CollectionAction;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

/**
 * 
 */
public class SolrCloudFixture {
    
    /**
     * Create indexes in this directory, optimally use a subdir, named after the
     * test
     */
    public static final File TEMP_DIR;
    static {
        String s = System.getProperty("tempDir", System.getProperty("java.io.tmpdir"));
        if (s == null) {
            throw new RuntimeException("To run tests, you need to define system property 'tempDir' or 'java.io.tmpdir'.");
        }
        TEMP_DIR = new File(s);
        TEMP_DIR.mkdirs();
        System.out.println("Created: " + TEMP_DIR.getAbsolutePath());
    }
    
    static Logger log = Logger.getLogger(SolrCloudFixture.class);
    

    MiniSolrCloudCluster miniCluster;
    File testDir;
    SolrZkClient zkClient;

    CloudSolrClient solrClient;
    
    public SolrCloudFixture(String solrHome) throws Exception {
        String xml = IOHelper.loadText(new FileInputStream(new File(solrHome, "solr-no-core.xml")));
        miniCluster = new MiniSolrCloudCluster(1, "/solr", new File("target/tmp").toPath(), xml, null, null);
        String zkAddr = miniCluster.getZkServer().getZkAddress();
        String zkHost = miniCluster.getZkServer().getZkHost();

        buildZooKeeper(zkHost, zkAddr, new File(solrHome), "solrconfig.xml", "schema.xml");
        List<JettySolrRunner> jettys = miniCluster.getJettySolrRunners();
        for (JettySolrRunner jetty : jettys) {
            if (!jetty.isRunning()) {
                log.warn("JETTY NOT RUNNING!");
            } else {
                log.info("JETTY RUNNING AT " + jetty.getBaseUrl() + " PORT " + jetty.getLocalPort());
            }
        }

        solrClient = new CloudSolrClient(zkAddr, true);
        solrClient.connect();

        createCollection(solrClient, "collection1", 1, 1, "conf1");
        Thread.sleep(1000); // takes some time to setup the collection...
                            // otherwise you'll get no live solr servers
        solrClient.setDefaultCollection("collection1");

        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", "1");

        solrClient.add(doc);
        solrClient.commit();
    }

    public static void putConfig(String confName, SolrZkClient zkClient, File solrhome, final String name)
        throws Exception {
        putConfig(confName, zkClient, solrhome, name, name);
    }

    protected NamedList<Object> createCollection(CloudSolrClient server, String name, int numShards,
                                                 int replicationFactor, String configName) throws Exception {
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

    public static void putConfig(String confName, SolrZkClient zkClient, File solrhome, final String srcName,
                                 String destName) throws Exception {
        File file = new File(solrhome, "collection1" + File.separator + "conf" + File.separator + srcName);
        if (!file.exists()) {
            log.info("zk skipping " + file.getAbsolutePath() + " because it doesn't exist");
            return;
        }

        String destPath = "/configs/" + confName + "/" + destName;
        log.info("zk put " + file.getAbsolutePath() + " to " + destPath);
        zkClient.makePath(destPath, file, false, true);
    }

    // static to share with distrib test
    public void buildZooKeeper(String zkHost, String zkAddress, File solrhome, String config, String schema)
        throws Exception {
        zkClient = new SolrZkClient(zkAddress, 60000);

        Map<String, Object> props = new HashMap<String, Object>();
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

        solrClient = null;
        miniCluster = null;
    }

}
