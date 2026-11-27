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
package org.apache.camel.component.consul;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.*;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.option.ConsistencyMode;

@UriParams
public abstract class ConsulClientConfiguration implements Cloneable {
    @UriParam(description = "The URL of the Consul agent that the client connects to, such as http://localhost:8500 (the"
                            + " default of the Consul client when not set). Use an https URL to connect with"
                            + " sslContextParameters.")
    private String url;
    @UriParam(label = "advanced",
              description = "The datacenter to query instead of the datacenter of the agent. Used by the catalog and health"
                            + " queries, the agent DEREGISTER action and the coordinates NODES action; the"
                            + " CamelConsulDatacenter header overrides it.")
    private String datacenter;
    @UriParam(label = "advanced",
              description = "The name of a node (or _agent for the node of the agent) from which Consul sorts the results of"
                            + " the catalog and health queries by estimated round trip time. The CamelConsulNearNode header"
                            + " overrides it.")
    private String nearNode;
    @UriParam(label = "advanced",
              description = "Comma separated node metadata filters, each as key:value, so that the catalog and health"
                            + " queries return only the nodes with that metadata. The CamelConsulNodeMeta header overrides"
                            + " it.")
    private String nodeMeta;
    @UriParam(label = "advanced", defaultValue = "DEFAULT", enums = "DEFAULT,STALE,CONSISTENT",
              description = "The consistency mode of the catalog and health queries: DEFAULT (the leader answers), STALE"
                            + " (any server answers, possibly with outdated data) or CONSISTENT (the leader first checks"
                            + " with a quorum that it is still the leader). The CamelConsulConsistencyMode header overrides"
                            + " it.")
    private ConsistencyMode consistencyMode = ConsistencyMode.DEFAULT;
    @UriParam(javaType = "java.lang.String",
              description = "Comma separated tags. The consul endpoints do not use this option, so setting it has no"
                            + " effect.")
    private String tags;

    @UriParam(label = "security",
              description = "The SSL configuration of the HTTPS connection to the Consul agent. When not set, the global"
                            + " SSL context parameters are used if the component enables useGlobalSslContextParameters.")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "security", security = "secret",
              description = "The ACL token that the client sends with each request to Consul.")
    private String aclToken;
    @UriParam(label = "security", security = "secret",
              description = "The user name for HTTP basic authentication to the Consul agent. Used only together with"
                            + " password.")
    private String userName;
    @UriParam(label = "security", security = "secret",
              description = "The password for HTTP basic authentication to the Consul agent. Used only together with"
                            + " userName.")
    private String password;

    @UriParam(description = "The connect timeout of the HTTP client. When not set, the default of the OkHttp client"
                            + " applies.")
    private Duration connectTimeout;
    @UriParam(description = "The read timeout of the HTTP client. When not set, the default of the OkHttp client applies;"
                            + " the Consul client extends it for the blocking queries of the consumers.")
    private Duration readTimeout;
    @UriParam(description = "The write timeout of the HTTP client. When not set, the default of the OkHttp client"
                            + " applies.")
    private Duration writeTimeout;
    @UriParam(defaultValue = "true",
              description = "Whether the Consul client pings the agent when it is created, so that creating it fails when"
                            + " the agent cannot be reached. The client is created when the consumer starts or when the"
                            + " producer handles its first message.")
    private boolean pingInstance = true;

    @UriParam(label = "consumer,watch", defaultValue = "10",
              description = "The wait time in seconds of the blocking query with which the kv and event consumers watch:"
                            + " Consul answers when the data changes or when the time is up, and the consumer then queries"
                            + " again. The event consumer also waits this long before each query, and both wait this long"
                            + " (at least one second) before they query again after an error.")
    private Integer blockSeconds = 10;
    @UriParam(label = "consumer,watch", defaultValue = "0",
              description = "The Consul index from which the first blocking query of the kv or event consumer waits for a"
                            + " change. With 0 the first query answers at once, so the consumer receives the current value"
                            + " of the key, or the events that Consul still keeps.")
    private BigInteger firstIndex = BigInteger.valueOf(0L);
    @UriParam(label = "consumer,watch", defaultValue = "false",
              description = "Whether the kv consumer watches all the keys under the key (as a prefix) instead of only the"
                            + " key. When one of them changes, the consumer receives a message for each key under the"
                            + " prefix.")
    private boolean recursive;

    protected ConsulClientConfiguration() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getNearNode() {
        return nearNode;
    }

    public void setNearNode(String nearNode) {
        this.nearNode = nearNode;
    }

    public String getNodeMeta() {
        return nodeMeta;
    }

    public Collection<String> getNodeMetasAsList() {
        if (nodeMeta != null) {
            return List.of(nodeMeta.split(","));
        } else {
            return null;
        }
    }

    public void setNodeMeta(String nodeMeta) {
        this.nodeMeta = nodeMeta;
    }

    public ConsistencyMode getConsistencyMode() {
        return consistencyMode;
    }

    public void setConsistencyMode(ConsistencyMode consistencyMode) {
        this.consistencyMode = consistencyMode;
    }

    public String getTags() {
        return tags;
    }

    public Collection<String> getTagAsSet() {
        if (tags != null) {
            return Set.of(tags.split(","));
        } else {
            return null;
        }
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getAclToken() {
        return aclToken;
    }

    public void setAclToken(String aclToken) {
        this.aclToken = aclToken;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean requiresBasicAuthentication() {
        return ObjectHelper.isNotEmpty(userName) && ObjectHelper.isNotEmpty(password);
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public boolean isPingInstance() {
        return pingInstance;
    }

    public void setPingInstance(boolean pingInstance) {
        this.pingInstance = pingInstance;
    }

    public Integer getBlockSeconds() {
        return blockSeconds;
    }

    public void setBlockSeconds(Integer blockSeconds) {
        this.blockSeconds = blockSeconds;
    }

    public BigInteger getFirstIndex() {
        return firstIndex;
    }

    public void setFirstIndex(BigInteger firstIndex) {
        this.firstIndex = firstIndex;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    // ****************************************
    // Create a client
    // ****************************************

    public Consul createConsulClient() throws Exception {
        return createConsulClient(null);
    }

    public Consul createConsulClient(CamelContext camelContext) throws GeneralSecurityException, IOException {
        Consul.Builder builder = Consul.builder();
        builder.withPing(pingInstance);

        if (ObjectHelper.isNotEmpty(url)) {
            builder.withUrl(url);
        }
        if (ObjectHelper.isNotEmpty(camelContext) && ObjectHelper.isNotEmpty(sslContextParameters)) {
            builder.withSslContext(sslContextParameters.createSSLContext(camelContext));
        }
        if (ObjectHelper.isNotEmpty(aclToken)) {
            builder.withAclToken(aclToken);
        }
        if (requiresBasicAuthentication()) {
            builder.withBasicAuth(userName, password);
        }
        if (ObjectHelper.isNotEmpty(connectTimeout)) {
            builder.withConnectTimeoutMillis(connectTimeout.toMillis());
        }

        if (ObjectHelper.isNotEmpty(readTimeout)) {
            builder.withReadTimeoutMillis(readTimeout.toMillis());
        }

        if (ObjectHelper.isNotEmpty(writeTimeout)) {
            builder.withWriteTimeoutMillis(writeTimeout.toMillis());
        }

        return builder.build();
    }

    // ****************************************
    // Copy
    // ****************************************

    public ConsulClientConfiguration copy() {
        try {
            return (ConsulClientConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
