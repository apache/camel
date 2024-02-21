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
package org.apache.camel.component.etcd3;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.netty.handler.ssl.SslContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

import static org.apache.camel.component.etcd3.Etcd3Constants.ETCD_DEFAULT_ENDPOINTS;

@UriParams
public class Etcd3Configuration implements Cloneable {

    @UriParam(label = "common", defaultValue = "Etcd3Constants.ETCD_DEFAULT_ENDPOINTS")
    private String[] endpoints = ETCD_DEFAULT_ENDPOINTS;
    @UriParam(label = "security", secret = true)
    private String userName;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "advanced,security")
    private SslContext sslContext;
    @UriParam(label = "common")
    private String namespace;
    @UriParam(label = "advanced")
    private String loadBalancerPolicy;
    @UriParam(label = "advanced")
    private String authority;
    @UriParam(label = "advanced")
    private Integer maxInboundMessageSize;
    @UriParam(label = "advanced", prefix = "headers.", multiValue = true)
    private Map<String, String> headers = new HashMap<>();
    @UriParam(label = "advanced", prefix = "authHeaders.", multiValue = true)
    private Map<String, String> authHeaders = new HashMap<>();
    @UriParam(label = "advanced", defaultValue = "500")
    private long retryDelay = 500L;
    @UriParam(label = "advanced", defaultValue = "2500")
    private long retryMaxDelay = 2500L;
    @UriParam(label = "advanced", defaultValue = "30 seconds")
    private Duration keepAliveTime = Duration.ofSeconds(30L);
    @UriParam(label = "advanced", defaultValue = "10 seconds")
    private Duration keepAliveTimeout = Duration.ofSeconds(10L);
    @UriParam(label = "advanced")
    private Duration retryMaxDuration;
    @UriParam(label = "advanced")
    private Duration connectionTimeout;
    @UriParam(label = "common", defaultValue = "false")
    private boolean prefix;
    @UriParam(label = "consumer,advanced", defaultValue = "0", description = "The index to watch from")
    private long fromIndex;
    @UriParam(label = "cloud", defaultValue = "/services/")
    private String servicePath = "/services/";
    @UriParam(label = "common", defaultValue = "UTF-8")
    private String keyCharset = "UTF-8";
    @UriParam(label = "producer", defaultValue = "UTF-8")
    private String valueCharset = "UTF-8";

    public String[] getEndpoints() {
        return endpoints;
    }

    /**
     * Configure etcd server endpoints using the IPNameResolver.
     */
    public void setEndpoints(String... endpoints) {
        this.endpoints = endpoints;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Configure etcd auth user.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Configure etcd auth password.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * Configure the namespace of keys used. "/" will be treated as no namespace.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public SslContext getSslContext() {
        return sslContext;
    }

    /**
     * Configure SSL/TLS context to use instead of the system default.
     */
    public void setSslContext(SslContext sslContext) {
        this.sslContext = sslContext;
    }

    public String getLoadBalancerPolicy() {
        return loadBalancerPolicy;
    }

    /**
     * Configure etcd load balancer policy.
     */
    public void setLoadBalancerPolicy(String loadBalancerPolicy) {
        this.loadBalancerPolicy = loadBalancerPolicy;
    }

    public String getAuthority() {
        return authority;
    }

    /**
     * Configure the authority used to authenticate connections to servers.
     */
    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public Integer getMaxInboundMessageSize() {
        return maxInboundMessageSize;
    }

    /**
     * Configure the maximum message size allowed for a single gRPC frame.
     */
    public void setMaxInboundMessageSize(Integer maxInboundMessageSize) {
        this.maxInboundMessageSize = maxInboundMessageSize;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Configure the headers to be added to http request headers.
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getAuthHeaders() {
        return authHeaders;
    }

    /**
     * Configure the headers to be added to auth request headers.
     */
    public void setAuthHeaders(Map<String, String> authHeaders) {
        this.authHeaders = authHeaders;
    }

    public long getRetryDelay() {
        return retryDelay;
    }

    /**
     * Configure the delay between retries in milliseconds.
     */
    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    public long getRetryMaxDelay() {
        return retryMaxDelay;
    }

    /**
     * Configure the max backing off delay between retries in milliseconds.
     */
    public void setRetryMaxDelay(long retryMaxDelay) {
        this.retryMaxDelay = retryMaxDelay;
    }

    public Duration getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Configure the interval for gRPC keepalives. The current minimum allowed by gRPC is 10 seconds.
     */
    public void setKeepAliveTime(Duration keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public Duration getKeepAliveTimeout() {
        return keepAliveTimeout;
    }

    /**
     * Configure the timeout for gRPC keepalives.
     */
    public void setKeepAliveTimeout(Duration keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public Duration getRetryMaxDuration() {
        return retryMaxDuration;
    }

    /**
     * Configure the retries max duration.
     */
    public void setRetryMaxDuration(Duration retryMaxDuration) {
        this.retryMaxDuration = retryMaxDuration;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Configure the connection timeout.
     */
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isPrefix() {
        return prefix;
    }

    /**
     * To apply an action on all the key-value pairs whose key that starts with the target path.
     */
    public void setPrefix(boolean prefix) {
        this.prefix = prefix;
    }

    public long getFromIndex() {
        return fromIndex;
    }

    /**
     * The index to watch from.
     */
    public void setFromIndex(long fromIndex) {
        this.fromIndex = fromIndex;
    }

    public String getServicePath() {
        return servicePath;
    }

    /**
     * The path to look for service discovery.
     */
    public void setServicePath(String servicePath) {
        this.servicePath = servicePath;
    }

    public String getKeyCharset() {
        return keyCharset;
    }

    /**
     * Configure the charset to use for the keys.
     */
    public void setKeyCharset(String keyCharset) {
        this.keyCharset = keyCharset;
    }

    public String getValueCharset() {
        return valueCharset;
    }

    /**
     * Configure the charset to use for the values.
     */
    public void setValueCharset(String valueCharset) {
        this.valueCharset = valueCharset;
    }

    /**
     * @return a {@link Client} instance configured with all parameters set.
     */
    public Client createClient() {
        final ClientBuilder builder = Client.builder()
                .endpoints(endpoints)
                .sslContext(sslContext)
                .authority(authority)
                .maxInboundMessageSize(maxInboundMessageSize)
                .retryDelay(retryDelay)
                .retryMaxDelay(retryMaxDelay)
                .retryMaxDuration(retryMaxDuration)
                .keepaliveTime(keepAliveTime)
                .keepaliveTimeout(keepAliveTimeout)
                .connectTimeout(connectionTimeout);
        if (loadBalancerPolicy != null) {
            builder.loadBalancerPolicy(loadBalancerPolicy);
        }
        if (userName != null) {
            builder.user(ByteSequence.from(userName.getBytes()));
        }
        if (password != null) {
            builder.password(ByteSequence.from(password.getBytes()));
        }
        if (namespace != null) {
            builder.namespace(ByteSequence.from(namespace.getBytes()));
        }
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getKey());
            }
        }
        if (authHeaders != null && !authHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : authHeaders.entrySet()) {
                builder.authHeader(entry.getKey(), entry.getKey());
            }
        }
        return builder.build();
    }

    Etcd3Configuration copy() {
        try {
            Etcd3Configuration configuration = (Etcd3Configuration) super.clone();
            configuration.setHeaders(new HashMap<>(headers));
            configuration.setAuthHeaders(new HashMap<>(authHeaders));
            return configuration;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
