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

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClientBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Perform operations against Apache Lucene Solr.
 */
@UriEndpoint(firstVersion = "4.8.0", scheme = "solr", title = "Solr", syntax = "solr:host:port/basePath", producerOnly = true,
             category = { Category.SEARCH, Category.MONITORING }, headersClass = SolrConstants.class)
public class SolrEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(SolrEndpoint.class);

    @UriParam
    private final SolrConfiguration configuration;

    private SolrClient solrClient;

    public SolrEndpoint(String uri, SolrComponent component, SolrConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public SolrConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() {
        return new SolrProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Cannot consume from a Solr endpoint: " + getEndpointUri());
    }

    @Override
    public String getServiceUrl() {
        return configuration.getSolrBaseUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "solr";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // preconfigured solr client
        if (solrClient == null && configuration.getSolrClient() == null) {
            // create solr client from config
            solrClient = createSolrClient();
            LOG.info("Starting SolrClient: {}",
                    getSolrClientInfoString(solrClient, isProcessAsync(solrClient, configuration), this.getEndpointUri()));
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        // stop solr client when created (not pre-configured)
        if (solrClient != null) {
            LOG.info("Stopping SolrClient: {}",
                    getSolrClientInfoString(solrClient, isProcessAsync(solrClient, configuration), this.getEndpointUri()));
            IOHelper.close(solrClient);
            solrClient = null;
        }
    }

    public SolrClient getSolrClient() {
        if (configuration.getSolrClient() != null) {
            return configuration.getSolrClient();
        }
        return solrClient;
    }

    public static String getSolrClientInfoString(SolrClient solrClient, boolean async, String endpointUri) {
        return String.format(
                "%s %s (async=%s; endpoint=%s)",
                solrClient.getClass().getSimpleName(),
                solrClient instanceof HttpJdkSolrClient httpJdkSolrClient
                        ? "@ " + httpJdkSolrClient.getBaseURL()
                        : "",
                async,
                URISupport.sanitizeUri(endpointUri));
    }

    public SolrClient createSolrClient() {
        final HttpJdkSolrClient.Builder builder = new HttpJdkSolrClient.Builder(configuration.getSolrBaseUrl());
        builder.withConnectionTimeout(configuration.getConnectionTimeout(), TimeUnit.MILLISECONDS);
        builder.withRequestTimeout(configuration.getRequestTimeout(), TimeUnit.MILLISECONDS);
        if (ObjectHelper.isNotEmpty(configuration.getUsername()) && ObjectHelper.isNotEmpty(configuration.getPassword())) {
            builder.withBasicAuthCredentials(configuration.getUsername(), configuration.getPassword());
        }
        if (ObjectHelper.isNotEmpty(configuration.getCertificatePath())) {
            builder.withSSLContext(createSslContextFromCa(getCamelContext(), configuration.getCertificatePath()));
        }
        if (configuration.getCollection() != null) {
            builder.withDefaultCollection(configuration.getCollection());
        }
        return builder.build();
    }

    protected static boolean isProcessAsync(SolrClient solrClient, SolrConfiguration configuration) {
        if (!(solrClient instanceof HttpSolrClientBase)) {
            return false;
        }
        return configuration.isAsync();
    }

    /**
     * An SSL context based on the self-signed CA, so that using this SSL Context allows to connect to the solr instance
     *
     * @return a customized SSL Context
     */
    private static SSLContext createSslContextFromCa(CamelContext camelContext, String certificatePath) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            InputStream resolveMandatoryResourceAsInputStream
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(
                            camelContext, certificatePath);
            Certificate trustedCa = factory.generateCertificate(resolveMandatoryResourceAsInputStream);
            KeyStore trustStore = KeyStore.getInstance("pkcs12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", trustedCa);
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
            TrustManagerFactory trustManagerFactory
                    = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
