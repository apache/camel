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
package org.apache.camel.component.etcd3.cloud;

import java.time.Duration;
import java.util.Map;

import io.netty.handler.ssl.SslContext;
import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.component.etcd3.Etcd3Configuration;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.CloudServiceFactory;
import org.apache.camel.util.ObjectHelper;

@CloudServiceFactory("etcd-service-discovery")
@Configurer
public class Etcd3ServiceDiscoveryFactory implements ServiceDiscoveryFactory {
    /**
     * The configuration of the factory.
     */
    private final Etcd3Configuration configuration;
    /**
     * The type of the expected service discovery.
     */
    private String type;

    public Etcd3ServiceDiscoveryFactory() {
        this(new Etcd3Configuration());
    }

    public Etcd3ServiceDiscoveryFactory(Etcd3Configuration configuration) {
        this.configuration = configuration;
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String[] getEndpoints() {
        return configuration.getEndpoints();
    }

    /**
     * Configure etcd server endpoints using the IPNameResolver.
     */
    public void setEndpoints(String... endpoints) {
        configuration.setEndpoints(endpoints);
    }

    public String getUserName() {
        return configuration.getUserName();
    }

    /**
     * Configure etcd auth user.
     */
    public void setUserName(String userName) {
        configuration.setUserName(userName);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    /**
     * Configure etcd auth password.
     */
    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public String getNamespace() {
        return configuration.getNamespace();
    }

    /**
     * Configure the namespace of keys used. "/" will be treated as no namespace.
     */
    public void setNamespace(String namespace) {
        configuration.setNamespace(namespace);
    }

    public SslContext getSslContext() {
        return configuration.getSslContext();
    }

    /**
     * Configure SSL/TLS context to use instead of the system default.
     */
    public void setSslContext(SslContext sslContext) {
        configuration.setSslContext(sslContext);
    }

    public String getLoadBalancerPolicy() {
        return configuration.getLoadBalancerPolicy();
    }

    /**
     * Configure etcd load balancer policy.
     */
    public void setLoadBalancerPolicy(String loadBalancerPolicy) {
        configuration.setLoadBalancerPolicy(loadBalancerPolicy);
    }

    public String getAuthority() {
        return configuration.getAuthority();
    }

    /**
     * Configure the authority used to authenticate connections to servers.
     */
    public void setAuthority(String authority) {
        configuration.setAuthority(authority);
    }

    public Integer getMaxInboundMessageSize() {
        return configuration.getMaxInboundMessageSize();
    }

    /**
     * Configure the maximum message size allowed for a single gRPC frame.
     */
    public void setMaxInboundMessageSize(Integer maxInboundMessageSize) {
        configuration.setMaxInboundMessageSize(maxInboundMessageSize);
    }

    public Map<String, String> getHeaders() {
        return configuration.getHeaders();
    }

    /**
     * Configure the headers to be added to http request headers.
     */
    public void setHeaders(Map<String, String> headers) {
        configuration.setHeaders(headers);
    }

    public Map<String, String> getAuthHeaders() {
        return configuration.getAuthHeaders();
    }

    /**
     * Configure the headers to be added to auth request headers.
     */
    public void setAuthHeaders(Map<String, String> authHeaders) {
        configuration.setAuthHeaders(authHeaders);
    }

    public long getRetryDelay() {
        return configuration.getRetryDelay();
    }

    /**
     * Configure the delay between retries in milliseconds.
     */
    public void setRetryDelay(long retryDelay) {
        configuration.setRetryDelay(retryDelay);
    }

    public long getRetryMaxDelay() {
        return configuration.getRetryMaxDelay();
    }

    /**
     * Configure the max backing off delay between retries in milliseconds.
     */
    public void setRetryMaxDelay(long retryMaxDelay) {
        configuration.setRetryMaxDelay(retryMaxDelay);
    }

    public Duration getKeepAliveTime() {
        return configuration.getKeepAliveTime();
    }

    /**
     * Configure the interval for gRPC keepalives. The current minimum allowed by gRPC is 10 seconds.
     */
    public void setKeepAliveTime(Duration keepAliveTime) {
        configuration.setKeepAliveTime(keepAliveTime);
    }

    public Duration getKeepAliveTimeout() {
        return configuration.getKeepAliveTimeout();
    }

    /**
     * Configure the timeout for gRPC keepalives.
     */
    public void setKeepAliveTimeout(Duration keepAliveTimeout) {
        configuration.setKeepAliveTimeout(keepAliveTimeout);
    }

    public Duration getRetryMaxDuration() {
        return configuration.getRetryMaxDuration();
    }

    /**
     * Configure the retries max duration.
     */
    public void setRetryMaxDuration(Duration retryMaxDuration) {
        this.configuration.setRetryMaxDuration(retryMaxDuration);
    }

    public Duration getConnectionTimeout() {
        return configuration.getConnectionTimeout();
    }

    /**
     * Configure the connection timeout.
     */
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.configuration.setConnectionTimeout(connectionTimeout);
    }

    public String getServicePath() {
        return configuration.getServicePath();
    }

    /**
     * The path to look for service discovery.
     */
    public void setServicePath(String servicePath) {
        configuration.setServicePath(servicePath);
    }

    public String getKeyCharset() {
        return configuration.getKeyCharset();
    }

    /**
     * Configure the charset to use for the keys.
     */
    public void setKeyCharset(String keyCharset) {
        configuration.setKeyCharset(keyCharset);
    }

    public String getValueCharset() {
        return configuration.getValueCharset();
    }

    /**
     * Configure the charset to use for the values.
     */
    public void setValueCharset(String valueCharset) {
        configuration.setValueCharset(valueCharset);
    }

    public String getType() {
        return type;
    }

    /**
     * Configure the type of service discovery. Possible values "watch" or any other.
     */
    public void setType(String type) {
        this.type = type;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        return ObjectHelper.equal("watch", type, true)
                ? new Etcd3WatchServiceDiscovery(configuration)
                : new Etcd3OnDemandServiceDiscovery(configuration);
    }
}
