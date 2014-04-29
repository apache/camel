package org.apache.camel.component.solr;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.cloud.AbstractZkTestCase;
import org.apache.solr.cloud.ZkTestServer;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.servlet.SolrDispatchFilter;
import org.apache.zookeeper.CreateMode;

public class SolrCloudFixture {

	File testDir;
	ZkTestServer zkServer;
	JettySolrRunner jetty1;
	AtomicInteger nodeCnt;

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
	}

	public static void putConfig(String confName, SolrZkClient zkClient,
			File solrhome, final String name) throws Exception {
		putConfig(confName, zkClient, solrhome, name, name);
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
		SolrZkClient zkClient = new SolrZkClient(zkHost, 60000);
		zkClient.makePath("/solr", false, true);
		zkClient.close();

		zkClient = new SolrZkClient(zkAddress, 60000);

		Map<String, Object> props = new HashMap<String, Object>();
		props.put("configName", "conf1");
		final ZkNodeProps zkProps = new ZkNodeProps(props);

		zkClient.makePath("/collections/collection1",
				ZkStateReader.toJSON(zkProps), CreateMode.PERSISTENT, true);
		zkClient.makePath("/collections/collection1/shards",
				CreateMode.PERSISTENT, true);
		zkClient.makePath("/collections/control_collection",
				ZkStateReader.toJSON(zkProps), CreateMode.PERSISTENT, true);
		zkClient.makePath("/collections/control_collection/shards",
				CreateMode.PERSISTENT, true);

		// for now, always upload the config and schema to the canonical names
		putConfig("conf1", zkClient, solrhome, config, "solrconfig.xml");
		putConfig("conf1", zkClient, solrhome, schema, "schema.xml");

		putConfig("conf1", zkClient, solrhome,
				"solrconfig.snippet.randomindexconfig.xml");
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

	public JettySolrRunner createJetty(File solrHome, String dataDir,
			String shardList, String solrConfigOverride, String schemaOverride,
			boolean explicitCoreNodeName) throws Exception {

		boolean stopAtShutdown = true;
		JettySolrRunner jetty = new JettySolrRunner(solrHome.getAbsolutePath(),
				"/solr", 0, solrConfigOverride, schemaOverride, stopAtShutdown,
				null, null, null);
		jetty.setShards(shardList);
		jetty.setDataDir(dataDir);
		if (explicitCoreNodeName) {
			jetty.setCoreNodeName(Integer.toString(nodeCnt.get()));
		}
		jetty.start();
		log.info("Test SolrCloud Jetty started at: " + jetty.getBaseUrl());

		return jetty;
	}

	public SolrCloudFixture(String solrHome) throws Exception {

		nodeCnt = new AtomicInteger(1);
		testDir = new File(TEMP_DIR, getClass().getName() + "-"
				+ System.currentTimeMillis());
		testDir.mkdirs();
		String zkDir = testDir.getAbsolutePath() + File.separator
				+ "zookeeper/server1/data";
		zkServer = new ZkTestServer(zkDir);
		zkServer.run();

		System.setProperty("solrcloud.skip.autorecovery", "true");
		System.setProperty("zkHost", zkServer.getZkAddress());
		System.setProperty("jetty.port", "0000");
		System.setProperty("solr.test.sys.prop1", "propone");
		System.setProperty("solr.test.sys.prop2", "proptwo");

		File solrHomeFile = new File(solrHome);
		buildZooKeeper(zkServer.getZkHost(), zkServer.getZkAddress(),
				solrHomeFile, "solrconfig.xml", "schema.xml");

		int aShard = nodeCnt.get();
		String shardName = "shard" + aShard;
		jetty1 = createJetty(solrHomeFile, testDir.getAbsolutePath() + "/shard"
				+ aShard + "/data", shardName, "solrconfig.xml", "schema.xml",
				true);

		log.info("Waiting for leader for shard: " + shardName);
		ZkStateReader zkStateReader = ((SolrDispatchFilter) jetty1
				.getDispatchFilter().getFilter()).getCores().getZkController()
				.getZkStateReader();
		zkStateReader.getLeaderRetry("collection1", shardName, 15000);

		zkStateReader.updateClusterState(true);
		ClusterState clusterState = zkStateReader.getClusterState();
		Map<String, Slice> slices = clusterState.getSlicesMap("collection1");

		solrClient = new CloudSolrServer(zkServer.getZkAddress());
		solrClient.setDefaultCollection("collection1");
		solrClient.deleteByQuery("*:*");
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
