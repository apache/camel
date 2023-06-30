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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.orbitz.consul.Consul;
import com.orbitz.consul.option.ConsistencyMode;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

@UriParams
public abstract class ConsulClientConfiguration implements Cloneable {
    @UriParam
    private String url;
    @UriParam(label = "advanced")
    private String datacenter;
    @UriParam(label = "advanced")
    private String nearNode;
    @UriParam(label = "advanced")
    private List<String> nodeMeta;
    @UriParam(label = "advanced", defaultValue = "DEFAULT", enums = "DEFAULT,STALE,CONSISTENT")
    private ConsistencyMode consistencyMode = ConsistencyMode.DEFAULT;
    @UriParam(javaType = "java.lang.String")
    private Set<String> tags;

    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "security", secret = true)
    private String aclToken;
    @UriParam(label = "security", secret = true)
    private String userName;
    @UriParam(label = "security", secret = true)
    private String password;

    @UriParam
    private Duration connectTimeout;
    @UriParam
    private Duration readTimeout;
    @UriParam
    private Duration writeTimeout;
    @UriParam(defaultValue = "true")
    private boolean pingInstance = true;

    @UriParam(label = "consumer,watch", defaultValue = "10")
    private Integer blockSeconds = 10;
    @UriParam(label = "consumer,watch", defaultValue = "0")
    private BigInteger firstIndex = BigInteger.valueOf(0L);
    @UriParam(label = "consumer,watch", defaultValue = "false")
    private boolean recursive;

    protected ConsulClientConfiguration() {
    }

    public String getUrl() {
        return url;
    }

    /**
     * The Consul agent URL
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @deprecated replaced by {@link #getDatacenter()} ()}
     */
    @Deprecated
    public String getDc() {
        return datacenter;
    }

    /**
     * The data center
     *
     * @deprecated replaced by {@link #setDatacenter(String)} ()}
     */
    @Deprecated
    public void setDc(String dc) {
        this.datacenter = dc;
    }

    public String getDatacenter() {
        return datacenter;
    }

    /**
     * The data center
     */
    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getNearNode() {
        return nearNode;
    }

    /**
     * The near node to use for queries.
     */
    public void setNearNode(String nearNode) {
        this.nearNode = nearNode;
    }

    public List<String> getNodeMeta() {
        return nodeMeta;
    }

    /**
     * The note meta-data to use for queries.
     */
    public void setNodeMeta(List<String> nodeMeta) {
        this.nodeMeta = nodeMeta;
    }

    public ConsistencyMode getConsistencyMode() {
        return consistencyMode;
    }

    /**
     * The consistencyMode used for queries, default ConsistencyMode.DEFAULT
     */
    public void setConsistencyMode(ConsistencyMode consistencyMode) {
        this.consistencyMode = consistencyMode;
    }

    public Set<String> getTags() {
        return tags;
    }

    /**
     * Set tags. You can separate multiple tags by comma.
     */
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    /**
     * Set tags. You can separate multiple tags by comma.
     */
    public void setTags(String tagsAsString) {
        this.tags = new HashSet<>();
        Collections.addAll(tags, tagsAsString.split(","));
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * SSL configuration using an org.apache.camel.support.jsse.SSLContextParameters instance.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public String getAclToken() {
        return aclToken;
    }

    /**
     * Sets the ACL token to be used with Consul
     */
    public void setAclToken(String aclToken) {
        this.aclToken = aclToken;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Sets the username to be used for basic authentication
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets the password to be used for basic authentication
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean requiresBasicAuthentication() {
        return ObjectHelper.isNotEmpty(userName) && ObjectHelper.isNotEmpty(password);
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Connect timeout for OkHttpClient
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    /**
     * Read timeout for OkHttpClient
     */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    /**
     * Write timeout for OkHttpClient
     */
    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public boolean isPingInstance() {
        return pingInstance;
    }

    /**
     * Configure if the AgentClient should attempt a ping before returning the Consul instance
     */
    public void setPingInstance(boolean pingInstance) {
        this.pingInstance = pingInstance;
    }

    public Integer getBlockSeconds() {
        return blockSeconds;
    }

    /**
     * The second to wait for a watch event, default 10 seconds
     */
    public void setBlockSeconds(Integer blockSeconds) {
        this.blockSeconds = blockSeconds;
    }

    public BigInteger getFirstIndex() {
        return firstIndex;
    }

    /**
     * The first index for watch for, default 0
     */
    public void setFirstIndex(BigInteger firstIndex) {
        this.firstIndex = firstIndex;
    }

    public boolean isRecursive() {
        return recursive;
    }

    /**
     * Recursively watch, default false
     */
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
            builder.withConnectTimeoutMillis(readTimeout.toMillis());
        }

        if (ObjectHelper.isNotEmpty(writeTimeout)) {
            builder.withConnectTimeoutMillis(writeTimeout.toMillis());
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
