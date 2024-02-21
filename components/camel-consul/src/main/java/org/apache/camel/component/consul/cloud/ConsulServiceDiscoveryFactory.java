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

import java.time.Duration;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.CloudServiceFactory;
import org.apache.camel.support.jsse.SSLContextParameters;

@CloudServiceFactory("consul-service-discovery")
@Configurer
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

    public Duration getConnectTimeout() {
        return configuration.getConnectTimeout();
    }

    public void setConnectTimeout(Duration connectTimeout) {
        configuration.setConnectTimeout(connectTimeout);
    }

    public Duration getReadTimeout() {
        return configuration.getReadTimeout();
    }

    public void setReadTimeout(Duration readTimeout) {
        configuration.setReadTimeout(readTimeout);
    }

    public Duration getWriteTimeout() {
        return configuration.getWriteTimeout();
    }

    public void setWriteTimeout(Duration writeTimeout) {
        configuration.setWriteTimeout(writeTimeout);
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
