package org.apache.camel.component.solr;

import java.io.File;
import java.util.SortedMap;

import org.apache.lucene.util.Constants;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.embedded.SSLConfig;
import org.apache.solr.client.solrj.impl.HttpClientConfigurer;
import org.eclipse.jetty.servlet.ServletHolder;

// Create embedded's Solrs for testing,
// based on SolrJettyTestBase
public class JettySolrFactory {

	public static File TEST_KEYSTORE = new File("./target/test-classes/solrtest.keystore");

	private static String TEST_KEYSTORE_PATH = TEST_KEYSTORE.getAbsolutePath();
	private static String TEST_KEYSTORE_PASSWORD = "secret";
	private static HttpClientConfigurer DEFAULT_CONFIGURER = new HttpClientConfigurer();

	private static SSLConfig buildSSLConfig(boolean useSsl,
			boolean sslClientAuth) {
		SSLConfig sslConfig = new SSLConfig(useSsl, false,
				TEST_KEYSTORE_PATH, TEST_KEYSTORE_PASSWORD,
				TEST_KEYSTORE_PATH, TEST_KEYSTORE_PASSWORD);
		return sslConfig;
	}

	private static JettySolrRunner createJetty(String solrHome,
			String configFile, String schemaFile, String context,
			boolean stopAtShutdown,
			SortedMap<ServletHolder, String> extraServlets, boolean ssl) throws Exception {
		// Set appropriate paths for Solr to use.
        System.setProperty("solr.solr.home", solrHome);
        System.setProperty("jetty.testMode", "true");
        System.setProperty("solr.data.dir", "target/test-classes/solr/data");

        // Instruct Solr to keep the index in memory, for faster testing.
        System.setProperty("solr.directoryFactory", "solr.RAMDirectoryFactory");


		SSLConfig sslConfig = buildSSLConfig(ssl, false); 
		
		context = context == null ? "/solr" : context;
		JettySolrRunner jetty = new JettySolrRunner(solrHome, context, 0, configFile,
				schemaFile, stopAtShutdown, extraServlets, sslConfig);

		jetty.start();
		int port = jetty.getLocalPort();
		return jetty;
	}
	
	public static JettySolrRunner createJettyTestFixture(boolean useSsl) throws Exception {
		String solrHome ="src/test/resources/solr";
		String configFile = null;
		String schemaFile = null;
		String context = "/solr";
		boolean stopAtShutdown = true;
		SortedMap<ServletHolder, String> extraServlets = null;
		
		return createJetty(solrHome, configFile, schemaFile, context, stopAtShutdown, extraServlets, useSsl);
		
	}

}
