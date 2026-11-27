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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.solr.client.solrj.SolrClient;

/**
 * Represents the component that manages {@link SolrEndpoint}.
 */
@Component("solr")
public class SolrComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced", autowired = true,
              description = "To use a custom org.apache.solr.client.solrj.SolrClient for all the endpoints, instead of a client"
                            + " that Camel creates for each endpoint. The solrClient option of an endpoint replaces it. Camel"
                            + " does not close a client that is set or autowired.")
    private SolrClient solrClient;
    @Metadata(description = "The host name of the Solr server, for the endpoints that set their host to default. If not set,"
                            + " these endpoints use localhost.")
    private String host;
    @Metadata(description = "The port of the Solr server, for the endpoints that set no port. If not set, these endpoints use"
                            + " 8983.")
    private int port;
    @Metadata(description = "The name of the collection, for the endpoints that set no collection.")
    private String defaultCollection;
    @Metadata(defaultValue = "" + SolrConstants.DEFAULT_REQUEST_TIMEOUT,
              description = "The time in milliseconds that the clients that Camel creates wait for the response to a request."
                            + " The requestTimeout option of an endpoint replaces it.")
    private long requestTimeout = SolrConstants.DEFAULT_REQUEST_TIMEOUT;
    @Metadata(defaultValue = "" + SolrConstants.DEFAULT_CONNECT_TIMEOUT,
              description = "The time in milliseconds that the clients that Camel creates wait to connect to Solr. The"
                            + " connectionTimeout option of an endpoint replaces it.")
    private long connectionTimeout = SolrConstants.DEFAULT_CONNECT_TIMEOUT;
    @Metadata(label = "security", security = "secret",
              description = "The user name for HTTP basic authentication with Solr, for all the endpoints. The username option"
                            + " of an endpoint replaces it.")
    private String username;
    @Metadata(label = "security", security = "secret",
              description = "The password for HTTP basic authentication with Solr, for all the endpoints. The password option"
                            + " of an endpoint replaces it.")
    private String password;
    @Metadata(label = "security",
              description = "Whether the clients that Camel creates connect to Solr with HTTPS instead of HTTP. The enableSSL"
                            + " option of an endpoint replaces it.")
    private boolean enableSSL;
    @Metadata(label = "security",
              description = "The TLS configuration (key store, trust store, protocols, cipher suites, named groups) of the"
                            + " clients that Camel creates, for all the endpoints. It is used when the enableSSL option is"
                            + " enabled. The sslContextParameters option of an endpoint replaces it.")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false",
              description = "Whether the endpoints use the global SSL context parameters of the CamelContext when neither the"
                            + " component nor the endpoint sets the sslContextParameters option.")
    private boolean useGlobalSslContextParameters;

    public SolrComponent() {
        this(null);
    }

    public SolrComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SolrConfiguration config = new SolrConfiguration();
        config.setSolrClient(this.getSolrClient());
        config.setHost(this.getHost());
        config.setPort(this.getPort());
        config.setCollection(this.getDefaultCollection());
        config.setRequestTimeout(this.getRequestTimeout());
        config.setConnectionTimeout(this.getConnectionTimeout());
        config.setEnableSSL(this.isEnableSSL());
        config.setUsername(this.getUsername());
        config.setPassword(this.getPassword());
        config.setSslContextParameters(this.getSslContextParameters());
        config.configure(uri);

        Endpoint endpoint = new SolrEndpoint(uri, this, config);
        setProperties(endpoint, parameters);

        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        // add collection from solrclient if it is not yet defined
        //     while it might be set on the solr client that could be set from parameters
        if (config.getCollection() == null && config.getSolrClient() != null) {
            config.setCollection(config.getSolrClient().getDefaultCollection());
        }

        return endpoint;
    }

    public SolrClient getSolrClient() {
        return solrClient;
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDefaultCollection() {
        return defaultCollection;
    }

    public void setDefaultCollection(String defaultCollection) {
        this.defaultCollection = defaultCollection;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

}
