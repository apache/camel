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
package org.apache.camel.component.es;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Represents the component that manages {@link ElasticsearchEndpoint}.
 */
@Component("elasticsearch")
public class ElasticsearchComponent extends DefaultComponent {

    @Metadata(label = "advanced", autowired = true)
    private RestClient client;
    @Metadata
    private String hostAddresses;
    @Metadata(defaultValue = "" + ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT)
    private int socketTimeout = ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT;
    @Metadata(defaultValue = "" + ElasticsearchConstants.MAX_RETRY_TIMEOUT)
    private int maxRetryTimeout = ElasticsearchConstants.MAX_RETRY_TIMEOUT;
    @Metadata(defaultValue = "" + ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT)
    private int connectionTimeout = ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT;
    @Metadata(defaultValue = "false")
    private boolean enableDocumentOnlyMode;
    @Metadata(label = "security", secret = true)
    private String user;
    @Metadata(label = "security", secret = true)
    private String password;
    @Metadata(label = "security")
    private boolean enableSSL;
    @Metadata(label = "security")
    private String certificatePath;
    @Metadata(label = "advanced")
    private boolean enableSniffer;
    @Metadata(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL)
    private int snifferInterval = ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL;
    @Metadata(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY)
    private int sniffAfterFailureDelay = ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY;

    public ElasticsearchComponent() {
        this(null);
    }

    public ElasticsearchComponent(CamelContext context) {
        super(context);
        registerExtension(new ElasticsearchComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ElasticsearchConfiguration config = new ElasticsearchConfiguration();
        config.setHostAddresses(this.getHostAddresses());
        config.setSocketTimeout(this.getSocketTimeout());
        config.setMaxRetryTimeout(this.getMaxRetryTimeout());
        config.setConnectionTimeout(this.getConnectionTimeout());
        config.setUser(this.getUser());
        config.setEnableSSL(this.isEnableSSL());
        config.setPassword(this.getPassword());
        config.setCertificatePath(this.getCertificatePath());
        config.setEnableSniffer(this.isEnableSniffer());
        config.setSnifferInterval(this.getSnifferInterval());
        config.setSniffAfterFailureDelay(this.getSniffAfterFailureDelay());
        config.setClusterName(remaining);
        config.setEnableDocumentOnlyMode(this.isEnableDocumentOnlyMode());

        Endpoint endpoint = new ElasticsearchEndpoint(uri, this, config, client);
        setProperties(endpoint, parameters);
        config.setHostAddressesList(parseHostAddresses(config.getHostAddresses(), config));

        return endpoint;
    }

    private List<HttpHost> parseHostAddresses(String ipsString, ElasticsearchConfiguration config) {
        if (ipsString == null || ipsString.isEmpty()) {
            return null;
        }
        List<String> addressesStr = Arrays.asList(ipsString.split(","));
        List<HttpHost> addressesTrAd = new ArrayList<>(addressesStr.size());
        for (String address : addressesStr) {
            String[] split = address.split(":");
            String hostname;
            if (split.length > 0) {
                hostname = split[0];
            } else {
                throw new IllegalArgumentException();
            }
            int port = split.length > 1 ? Integer.parseInt(split[1]) : ElasticsearchConstants.DEFAULT_PORT;
            addressesTrAd.add(new HttpHost(hostname, port, config.isEnableSSL() ? "HTTPS" : "HTTP"));
        }
        return addressesTrAd;
    }

    public RestClient getClient() {
        return client;
    }

    /**
     * To use an existing configured Elasticsearch client, instead of creating a client per endpoint. This allow to
     * customize the client with specific settings.
     */
    public void setClient(RestClient client) {
        this.client = client;
    }

    /**
     * Comma separated list with ip:port formatted remote transport addresses to use. The ip and port options must be
     * left blank for hostAddresses to be considered instead.
     */
    public String getHostAddresses() {
        return hostAddresses;
    }

    public void setHostAddresses(String hostAddresses) {
        this.hostAddresses = hostAddresses;
    }

    /**
     * The timeout in ms to wait before the socket will timeout.
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    /**
     * The time in ms to wait before connection will timeout.
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Basic authenticate user
     */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Password for authenticate
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnableSSL() {
        return enableSSL;
    }

    /**
     * Enable SSL
     */
    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    /**
     * The path of the self-signed certificate to use to access to Elasticsearch.
     */
    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    /**
     * Indicates whether the body of the message contains only documents. By default, it is set to false to be able to
     * do the same requests as what the Document API supports (see
     * https://www.elastic.co/guide/en/elasticsearch/reference/current/docs.html for more details). To ease the
     * migration of routes based on the legacy component camel-elasticsearch-rest, you should consider enabling the mode
     * especially if your routes do update operations.
     */
    public boolean isEnableDocumentOnlyMode() {
        return enableDocumentOnlyMode;
    }

    public void setEnableDocumentOnlyMode(boolean enableDocumentOnlyMode) {
        this.enableDocumentOnlyMode = enableDocumentOnlyMode;
    }

    /**
     * The time in ms before retry
     */
    public int getMaxRetryTimeout() {
        return maxRetryTimeout;
    }

    public void setMaxRetryTimeout(int maxRetryTimeout) {
        this.maxRetryTimeout = maxRetryTimeout;
    }

    public boolean isEnableSniffer() {
        return enableSniffer;
    }

    /**
     * Enable automatically discover nodes from a running Elasticsearch cluster. If this option is used in conjunction
     * with Spring Boot then it's managed by the Spring Boot configuration (see: Disable Sniffer in Spring Boot).
     */
    public void setEnableSniffer(boolean enableSniffer) {
        this.enableSniffer = enableSniffer;
    }

    /**
     * The interval between consecutive ordinary sniff executions in milliseconds. Will be honoured when sniffOnFailure
     * is disabled or when there are no failures between consecutive sniff executions
     */
    public int getSnifferInterval() {
        return snifferInterval;
    }

    public void setSnifferInterval(int snifferInterval) {
        this.snifferInterval = snifferInterval;
    }

    /**
     * The delay of a sniff execution scheduled after a failure (in milliseconds)
     */
    public int getSniffAfterFailureDelay() {
        return sniffAfterFailureDelay;
    }

    public void setSniffAfterFailureDelay(int sniffAfterFailureDelay) {
        this.sniffAfterFailureDelay = sniffAfterFailureDelay;
    }

}
