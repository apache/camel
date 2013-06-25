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
package org.apache.camel.component.netty;

import java.io.File;
import java.util.Map;

import org.apache.camel.util.jsse.SSLContextParameters;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServerBootstrapConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(NettyServerBootstrapConfiguration.class);

    protected String protocol;
    protected String host;
    protected int port;
    protected boolean broadcast;
    protected long sendBufferSize = 65536;
    protected long receiveBufferSize = 65536;
    protected int receiveBufferSizePredictor;
    protected int workerCount;
    protected boolean keepAlive = true;
    protected boolean tcpNoDelay = true;
    protected boolean reuseAddress = true;
    protected long connectTimeout = 10000;
    protected int backlog;
    protected ServerPipelineFactory serverPipelineFactory;
    protected NettyServerBootstrapFactory nettyServerBootstrapFactory;
    protected Map<String, Object> options;
    // SSL options is also part of the server bootstrap as the server listener on port X is either plain or SSL
    protected boolean ssl;
    protected SslHandler sslHandler;
    protected SSLContextParameters sslContextParameters;
    protected boolean needClientAuth;
    protected File keyStoreFile;
    protected File trustStoreFile;
    protected String keyStoreResource;
    protected String trustStoreResource;
    protected String keyStoreFormat;
    protected String securityProvider;
    protected String passphrase;

    public String getAddress() {
        return host + ":" + port;
    }

    public boolean isTcp() {
        return protocol.equalsIgnoreCase("tcp");
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    public long getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(long sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public long getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(long receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getReceiveBufferSizePredictor() {
        return receiveBufferSizePredictor;
    }

    public void setReceiveBufferSizePredictor(int receiveBufferSizePredictor) {
        this.receiveBufferSizePredictor = receiveBufferSizePredictor;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public SslHandler getSslHandler() {
        return sslHandler;
    }

    public void setSslHandler(SslHandler sslHandler) {
        this.sslHandler = sslHandler;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    @Deprecated
    public File getKeyStoreFile() {
        return keyStoreFile;
    }

    @Deprecated
    public void setKeyStoreFile(File keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    @Deprecated
    public File getTrustStoreFile() {
        return trustStoreFile;
    }

    @Deprecated
    public void setTrustStoreFile(File trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public String getKeyStoreResource() {
        return keyStoreResource;
    }

    public void setKeyStoreResource(String keyStoreResource) {
        this.keyStoreResource = keyStoreResource;
    }

    public String getTrustStoreResource() {
        return trustStoreResource;
    }

    public void setTrustStoreResource(String trustStoreResource) {
        this.trustStoreResource = trustStoreResource;
    }

    public String getKeyStoreFormat() {
        return keyStoreFormat;
    }

    public void setKeyStoreFormat(String keyStoreFormat) {
        this.keyStoreFormat = keyStoreFormat;
    }

    public String getSecurityProvider() {
        return securityProvider;
    }

    public void setSecurityProvider(String securityProvider) {
        this.securityProvider = securityProvider;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public ServerPipelineFactory getServerPipelineFactory() {
        return serverPipelineFactory;
    }

    public void setServerPipelineFactory(ServerPipelineFactory serverPipelineFactory) {
        this.serverPipelineFactory = serverPipelineFactory;
    }

    public NettyServerBootstrapFactory getNettyServerBootstrapFactory() {
        return nettyServerBootstrapFactory;
    }

    public void setNettyServerBootstrapFactory(NettyServerBootstrapFactory nettyServerBootstrapFactory) {
        this.nettyServerBootstrapFactory = nettyServerBootstrapFactory;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    /**
     * Checks if the other {@link NettyServerBootstrapConfiguration} is compatible
     * with this, as a Netty listener bound on port X shares the same common
     * {@link NettyServerBootstrapConfiguration}, which must be identical.
     */
    public boolean compatible(NettyServerBootstrapConfiguration other) {
        if (!protocol.equals(other.protocol)) {
            return false;
        }
        if (!host.equals(other.host)) {
            return false;
        }
        if (port != other.port) {
            return false;
        }
        if (broadcast != other.broadcast) {
            return false;
        }
        if (sendBufferSize != other.sendBufferSize) {
            return false;
        }
        if (receiveBufferSize != other.receiveBufferSize) {
            return false;
        }
        if (receiveBufferSizePredictor != other.receiveBufferSizePredictor) {
            return false;
        }
        if (workerCount != other.workerCount) {
            return false;
        }
        if (keepAlive != other.keepAlive) {
            return false;
        }
        if (tcpNoDelay != other.tcpNoDelay) {
            return false;
        }
        if (reuseAddress != other.reuseAddress) {
            return false;
        }
        if (connectTimeout != other.connectTimeout) {
            return false;
        }
        if (backlog != other.backlog) {
            return false;
        }
        if (serverPipelineFactory != other.serverPipelineFactory) {
            return false;
        }
        if (nettyServerBootstrapFactory != other.nettyServerBootstrapFactory) {
            return false;
        }
        // validate all the options is identical
        if (options == null && other.options != null) {
            return false;
        }
        if (options != null && other.options == null) {
            return false;
        }
        if (options != null && other.options != null) {
            if (options.size() != other.options.size()) {
                return false;
            }
            if (!options.keySet().containsAll(other.options.keySet())) {
                return false;
            }
            if (!options.values().containsAll(other.options.values())) {
                return false;
            }
        }
        if (ssl != other.ssl) {
            return false;
        }
        if (sslHandler != other.sslHandler) {
            return false;
        }
        if (sslContextParameters != other.sslContextParameters) {
            return false;
        }
        if (needClientAuth != other.needClientAuth) {
            return false;
        }
        if (keyStoreFile != other.keyStoreFile) {
            return false;
        }
        if (trustStoreFile != other.trustStoreFile) {
            return false;
        }
        if (keyStoreResource != null && !keyStoreResource.equals(other.keyStoreResource)) {
            return false;
        }
        if (trustStoreResource != null && !trustStoreResource.equals(other.trustStoreResource)) {
            return false;
        }
        if (keyStoreFormat != null && !keyStoreFormat.equals(other.keyStoreFormat)) {
            return false;
        }
        if (securityProvider != null && !securityProvider.equals(other.securityProvider)) {
            return false;
        }
        if (passphrase != null && !passphrase.equals(other.passphrase)) {
            return false;
        }
        return true;
    }

    public String toStringBootstrapConfiguration() {
        return "NettyServerBootstrapConfiguration{" +
                "protocol='" + protocol + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", broadcast=" + broadcast +
                ", sendBufferSize=" + sendBufferSize +
                ", receiveBufferSize=" + receiveBufferSize +
                ", receiveBufferSizePredictor=" + receiveBufferSizePredictor +
                ", workerCount=" + workerCount +
                ", keepAlive=" + keepAlive +
                ", tcpNoDelay=" + tcpNoDelay +
                ", reuseAddress=" + reuseAddress +
                ", connectTimeout=" + connectTimeout +
                ", backlog=" + backlog +
                ", serverPipelineFactory=" + serverPipelineFactory +
                ", nettyServerBootstrapFactory=" + nettyServerBootstrapFactory +
                ", options=" + options +
                ", ssl=" + ssl +
                ", sslHandler=" + sslHandler +
                ", sslContextParameters='" + sslContextParameters + '\'' +
                ", needClientAuth=" + needClientAuth +
                ", keyStoreFile=" + keyStoreFile +
                ", trustStoreFile=" + trustStoreFile +
                ", keyStoreResource='" + keyStoreResource + '\'' +
                ", trustStoreResource='" + trustStoreResource + '\'' +
                ", keyStoreFormat='" + keyStoreFormat + '\'' +
                ", securityProvider='" + securityProvider + '\'' +
                ", passphrase='" + passphrase + '\'' +
                '}';
    }
}
