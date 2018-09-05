/**
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
package org.apache.camel.component.consul.cluster;

import org.apache.camel.impl.cluster.AbstractCamelClusterService;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsulClusterService extends AbstractCamelClusterService<ConsulClusterView> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulClusterService.class);

    private ConsulClusterConfiguration configuration;

    public ConsulClusterService() {
        this.configuration = new ConsulClusterConfiguration();
    }

    public ConsulClusterService(ConsulClusterConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    // *********************************************
    // Properties
    // *********************************************

    public ConsulClusterConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ConsulClusterConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    public String getUrl() {
        return configuration.getUrl();
    }

    public void setUrl(String url) {
        configuration.setUrl(url);
    }

    public String getDatacenter() {
        return configuration.getDatacenter();
    }

    public void setDatacenter(String datacenter) {
        configuration.setDatacenter(datacenter);
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

    public int getTtl() {
        return configuration.getSessionTtl();
    }

    public void setTtl(int ttl) {
        configuration.setSessionTtl(ttl);
    }

    public int getLockDelay() {
        return configuration.getSessionLockDelay();
    }

    public void setLockDelay(int lockDelay) {
        configuration.setSessionLockDelay(lockDelay);
    }

    public String getRootPath() {
        return configuration.getRootPath();
    }

    public void setRootPath(String rootPath) {
        configuration.setRootPath(rootPath);
    }

    // *********************************************
    //
    // *********************************************

    @Override
    protected ConsulClusterView createView(String namespace) throws Exception {

        // Validate parameters
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        ObjectHelper.notNull(getRootPath(), "Consul root path");

        return new ConsulClusterView(this, configuration, namespace);
    }
}
