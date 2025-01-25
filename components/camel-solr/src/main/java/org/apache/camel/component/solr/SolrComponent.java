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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.solr.client.solrj.SolrClient;

/**
 * Represents the component that manages {@link SolrEndpoint}.
 */
@Component("solr")
public class SolrComponent extends DefaultComponent {

    @Metadata(label = "advanced", autowired = true)
    private SolrClient solrClient;
    @Metadata
    private String host;
    @Metadata
    private int port;
    @Metadata
    private String defaultCollection;
    @Metadata(defaultValue = "" + SolrConstants.DEFAULT_REQUEST_TIMEOUT)
    private long requestTimeout = SolrConstants.DEFAULT_REQUEST_TIMEOUT;
    @Metadata(defaultValue = "" + SolrConstants.DEFAULT_CONNECT_TIMEOUT)
    private long connectionTimeout = SolrConstants.DEFAULT_CONNECT_TIMEOUT;
    @Metadata(label = "security", secret = true)
    private String username;
    @Metadata(label = "security", secret = true)
    private String password;
    @Metadata(label = "security")
    private boolean enableSSL;

    public SolrComponent() {
        this(null);
    }

    public SolrComponent(CamelContext context) {
        super(context);
        registerExtension(new SolrComponentVerifierExtension());
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
        config.configure(uri);

        Endpoint endpoint = new SolrEndpoint(uri, this, config);
        setProperties(endpoint, parameters);

        // add collection from solrclient if it is not yet defined
        //     while it might be set on the solr client that could be set from parameters
        if (config.getCollection() == null && config.getSolrClient() != null) {
            config.setCollection(config.getSolrClient().getDefaultCollection());
        }

        return endpoint;
    }

    /**
     * To use an existing configured solr client, instead of creating a client per endpoint. This allows customizing the
     * client with specific advanced settings.
     */
    public SolrClient getSolrClient() {
        return solrClient;
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    /**
     * The solr instance host name
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * The solr instance port number
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Solr default collection name
     */
    public String getDefaultCollection() {
        return defaultCollection;
    }

    public void setDefaultCollection(String defaultCollection) {
        this.defaultCollection = defaultCollection;
    }

    /**
     * The timeout in ms to wait before the socket will time out.
     */
    public long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    /**
     * The time in ms to wait before connection will time out.
     */
    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Basic authenticate user
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Password for authenticating
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Enable SSL
     */
    public boolean isEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

}
