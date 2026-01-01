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
package org.apache.camel.component.consul.cluster;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

@Metadata(label = "bean",
          description = "A consul based cluster locking",
          annotations = { "interfaceName=org.apache.camel.cluster.CamelClusterService" })
@Configurer(metadataOnly = true)
public final class ConsulClusterService extends AbstractCamelClusterService<ConsulClusterView> {

    @Metadata(description = "Client id registered as _consul.service.registry.id", required = true)
    private String id;
    @Metadata(description = "The Consul agent URL")
    private String url;
    @Metadata(description = "The Consul cluster root directory path", defaultValue = "/camel")
    private String rootPath;
    @Metadata(description = "The data center")
    private String datacenter;
    @Metadata(label = "security", description = "SSL configuration for advanced security configuration")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", description = "Sets the ACL token to be used with Consul", secret = true)
    private String aclToken;
    @Metadata(label = "security", description = "Sets the username to be used for basic authentication", secret = true)
    private String userName;
    @Metadata(label = "security", description = "Sets the password to be used for basic authentication", secret = true)
    private String password;
    @Metadata(description = "Connect timeout in millis")
    private int connectTimeout;
    @Metadata(label = "advanced", description = "Read timeout in millis")
    private int readTimeout;
    @Metadata(label = "advanced", description = "Write timeout in mills")
    private int writeTimeout;
    @Metadata(description = "Session time to live in seconds", defaultValue = "60")
    private int sessionTtl;
    @Metadata(description = "Session lock delay in seconds", defaultValue = "5")
    private int sessionLockDelay;
    @Metadata(description = "Session refresh interval in seconds", defaultValue = "5")
    private int sessionRefreshInterval;
    @Metadata(label = "advanced", description = "The second to wait for a watch event, default 10 seconds", defaultValue = "10")
    private int blockSeconds;
    @Metadata(label = "advanced", description = "To use an existing configuration")
    private ConsulClusterConfiguration configuration;

    public ConsulClusterService() {
    }

    public ConsulClusterService(ConsulClusterConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    // *********************************************
    // Properties
    // *********************************************

    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public void setId(String id) {
        this.id = id;
        super.setId(id);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
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

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public int getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(int sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public int getSessionLockDelay() {
        return sessionLockDelay;
    }

    public void setSessionLockDelay(int sessionLockDelay) {
        this.sessionLockDelay = sessionLockDelay;
    }

    public int getSessionRefreshInterval() {
        return sessionRefreshInterval;
    }

    public void setSessionRefreshInterval(int sessionRefreshInterval) {
        this.sessionRefreshInterval = sessionRefreshInterval;
    }

    public int getBlockSeconds() {
        return blockSeconds;
    }

    public void setBlockSeconds(int blockSeconds) {
        this.blockSeconds = blockSeconds;
    }

    public ConsulClusterConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ConsulClusterConfiguration configuration) {
        this.configuration = configuration;
    }

    // *********************************************
    //
    // *********************************************

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (configuration == null) {
            configuration = new ConsulClusterConfiguration();
            if (rootPath != null) {
                configuration.setRootPath(rootPath);
            }
            configuration.setUrl(url);
            configuration.setDatacenter(datacenter);
            configuration.setSslContextParameters(sslContextParameters);
            configuration.setAclToken(aclToken);
            configuration.setUserName(userName);
            configuration.setPassword(password);
            if (connectTimeout > 0) {
                configuration.setConnectTimeout(Duration.of(connectTimeout, ChronoUnit.MILLIS));
            }
            if (readTimeout > 0) {
                configuration.setReadTimeout(Duration.of(readTimeout, ChronoUnit.MILLIS));
            }
            if (writeTimeout > 0) {
                configuration.setWriteTimeout(Duration.of(writeTimeout, ChronoUnit.MILLIS));
            }
            if (sessionTtl != 0) {
                configuration.setSessionTtl(sessionTtl);
            }
            if (sessionLockDelay != 0) {
                configuration.setSessionLockDelay(sessionLockDelay);
            }
            if (sessionRefreshInterval != 0) {
                configuration.setSessionRefreshInterval(sessionRefreshInterval);
            }
            if (blockSeconds != 0) {
                configuration.setBlockSeconds(blockSeconds);
            }
        }
    }

    @Override
    protected ConsulClusterView createView(String namespace) throws Exception {
        // Validate parameters
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        ObjectHelper.notNull(configuration.getRootPath(), "Consul root path");

        return new ConsulClusterView(this, configuration, namespace);
    }
}
