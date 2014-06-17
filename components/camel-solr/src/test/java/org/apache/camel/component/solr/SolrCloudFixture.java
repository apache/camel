package org.apache.camel.component.solr;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.cloud.AbstractZkTestCase;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.cloud.ZkTestServer;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.CollectionParams.CollectionAction;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.servlet.SolrDispatchFilter;
import org.apache.zookeeper.CreateMode;

/**
 * 
 */
public class SolrCloudFixture {

	MiniSolrCloudCluster miniCluster;
	File testDir;
	SolrZkClient zkClient = null;

	CloudSolrServer solrClient = null;

	static Logger log = Logger.getLogger(SolrCloudFixture.class);
	// /** create indexes in this directory, optimally use a subdir, named after
	// the test */
	/**
	 * Create indexes in this directory, optimally use a subdir, named after the
	 * test
	 */
	public static final File TEMP_DIR;
	static {
		String s = System.getProperty("tempDir",
				System.getProperty("java.io.tmpdir"));
		if (s == null)
			throw new RuntimeException(
					"To run tests, you need to define system property 'tempDir' or 'java.io.tmpdir'.");
		TEMP_DIR = new File(s);
		TEMP_DIR.mkdirs();
		System.out.println("Created: " + TEMP_DIR.getAbsolutePath());
	}

	public static void putConfig(String confName, SolrZkClient zkClient,
			File solrhome, final String name) throws Exception {
		putConfig(confName, zkClient, solrhome, name, name);
	}

	protected NamedList<Object> createCollection(CloudSolrServer server,
			String name, int numShards, int replicationFactor, String configName)
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

	public static void putConfig(String confName, SolrZkClient zkClient,
			File solrhome, final String srcName, String destName)
			throws Exception {
		File file = new File(solrhome, "collection1" + File.separator + "conf"
				+ File.separator + srcName);
		if (!file.exists()) {
			log.info("zk skipping " + file.getAbsolutePath()
					+ " because it doesn't exist");
			return;
		}

		String destPath = "/configs/" + confName + "/" + destName;
		log.info("zk put " + file.getAbsolutePath() + " to " + destPath);
		zkClient.makePath(destPath, file, false, true);
	}

	// static to share with distrib test
	public void buildZooKeeper(String zkHost, String zkAddress, File solrhome,
			String config, String schema) throws Exception {
		zkClient = new SolrZkClient(zkAddress, 60000);

		Map<String, Object> props = new HashMap<String, Object>();
		props.put("configName", "conf1");
		final ZkNodeProps zkProps = new ZkNodeProps(props);

		// zkClient.makePath("/collections/collection1",
		// ZkStateReader.toJSON(zkProps), CreateMode.PERSISTENT, true);
		// zkClient.makePath("/collections/collection1/shards",
		// CreateMode.PERSISTENT, true);
		// zkClient.makePath("/collections/control_collection",
		// ZkStateReader.toJSON(zkProps), CreateMode.PERSISTENT, true);
		// zkClient.makePath("/collections/control_collection/shards",
		// CreateMode.PERSISTENT, true);

		// for now, always upload the config and schema to the canonical names
		putConfig("conf1", zkClient, solrhome, config, "solrconfig.xml");
		putConfig("conf1", zkClient, solrhome, schema, "schema.xml");

		// putConfig("conf1", zkClient, solrhome,
		// "solrconfig.snippet.randomindexconfig.xml");
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

	public SolrCloudFixture(String solrHome) throws Exception {

		// String testHome = SolrTestCaseJ4.TEST_HOME();
		// miniCluster = new MiniSolrCloudCluster(NUM_SERVERS, null, new
		// File(testHome, "solr-no-core.xml"),
		// null, null);
		miniCluster = new MiniSolrCloudCluster(1, "/solr", new File(solrHome,
				"solr-no-core.xml"), null, null);
		String zkAddr = miniCluster.getZkServer().getZkAddress();
		String zkHost = miniCluster.getZkServer().getZkHost();

		buildZooKeeper(zkHost, zkAddr, new File(solrHome), "solrconfig.xml",
				"schema.xml");
		List<JettySolrRunner> jettys = miniCluster.getJettySolrRunners();
		for (JettySolrRunner jetty : jettys) {
			if (!jetty.isRunning()) {
				System.out.println("JETTY NOT RUNNING!");
			} else {
				// jetty.stop();
				// jetty.start();
				System.out.println("JETTY RUNNING");
				System.out.println("AT:  " + jetty.getBaseUrl());
				System.out.println("PORTT" + jetty.getLocalPort());
			}
		}

		solrClient = new CloudSolrServer(zkAddr, true);
		solrClient.connect();

		createCollection(solrClient, "collection1", 1, 1, "conf1");
		solrClient.setDefaultCollection("collection1");

		SolrInputDocument doc = new SolrInputDocument();
		doc.setField("id", "1");

		solrClient.add(doc);
		solrClient.commit();
	}

	public void teardown() {
		System.clearProperty("zkHost");
		System.clearProperty("solr.test.sys.prop1");
		System.clearProperty("solr.test.sys.prop2");
		System.clearProperty("solrcloud.skip.autorecovery");
		System.clearProperty("jetty.port");

	}

}
