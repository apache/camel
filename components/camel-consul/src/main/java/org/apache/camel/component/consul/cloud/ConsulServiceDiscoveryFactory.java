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
package org.apache.camel.component.consul.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.spi.annotations.CloudServiceFactory;
import org.apache.camel.support.jsse.SSLContextParameters;

@CloudServiceFactory("consul-service-discovery")
public class ConsulServiceDiscoveryFactory implements ServiceDiscoveryFactory {
    private final ConsulConfiguration configuration;

    public ConsulServiceDiscoveryFactory() {
        this(new ConsulConfiguration());
    }

    public ConsulServiceDiscoveryFactory(ConsulConfiguration configuration) {
        this.configuration = configuration;
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getUrl() {
        return configuration.getUrl();
    }

    public void setUrl(String url) {
        configuration.setUrl(url);
    }

    /**
     * @deprecated, @deprecated replaced by {@link #getDatacenter()} ()}
     * 
     * @return
     */
    @Deprecated
    public String getDc() {
        return configuration.getDatacenter();
    }

    /**
     * @deprecated, @deprecated replaced by {@link #setDatacenter(String)}} ()}
     */
    @Deprecated
    public void setDc(String dc) {
        configuration.setDc(dc);
    }

    public void setDatacenter(String dc) {
        configuration.setDatacenter(dc);
    }

    public String getDatacenter() {
        return configuration.getDatacenter();
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
    }

    public String getAclToken() {
        return configuration.getAclToken();
    }

    public void setAclToken(String aclToken) {
        configuration.setAclToken(aclToken);
    }

    public String getUserName() {
        return configuration.getUserName();
    }

    public void setUserName(String userName) {
        configuration.setUserName(userName);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public Long getConnectTimeoutMillis() {
        return configuration.getConnectTimeoutMillis();
    }

    public void setConnectTimeoutMillis(Long connectTimeoutMillis) {
        configuration.setConnectTimeoutMillis(connectTimeoutMillis);
    }

    public Long getReadTimeoutMillis() {
        return configuration.getReadTimeoutMillis();
    }

    public void setReadTimeoutMillis(Long readTimeoutMillis) {
        configuration.setReadTimeoutMillis(readTimeoutMillis);
    }

    public Long getWriteTimeoutMillis() {
        return configuration.getWriteTimeoutMillis();
    }

    public void setWriteTimeoutMillis(Long writeTimeoutMillis) {
        configuration.setWriteTimeoutMillis(writeTimeoutMillis);
    }

    public Integer getBlockSeconds() {
        return configuration.getBlockSeconds();
    }

    public void setBlockSeconds(Integer blockSeconds) {
        configuration.setBlockSeconds(blockSeconds);
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        return new ConsulServiceDiscovery(configuration);
    }
}
