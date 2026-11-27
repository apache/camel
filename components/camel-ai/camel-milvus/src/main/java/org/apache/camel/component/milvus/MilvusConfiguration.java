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
package org.apache.camel.component.milvus;

import io.milvus.client.MilvusClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class MilvusConfiguration implements Cloneable {

    @UriParam(description = "The action to perform. The CamelMilvusAction header, when set, wins over this option.")
    private MilvusAction action;

    @Metadata(defaultValue = "localhost")
    @UriParam(description = "The host name or IP address of the Milvus server."
                            + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private String host = "localhost";

    @Metadata(defaultValue = "19530")
    @UriParam(description = "The gRPC port of the Milvus server."
                            + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private int port = 19530;

    @Metadata(security = "secret")
    @UriParam(label = "security",
              description = "The token that the client sends to authenticate with Milvus: a user name and password joined"
                            + " by a colon, or an API key of Zilliz Cloud. If not set, the client sends"
                            + " no credentials."
                            + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private String token;

    @UriParam(description = "The timeout in milliseconds for connecting to the Milvus server, 10000 if not set. The value"
                            + " must be greater than 0. It is not a timeout of each request: the requests have no deadline."
                            + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private long timeout = 10000L;

    @UriParam(label = "security", description = "Whether to connect to the Milvus server with TLS. If not set, the"
                                                + " connection is plaintext, and the token is sent in clear text, unless"
                                                + " serverPemPath, or caPemPath with clientPemPath and clientKeyPath, is"
                                                + " set. Without these options, the client trusts the certificate"
                                                + " authorities of the JVM."
                                                + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private boolean secure;

    @UriParam(label = "security", description = "The path of a PEM file with the certificate of the Milvus server, or of"
                                                + " the certificate authority that signed it, that the client trusts for"
                                                + " one-way TLS. Setting it connects with TLS."
                                                + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private String serverPemPath;

    @UriParam(label = "security", description = "The name that the client expects in the certificate of the Milvus server"
                                                + " (the authority of the gRPC channel), when it differs from the host."
                                                + " Only used with serverPemPath, where the host is used if not set,"
                                                + " or with mutual TLS."
                                                + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private String serverName;

    @UriParam(label = "security", description = "The path of a PEM file with the certificate authority that the client"
                                                + " trusts for mutual TLS. Set it together with clientPemPath and"
                                                + " clientKeyPath; for one-way TLS use serverPemPath instead."
                                                + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private String caPemPath;

    @UriParam(label = "security", description = "The path of a PEM file with the certificate of the client for mutual TLS."
                                                + " Set it together with caPemPath and clientKeyPath."
                                                + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private String clientPemPath;

    @UriParam(label = "security", description = "The path of a PEM file with the private key (PKCS#8) of the client for"
                                                + " mutual TLS. Set it together with caPemPath and clientPemPath."
                                                + " Not used when a MilvusClient is set with MilvusConfiguration.setClient().")
    private String clientKeyPath;

    @Metadata(autowired = true)
    private MilvusClient client;

    public MilvusAction getAction() {
        return action;
    }

    public void setAction(MilvusAction action) {
        this.action = action;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getServerPemPath() {
        return serverPemPath;
    }

    public void setServerPemPath(String serverPemPath) {
        this.serverPemPath = serverPemPath;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getCaPemPath() {
        return caPemPath;
    }

    public void setCaPemPath(String caPemPath) {
        this.caPemPath = caPemPath;
    }

    public String getClientPemPath() {
        return clientPemPath;
    }

    public void setClientPemPath(String clientPemPath) {
        this.clientPemPath = clientPemPath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public void setClientKeyPath(String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }

    public MilvusClient getClient() {
        return client;
    }

    /**
     * Reference to a `io.milvus.client.MilvusClient`.
     */
    public void setClient(MilvusClient client) {
        this.client = client;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public MilvusConfiguration copy() {
        try {
            return (MilvusConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
