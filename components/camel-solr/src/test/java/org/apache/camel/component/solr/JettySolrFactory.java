package org.apache.camel.component.solr;

import java.io.File;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.SortedMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.lucene.util.Constants;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.embedded.SSLConfig;
import org.apache.solr.client.solrj.impl.HttpClientConfigurer;
import org.eclipse.jetty.servlet.ServletHolder;

// Create embedded's Solrs for testing,
// based on SolrJettyTestBase
public class JettySolrFactory {

	public static File TEST_KEYSTORE = new File("./target/test-classes/solrtest.keystore");
	private static boolean mockedSslClient = false;
	private static int data_dir_no = 0;

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
	
	private static void installAllTrustingClientSsl() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		SSLContextBuilder builder = new SSLContextBuilder();
	    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
	    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
	            builder.build());
	    
//		// Create a trust manager that does not validate certificate chains
	    final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
	        @Override
	        public void checkClientTrusted( final X509Certificate[] chain, final String authType ) {
	        }
	        @Override
	        public void checkServerTrusted( final X509Certificate[] chain, final String authType ) {
	        }
	        @Override
	        public X509Certificate[] getAcceptedIssuers() {
	            return null;
	        }
	    } };
	    final SSLContext sslContext = SSLContext.getInstance( "TLS" );
	    sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
	    SSLContext.setDefault(sslContext);
	    
	    
//	    // Install the all-trusting trust manager
//	    final SSLContext sslContext = SSLContext.getInstance( "SSL" );
//	    sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
//	    // Create an ssl socket factory with our all-trusting manager
//	    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
//        HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
	}

	private static JettySolrRunner createJetty(String solrHome,
			String configFile, String schemaFile, String context,
			boolean stopAtShutdown,
			SortedMap<ServletHolder, String> extraServlets, boolean ssl) throws Exception {
		if (!mockedSslClient) {
			installAllTrustingClientSsl();
			mockedSslClient = true;
		}
		// Set appropriate paths for Solr to use.
        System.setProperty("solr.solr.home", solrHome);
        System.setProperty("jetty.testMode", "true");
        System.setProperty("solr.data.dir", "target/test-classes/solr/data" + (data_dir_no++));

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
		
		if (!useSsl) {
	        System.setProperty("tests.jettySsl", "false");
		} 
		
		return createJetty(solrHome, configFile, schemaFile, context, stopAtShutdown, extraServlets, useSsl);
		
	}

}
