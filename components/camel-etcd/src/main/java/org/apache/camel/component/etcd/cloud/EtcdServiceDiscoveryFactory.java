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
package org.apache.camel.component.etcd.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;

public class EtcdServiceDiscoveryFactory implements ServiceDiscoveryFactory {
    private final EtcdConfiguration configuration;
    private String type;

    public EtcdServiceDiscoveryFactory() {
        this(new EtcdConfiguration());
    }

    public EtcdServiceDiscoveryFactory(EtcdConfiguration configuration) {
        this.configuration = configuration;
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getUris() {
        return configuration.getUris();
    }

    public void setUris(String uris) {
        configuration.setUris(uris);
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
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

    public Integer getTimeToLive() {
        return configuration.getTimeToLive();
    }

    public void setTimeToLive(Integer timeToLive) {
        configuration.setTimeToLive(timeToLive);
    }

    public Long getTimeout() {
        return configuration.getTimeout();
    }

    public void setTimeout(Long timeout) {
        configuration.setTimeout(timeout);
    }

    public String getServicePath() {
        return configuration.getServicePath();
    }

    public void setServicePath(String servicePath) {
        configuration.setServicePath(servicePath);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        return ObjectHelper.equal("watch", type, true)
            ? new EtcdWatchServiceDiscovery(configuration)
            : new EtcdOnDemandServiceDiscovery(configuration);
    }
}
