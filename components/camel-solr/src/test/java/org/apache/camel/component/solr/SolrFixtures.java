package org.apache.camel.component.solr;

import java.io.IOException;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

;

public class SolrFixtures {
	static Logger log = Logger.getLogger(SolrFixtures.class);

	private static JettySolrRunner solrRunner;
	private static JettySolrRunner solrHttpsRunner;
	private static HttpSolrServer solrServer;
	private static HttpSolrServer solrHttpsServer;
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
			String zkHostStr = cloudFixture.miniCluster.getZkServer()
					.getZkHost();
			return "solrCloud://localhost:" + httpsPort + "/solr?zkHost="
					+ zkHostStr + "&collection=collection1";
		} else {
			return "solr://localhost:" + port + "/solr";
		}
	}

	SolrServer getServer() {
		if (serverType == TestServerType.USE_HTTPS) {
			return this.solrHttpsServer;
		} else if (serverType == TestServerType.USE_CLOUD) {
			return this.cloudFixture.solrClient;
		} else {
			return this.solrServer;
		}
	}

	static void createSolrFixtures() throws Exception {
		solrHttpsRunner = JettySolrFactory.createJettyTestFixture(true);
		httpsPort = solrHttpsRunner.getLocalPort();
		log.info("Started Https Test Server: " + solrHttpsRunner.getBaseUrl());
		solrHttpsServer = new HttpSolrServer("https://localhost:" + httpsPort
				+ "/solr");
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
