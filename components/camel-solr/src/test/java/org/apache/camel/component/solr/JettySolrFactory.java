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
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.SortedMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.solr.client.solrj.embedded.JettySolrRunner;
import org.apache.solr.client.solrj.embedded.SSLConfig;
import org.eclipse.jetty.servlet.ServletHolder;

// Create embedded's Solrs for testing,
// based on SolrJettyTestBase
public final class JettySolrFactory {

    public static final File TEST_KEYSTORE = new File("./target/test-classes/solrtest.keystore");
    private static final String TEST_KEYSTORE_PATH = TEST_KEYSTORE.getAbsolutePath();
    private static final String TEST_KEYSTORE_PASSWORD = "secret";
    private static boolean mockedSslClient;
    private static int dataDirNo;
    
    private JettySolrFactory() {
        // Util classs
    }

    private static SSLConfig buildSSLConfig(boolean useSsl, boolean sslClientAuth) {
        SSLConfig sslConfig = new SSLConfig(useSsl, false, TEST_KEYSTORE_PATH, TEST_KEYSTORE_PASSWORD,
                                            TEST_KEYSTORE_PATH, TEST_KEYSTORE_PASSWORD);
        return sslConfig;
    }

    private static void installAllTrustingClientSsl() throws KeyManagementException,
        NoSuchAlgorithmException, KeyStoreException {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        
        // // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }};
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        SSLContext.setDefault(sslContext);

        // // Install the all-trusting trust manager
        // final SSLContext sslContext = SSLContext.getInstance( "SSL" );
        // sslContext.init( null, trustAllCerts, new
        // java.security.SecureRandom() );
        // // Create an ssl socket factory with our all-trusting manager
        // final SSLSocketFactory sslSocketFactory =
        // sslContext.getSocketFactory();
        // HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
    }

    private static JettySolrRunner createJetty(String solrHome, String configFile, String schemaFile,
                                               String context, boolean stopAtShutdown,
                                               SortedMap<ServletHolder, String> extraServlets, boolean ssl)
        throws Exception {
        if (!mockedSslClient) {
            installAllTrustingClientSsl();
            mockedSslClient = true;
        }
        // Set appropriate paths for Solr to use.
        System.setProperty("solr.solr.home", solrHome);
        System.setProperty("jetty.testMode", "true");
        System.setProperty("solr.data.dir", "target/test-classes/solr/data" + (dataDirNo++));

        // Instruct Solr to keep the index in memory, for faster testing.
        System.setProperty("solr.directoryFactory", "solr.RAMDirectoryFactory");

        SSLConfig sslConfig = buildSSLConfig(ssl, false);

        context = context == null ? "/solr" : context;
        JettySolrRunner jetty = new JettySolrRunner(solrHome, context, 0, configFile, schemaFile,
                                                    stopAtShutdown, extraServlets, sslConfig);

        jetty.start();
        
        return jetty;
    }

    public static JettySolrRunner createJettyTestFixture(boolean useSsl) throws Exception {
        String solrHome = "src/test/resources/solr";
        String configFile = solrHome + "/solr-no-core.xml";
        String schemaFile = solrHome + "/collection1/conf/schema.xml";
        String context = "/solr";
        boolean stopAtShutdown = true;
        SortedMap<ServletHolder, String> extraServlets = null;

        if (!useSsl) {
            System.setProperty("tests.jettySsl", "false");
        }

        return createJetty(solrHome, configFile, schemaFile, context, stopAtShutdown, extraServlets, useSsl);

    }

}
