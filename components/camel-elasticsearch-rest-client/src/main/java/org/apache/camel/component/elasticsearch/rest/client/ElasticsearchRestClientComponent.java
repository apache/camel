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

package org.apache.camel.component.elasticsearch.rest.client;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.elasticsearch.client.RestClient;

@Component("elasticsearch-rest-client")
public class ElasticsearchRestClientComponent extends DefaultComponent {

    @Metadata(label = "advanced", autowired = true)
    RestClient restClient;

    @Metadata
    String hostAddressesList;

    @Metadata(defaultValue = "" + ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT)
    private int connectionTimeout = ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT;

    @Metadata(defaultValue = "" + ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT)
    private int socketTimeout = ElasticSearchRestClientConstant.SOCKET_CONNECTION_TIMEOUT;

    @Metadata(label = "security", secret = true)
    private String user;

    @Metadata(label = "security", secret = true)
    private String password;

    @Metadata(label = "security", supportFileReference = true)
    private String certificatePath;

    @Metadata(label = "advanced")
    private boolean enableSniffer;

    @Metadata(
            label = "advanced",
            defaultValue = "" + ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY)
    private int snifferInterval = ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY;

    @Metadata(
            label = "advanced",
            defaultValue = "" + ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY)
    private int sniffAfterFailureDelay = ElasticSearchRestClientConstant.SNIFFER_INTERVAL_AND_FAILURE_DELAY;

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.isBlank()) {
            throw new IllegalArgumentException("Cluster Name must be specified.");
        }

        ElasticsearchRestClientEndpoint endpoint = new ElasticsearchRestClientEndpoint(uri, this);
        endpoint.setRestClient(restClient);
        endpoint.setHostAddressesList(hostAddressesList);
        endpoint.setConnectionTimeout(connectionTimeout);
        endpoint.setSocketTimeout(socketTimeout);
        endpoint.setUser(user);
        endpoint.setPassword(password);
        endpoint.setCertificatePath(certificatePath);
        endpoint.setEnableSniffer(enableSniffer);
        endpoint.setSnifferInterval(snifferInterval);
        endpoint.setSniffAfterFailureDelay(sniffAfterFailureDelay);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Rest Client of type org.elasticsearch.client.RestClient. This is only for advanced usage
     */
    public RestClient getRestClient() {
        return restClient;
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * List of host Addresses, multiple hosts can be separated by comma.
     */
    public String getHostAddressesList() {
        return hostAddressesList;
    }

    public void setHostAddressesList(String hostAddressesList) {
        this.hostAddressesList = hostAddressesList;
    }

    /**
     * Connection timeout
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Socket timeout
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    /**
     * Username
     */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Password
     */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Certificate Path
     */
    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    /**
     * Enabling Sniffer
     */
    public boolean isEnableSniffer() {
        return enableSniffer;
    }

    public void setEnableSniffer(boolean enableSniffer) {
        this.enableSniffer = enableSniffer;
    }

    /**
     * Sniffer interval (in millis)
     */
    public int getSnifferInterval() {
        return snifferInterval;
    }

    public void setSnifferInterval(int snifferInterval) {
        this.snifferInterval = snifferInterval;
    }

    /**
     * Sniffer after failure delay (in millis)
     */
    public int getSniffAfterFailureDelay() {
        return sniffAfterFailureDelay;
    }

    public void setSniffAfterFailureDelay(int sniffAfterFailureDelay) {
        this.sniffAfterFailureDelay = sniffAfterFailureDelay;
    }
}
