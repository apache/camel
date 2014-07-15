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
package org.apache.camel.component.netty4;

import java.io.File;
import java.util.Map;

import org.apache.camel.util.jsse.SSLContextParameters;
import io.netty.channel.socket.nio.BossPool;
import io.netty.channel.socket.nio.WorkerPool;
import io.netty.handler.ssl.SslHandler;

public class NettyServerBootstrapConfiguration implements Cloneable {

    protected String protocol;
    protected String host;
    protected int port;
    protected boolean broadcast;
    protected long sendBufferSize = 65536;
    protected long receiveBufferSize = 65536;
    protected int receiveBufferSizePredictor;
    protected int bossCount = 1;
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
    protected boolean sslClientCertHeaders;
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
    protected BossPool bossPool;
    protected WorkerPool workerPool;
    protected String networkInterface;

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

    public int getBossCount() {
        return bossCount;
    }

    public void setBossCount(int bossCount) {
        this.bossCount = bossCount;
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

    public boolean isSslClientCertHeaders() {
        return sslClientCertHeaders;
    }

    public void setSslClientCertHeaders(boolean sslClientCertHeaders) {
        this.sslClientCertHeaders = sslClientCertHeaders;
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

    public BossPool getBossPool() {
        return bossPool;
    }

    public void setBossPool(BossPool bossPool) {
        this.bossPool = bossPool;
    }

    public WorkerPool getWorkerPool() {
        return workerPool;
    }

    public void setWorkerPool(WorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    public String getNetworkInterface() {
        return networkInterface;
    }

    public void setNetworkInterface(String networkInterface) {
        this.networkInterface = networkInterface;
    }

    /**
     * Checks if the other {@link NettyServerBootstrapConfiguration} is compatible
     * with this, as a Netty listener bound on port X shares the same common
     * {@link NettyServerBootstrapConfiguration}, which must be identical.
     */
    public boolean compatible(NettyServerBootstrapConfiguration other) {
        boolean isCompatible = true;

        if (!protocol.equals(other.protocol)) {
            isCompatible = false;
        } else if (!host.equals(other.host)) {
            isCompatible = false;
        } else if (port != other.port) {
            isCompatible = false;
        } else if (broadcast != other.broadcast) {
            isCompatible = false;
        } else if (sendBufferSize != other.sendBufferSize) {
            return false;
        } else if (receiveBufferSize != other.receiveBufferSize) {
            isCompatible = false;
        } else if (receiveBufferSizePredictor != other.receiveBufferSizePredictor) {
            isCompatible = false;
        } else if (workerCount != other.workerCount) {
            isCompatible = false;
        } else if (bossCount != other.bossCount) {
            isCompatible = false;
        } else if (keepAlive != other.keepAlive) {
            isCompatible = false;
        } else if (tcpNoDelay != other.tcpNoDelay) {
            isCompatible = false;
        } else if (reuseAddress != other.reuseAddress) {
            isCompatible = false;
        } else if (connectTimeout != other.connectTimeout) {
            isCompatible = false;
        } else if (backlog != other.backlog) {
            isCompatible = false;
        } else if (serverPipelineFactory != other.serverPipelineFactory) {
            isCompatible = false;
        } else if (nettyServerBootstrapFactory != other.nettyServerBootstrapFactory) {
            isCompatible = false;
        } else if (options == null && other.options != null) {
            // validate all the options is identical
            isCompatible = false;
        } else if (options != null && other.options == null) {
            isCompatible = false;
        } else if (options != null && other.options != null && options.size() != other.options.size()) {
            isCompatible = false;
        } else if (options != null && other.options != null && !options.keySet().containsAll(other.options.keySet())) {
            isCompatible = false;
        } else if (options != null && other.options != null && !options.values().containsAll(other.options.values())) {
            isCompatible = false;
        } else if (ssl != other.ssl) {
            isCompatible = false;
        } else if (sslHandler != other.sslHandler) {
            isCompatible = false;
        } else if (sslContextParameters != other.sslContextParameters) {
            isCompatible = false;
        } else if (needClientAuth != other.needClientAuth) {
            isCompatible = false;
        } else if (keyStoreFile != other.keyStoreFile) {
            isCompatible = false;
        } else if (trustStoreFile != other.trustStoreFile) {
            isCompatible = false;
        } else if (keyStoreResource != null && !keyStoreResource.equals(other.keyStoreResource)) {
            isCompatible = false;
        } else if (trustStoreResource != null && !trustStoreResource.equals(other.trustStoreResource)) {
            isCompatible = false;
        } else if (keyStoreFormat != null && !keyStoreFormat.equals(other.keyStoreFormat)) {
            isCompatible = false;
        } else if (securityProvider != null && !securityProvider.equals(other.securityProvider)) {
            isCompatible = false;
        } else if (passphrase != null && !passphrase.equals(other.passphrase)) {
            isCompatible = false;
        } else if (bossPool != other.bossPool) {
            isCompatible = false;
        } else if (workerPool != other.workerPool) {
            isCompatible = false;
        } else if (networkInterface != null && !networkInterface.equals(other.networkInterface)) {
            isCompatible = false;
        }

        return isCompatible;
    }

    public String toStringBootstrapConfiguration() {
        return "NettyServerBootstrapConfiguration{"
                + "protocol='" + protocol + '\''
                + ", host='" + host + '\''
                + ", port=" + port
                + ", broadcast=" + broadcast
                + ", sendBufferSize=" + sendBufferSize
                + ", receiveBufferSize=" + receiveBufferSize
                + ", receiveBufferSizePredictor=" + receiveBufferSizePredictor
                + ", workerCount=" + workerCount
                + ", bossCount=" + bossCount
                + ", keepAlive=" + keepAlive
                + ", tcpNoDelay=" + tcpNoDelay
                + ", reuseAddress=" + reuseAddress
                + ", connectTimeout=" + connectTimeout
                + ", backlog=" + backlog
                + ", serverPipelineFactory=" + serverPipelineFactory
                + ", nettyServerBootstrapFactory=" + nettyServerBootstrapFactory
                + ", options=" + options
                + ", ssl=" + ssl
                + ", sslHandler=" + sslHandler
                + ", sslContextParameters='" + sslContextParameters + '\''
                + ", needClientAuth=" + needClientAuth
                + ", keyStoreFile=" + keyStoreFile
                + ", trustStoreFile=" + trustStoreFile
                + ", keyStoreResource='" + keyStoreResource + '\''
                + ", trustStoreResource='" + trustStoreResource + '\''
                + ", keyStoreFormat='" + keyStoreFormat + '\''
                + ", securityProvider='" + securityProvider + '\''
                + ", passphrase='" + passphrase + '\''
                + ", bossPool=" + bossPool
                + ", workerPool=" + workerPool
                + ", networkInterface='" + networkInterface + '\''
                + '}';
    }
}
