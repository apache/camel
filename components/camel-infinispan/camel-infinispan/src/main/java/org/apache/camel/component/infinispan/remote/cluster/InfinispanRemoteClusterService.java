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
package org.apache.camel.component.infinispan.remote.cluster;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.infinispan.cluster.InfinispanClusterService;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterView;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;

@Metadata(label = "bean",
          description = "Infinispan based remote cluster locking",
          annotations = { "interfaceName=org.apache.camel.cluster.CamelClusterService" })
@Configurer(metadataOnly = true)
public class InfinispanRemoteClusterService extends InfinispanClusterService {

    @Metadata(description = "Node id", required = true)
    private String id;
    @Metadata(label = "advanced", description = "An implementation specific URI for the CacheManager",
              supportFileReference = true)
    private String configurationUri;
    @Metadata(label = "advanced", description = "To use an existing configuration")
    private InfinispanRemoteClusterConfiguration configuration;
    @Metadata(description = "Specifies the cache Container to connect")
    private RemoteCacheManager cacheContainer;
    @Metadata(label = "advanced", description = "The CacheContainer configuration. Used if the cacheContainer is not defined.")
    private Configuration cacheContainerConfiguration;
    @Metadata(description = "The lifespan of the cache entry for the local cluster member registered to the inventory",
              defaultValue = "30")
    private long lifespan;
    @Metadata(description = "The TimeUnit of the lifespan", defaultValue = "SECONDS")
    private TimeUnit lifespanTimeUnit;
    @Metadata(description = "Specifies the host of the cache on Infinispan instance. Multiple hosts can be separated by semicolon.")
    private String hosts;
    @Metadata(label = "security", description = "Define if we are connecting to a secured Infinispan instance")
    private boolean secure;
    @Metadata(label = "security", description = "Define the username to access the infinispan instance")
    private String username;
    @Metadata(label = "security", description = "Define the password to access the infinispan instance", secret = true)
    private String password;
    @Metadata(label = "security", description = "Define the security server name to access the infinispan instance")
    private String securityServerName;
    @Metadata(label = "security", description = "Define the SASL Mechanism to access the infinispan instance")
    private String saslMechanism;
    @Metadata(label = "security", description = "Define the security realm to access the infinispan instance")
    private String securityRealm;
    @Metadata(label = "advanced", description = "Implementation specific properties for the CacheManager")
    private Map<String, String> configurationProperties;

    public InfinispanRemoteClusterService() {
    }

    public InfinispanRemoteClusterService(InfinispanRemoteClusterConfiguration configuration) {
        this.configuration = configuration.clone();
    }

    // *********************************************
    // Properties
    // *********************************************

    @Override
    public void setId(String id) {
        super.setId(id);
        this.id = id;
    }

    @Override
    public String getId() {
        return super.getId();
    }

    public String getConfigurationUri() {
        return configurationUri;
    }

    public void setConfigurationUri(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    public InfinispanRemoteClusterConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(InfinispanRemoteClusterConfiguration configuration) {
        this.configuration = configuration;
    }

    public RemoteCacheManager getCacheContainer() {
        return cacheContainer;
    }

    public void setCacheContainer(RemoteCacheManager cacheContainer) {
        this.cacheContainer = cacheContainer;
    }

    public Configuration getCacheContainerConfiguration() {
        return cacheContainerConfiguration;
    }

    public void setCacheContainerConfiguration(Configuration cacheContainerConfiguration) {
        this.cacheContainerConfiguration = cacheContainerConfiguration;
    }

    public long getLifespan() {
        return lifespan;
    }

    public void setLifespan(long lifespan) {
        this.lifespan = lifespan;
    }

    public TimeUnit getLifespanTimeUnit() {
        return lifespanTimeUnit;
    }

    public void setLifespanTimeUnit(TimeUnit lifespanTimeUnit) {
        this.lifespanTimeUnit = lifespanTimeUnit;
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSecurityServerName() {
        return securityServerName;
    }

    public void setSecurityServerName(String securityServerName) {
        this.securityServerName = securityServerName;
    }

    public String getSaslMechanism() {
        return saslMechanism;
    }

    public void setSaslMechanism(String saslMechanism) {
        this.saslMechanism = saslMechanism;
    }

    public String getSecurityRealm() {
        return securityRealm;
    }

    public void setSecurityRealm(String securityRealm) {
        this.securityRealm = securityRealm;
    }

    public Map<String, String> getConfigurationProperties() {
        return configurationProperties;
    }

    public void setConfigurationProperties(Map<String, String> configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

    // *********************************************
    // Impl
    // *********************************************

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (configuration == null) {
            configuration = new InfinispanRemoteClusterConfiguration();
            configuration.setConfigurationUri(configurationUri);
            configuration.setCacheContainer(cacheContainer);
            configuration.setCacheContainerConfiguration(cacheContainerConfiguration);
            configuration.setConfigurationProperties(configurationProperties);
            if (lifespan != 0) {
                configuration.setLifespan(lifespan);
            }
            if (lifespanTimeUnit != null) {
                configuration.setLifespanTimeUnit(lifespanTimeUnit);
            }
            configuration.setHosts(hosts);
            configuration.setSecure(secure);
            configuration.setUsername(username);
            configuration.setPassword(password);
            configuration.setSecurityServerName(securityServerName);
            configuration.setSecurityRealm(securityRealm);
            configuration.setSaslMechanism(saslMechanism);
        }
    }

    @Override
    protected InfinispanClusterView createView(String namespace) throws Exception {
        // Validate parameters
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        ObjectHelper.notNull(getId(), "Cluster ID");
        ObjectHelper.notNull(configuration, "Configuration");

        return new InfinispanRemoteClusterView(this, configuration, namespace);
    }
}
