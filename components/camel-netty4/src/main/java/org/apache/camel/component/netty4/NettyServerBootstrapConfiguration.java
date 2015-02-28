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

import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;

@UriParams
public class NettyServerBootstrapConfiguration implements Cloneable {
    public static final String DEFAULT_ENABLED_PROTOCOLS = "TLSv1,TLSv1.1,TLSv1.2";

    @UriPath @Metadata(required = "true")
    protected String protocol;
    @UriPath @Metadata(required = "true")
    protected String host;
    @UriPath @Metadata(required = "true")
    protected int port;
    @UriParam
    protected boolean broadcast;
    @UriParam(defaultValue = "65536")
    protected int sendBufferSize = 65536;
    @UriParam(defaultValue = "65536")
    protected int receiveBufferSize = 65536;
    @UriParam
    protected int receiveBufferSizePredictor;
    @UriParam(defaultValue = "1")
    protected int bossCount = 1;
    @UriParam
    protected int workerCount;
    @UriParam(defaultValue = "true")
    protected boolean keepAlive = true;
    @UriParam(defaultValue = "true")
    protected boolean tcpNoDelay = true;
    @UriParam(defaultValue = "true")
    protected boolean reuseAddress = true;
    @UriParam(defaultValue = "10000")
    protected int connectTimeout = 10000;
    @UriParam
    protected int backlog;
    @UriParam
    protected ServerInitializerFactory serverInitializerFactory;
    @UriParam
    protected NettyServerBootstrapFactory nettyServerBootstrapFactory;
    protected Map<String, Object> options;
    // SSL options is also part of the server bootstrap as the server listener on port X is either plain or SSL
    @UriParam
    protected boolean ssl;
    @UriParam
    protected boolean sslClientCertHeaders;
    @UriParam
    protected SslHandler sslHandler;
    @UriParam
    protected SSLContextParameters sslContextParameters;
    @UriParam
    protected boolean needClientAuth;
    @UriParam
    protected File keyStoreFile;
    @UriParam
    protected File trustStoreFile;
    @UriParam
    protected String keyStoreResource;
    @UriParam
    protected String trustStoreResource;
    @UriParam
    protected String keyStoreFormat;
    @UriParam
    protected String securityProvider;
    @UriParam(defaultValue = DEFAULT_ENABLED_PROTOCOLS)
    protected String enabledProtocols = DEFAULT_ENABLED_PROTOCOLS;
    @UriParam
    protected String passphrase;
    protected EventLoopGroup bossGroup;
    protected EventLoopGroup workerGroup;
    @UriParam
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

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
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

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
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

    /**
     * @deprecated use #getServerInitializerFactory
     */
    @Deprecated
    public ServerInitializerFactory getServerPipelineFactory() {
        return serverInitializerFactory;
    }

    /**
     * @deprecated use #setServerInitializerFactory
     */
    @Deprecated
    public void setServerPipelineFactory(ServerInitializerFactory serverPipelineFactory) {
        this.serverInitializerFactory = serverPipelineFactory;
    }

    public ServerInitializerFactory getServerInitializerFactory() {
        return serverInitializerFactory;
    }

    public void setServerInitializerFactory(ServerInitializerFactory serverInitializerFactory) {
        this.serverInitializerFactory = serverInitializerFactory;
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

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public void setBossGroup(EventLoopGroup bossGroup) {
        this.bossGroup = bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public void setWorkerGroup(EventLoopGroup workerGroup) {
        this.workerGroup = workerGroup;
    }

    public String getNetworkInterface() {
        return networkInterface;
    }

    public void setNetworkInterface(String networkInterface) {
        this.networkInterface = networkInterface;
    }

    public String getEnabledProtocols() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(String enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
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
        } else if (serverInitializerFactory != other.serverInitializerFactory) {
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
        } else if (bossGroup != other.bossGroup) {
            isCompatible = false;
        } else if (workerGroup != other.workerGroup) {
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
                + ", serverInitializerFactory=" + serverInitializerFactory
                + ", nettyServerBootstrapFactory=" + nettyServerBootstrapFactory
                + ", options=" + options
                + ", ssl=" + ssl
                + ", sslHandler=" + sslHandler
                + ", sslContextParameters='" + sslContextParameters + '\''
                + ", needClientAuth=" + needClientAuth
                + ", enabledProtocols='" + enabledProtocols
                + ", keyStoreFile=" + keyStoreFile
                + ", trustStoreFile=" + trustStoreFile
                + ", keyStoreResource='" + keyStoreResource + '\''
                + ", trustStoreResource='" + trustStoreResource + '\''
                + ", keyStoreFormat='" + keyStoreFormat + '\''
                + ", securityProvider='" + securityProvider + '\''
                + ", passphrase='" + passphrase + '\''
                + ", bossGroup=" + bossGroup
                + ", workerGroup=" + workerGroup
                + ", networkInterface='" + networkInterface + '\''
                + '}';
    }
}
