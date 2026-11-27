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
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

/**
 * Represents the component that manages {@link ElasticsearchEndpoint}.
 */
@Component("elasticsearch")
public class ElasticsearchComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced", autowired = true,
              description = "To use an existing org.elasticsearch.client.RestClient, shared by all the endpoints of the"
                            + " component, instead of a client that each producer creates. The hostAddresses, timeout,"
                            + " authentication, SSL, sniffer and disconnect options are then not used, and the producers do not"
                            + " close the client.")
    private RestClient client;
    @Metadata(description = "The comma-separated list of the nodes to connect to, each as host:port, for example"
                            + " es1:9200,es2:9200. The port is 9200 if not set, and the scheme is https when enableSSL is set."
                            + " Required unless the client option is set.")
    private String hostAddresses;
    @Metadata(defaultValue = "" + ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT,
              description = "The socket timeout in milliseconds: the longest time that the client waits for data from a node"
                            + " before the request fails. Not used when the client option is set.")
    private int socketTimeout = ElasticsearchConstants.DEFAULT_SOCKET_TIMEOUT;
    @Metadata(defaultValue = "" + ElasticsearchConstants.MAX_RETRY_TIMEOUT,
              description = "Deprecated: this option has no effect. It was used by the old low-level REST client and is"
                            + " ignored by the current client.")
    private int maxRetryTimeout = ElasticsearchConstants.MAX_RETRY_TIMEOUT;
    @Metadata(defaultValue = "" + ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT,
              description = "The connect timeout in milliseconds: the longest time that the client waits to establish the"
                            + " connection to a node. Not used when the client option is set.")
    private int connectionTimeout = ElasticsearchConstants.DEFAULT_CONNECTION_TIMEOUT;
    @Metadata(defaultValue = "false",
              description = "Whether the body of Update is only the partial document to merge into the document. If false,"
                            + " the body is the request body of the Update API, such as {\"doc\": {...}} or a script. Only JSON"
                            + " text, bytes, a stream, a file or a Map is read this way. The CamelElasticsearchEnableDocumentOnlyMode"
                            + " header replaces this option.")
    private boolean enableDocumentOnlyMode;
    @Metadata(label = "security", security = "secret",
              description = "The user name for HTTP basic authentication to the nodes, used only together with password. It"
                            + " cannot be combined with apiKey. Not used when the client option is set.")
    private String user;
    @Metadata(label = "security", security = "secret",
              description = "The password for HTTP basic authentication to the nodes, used only together with user. Not used"
                            + " when the client option is set.")
    private String password;
    @Metadata(label = "security", security = "secret",
              description = "API key to authenticate with, sent in the Authorization header instead of a user and password."
                            + " Either the encoded key as Elasticsearch shows it when the key is created, or the id and the key"
                            + " separated by a colon (id:key). The key is only sent to the configured hosts and the nodes found"
                            + " by the sniffer.")
    private String apiKey;
    @Metadata(label = "security",
              description = "Whether to connect to the nodes of hostAddresses with HTTPS instead of HTTP. The default"
                            + " SSLContext of the JVM is used unless sslContextParameters or certificatePath is set.")
    private boolean enableSSL;
    @Metadata(label = "security",
              description = "The CA certificate, in X.509 format, that the client trusts for HTTPS instead of the default"
                            + " trust store of the JVM. It is loaded from the classpath by default, or with the prefix"
                            + " classpath:, file: or http:. It needs enableSSL, and sslContextParameters takes precedence.")
    private String certificatePath;
    @Metadata(label = "security",
              description = "To configure security using SSLContextParameters, which sets the protocols, cipher suites, named"
                            + " groups and signature schemes of the HTTPS connection. It takes precedence over certificatePath,"
                            + " and it needs enableSSL.")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false",
              description = "Whether to use the global SSLContextParameters of the CamelContext for an endpoint that has no"
                            + " sslContextParameters.")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced",
              description = "Whether to start the Sniffer of the Elasticsearch REST client, which reads the nodes of the"
                            + " cluster from its Nodes Info API at an interval and updates the nodes that the client uses. Not"
                            + " used when the client option is set or autowired, as Spring Boot does.")
    private boolean enableSniffer;
    @Metadata(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL,
              description = "The interval in milliseconds between two reads of the nodes of the cluster, when enableSniffer"
                            + " is set.")
    private int snifferInterval = ElasticsearchConstants.DEFAULT_SNIFFER_INTERVAL;
    @Metadata(label = "advanced", defaultValue = "" + ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY,
              description = "The delay in milliseconds of the read of the nodes that the Sniffer schedules after a failed"
                            + " request. The component does not register the client listener that reports failures to the"
                            + " Sniffer, so this option has no effect.")
    private int sniffAfterFailureDelay = ElasticsearchConstants.DEFAULT_AFTER_FAILURE_DELAY;

    public ElasticsearchComponent() {
        this(null);
    }

    public ElasticsearchComponent(CamelContext context) {
        super(context);
    }

    @Override
    @SuppressWarnings("deprecation") // copies the deprecated maxRetryTimeout option while it still exists
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ElasticsearchConfiguration config = new ElasticsearchConfiguration();
        config.setHostAddresses(this.getHostAddresses());
        config.setSocketTimeout(this.getSocketTimeout());
        config.setMaxRetryTimeout(this.getMaxRetryTimeout());
        config.setConnectionTimeout(this.getConnectionTimeout());
        config.setUser(this.getUser());
        config.setEnableSSL(this.isEnableSSL());
        config.setPassword(this.getPassword());
        config.setApiKey(this.getApiKey());
        config.setCertificatePath(this.getCertificatePath());
        config.setSslContextParameters(this.getSslContextParameters());
        config.setEnableSniffer(this.isEnableSniffer());
        config.setSnifferInterval(this.getSnifferInterval());
        config.setSniffAfterFailureDelay(this.getSniffAfterFailureDelay());
        config.setClusterName(remaining);
        config.setEnableDocumentOnlyMode(this.isEnableDocumentOnlyMode());

        // a user without a password is not used for basic authentication, so it does not clash with an apiKey
        if (ObjectHelper.isNotEmpty(config.getApiKey()) && ObjectHelper.isNotEmpty(config.getUser())
                && ObjectHelper.isNotEmpty(config.getPassword())) {
            throw new IllegalArgumentException(
                    "Both apiKey and user and password are configured on the elasticsearch component: use either apiKey, or user and password");
        }

        Endpoint endpoint = new ElasticsearchEndpoint(uri, this, config, client);
        setProperties(endpoint, parameters);
        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }
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

    public void setClient(RestClient client) {
        this.client = client;
    }

    public String getHostAddresses() {
        return hostAddresses;
    }

    public void setHostAddresses(String hostAddresses) {
        this.hostAddresses = hostAddresses;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isEnableSSL() {
        return enableSSL;
    }

    public void setEnableSSL(boolean enableSSL) {
        this.enableSSL = enableSSL;
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public boolean isEnableDocumentOnlyMode() {
        return enableDocumentOnlyMode;
    }

    public void setEnableDocumentOnlyMode(boolean enableDocumentOnlyMode) {
        this.enableDocumentOnlyMode = enableDocumentOnlyMode;
    }

    /**
     * @deprecated this option has no effect and is ignored by the current client
     */
    @Deprecated
    public int getMaxRetryTimeout() {
        return maxRetryTimeout;
    }

    @Deprecated
    public void setMaxRetryTimeout(int maxRetryTimeout) {
        this.maxRetryTimeout = maxRetryTimeout;
    }

    public boolean isEnableSniffer() {
        return enableSniffer;
    }

    public void setEnableSniffer(boolean enableSniffer) {
        this.enableSniffer = enableSniffer;
    }

    public int getSnifferInterval() {
        return snifferInterval;
    }

    public void setSnifferInterval(int snifferInterval) {
        this.snifferInterval = snifferInterval;
    }

    public int getSniffAfterFailureDelay() {
        return sniffAfterFailureDelay;
    }

    public void setSniffAfterFailureDelay(int sniffAfterFailureDelay) {
        this.sniffAfterFailureDelay = sniffAfterFailureDelay;
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
