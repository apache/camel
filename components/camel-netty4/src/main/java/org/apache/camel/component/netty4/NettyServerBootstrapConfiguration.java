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
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.ssl.SslHandler;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;

@UriParams
public class NettyServerBootstrapConfiguration implements Cloneable {
    public static final String DEFAULT_ENABLED_PROTOCOLS = "TLSv1,TLSv1.1,TLSv1.2";

    @UriPath(enums = "tcp,udp") @Metadata(required = "true")
    protected String protocol;
    @UriPath @Metadata(required = "true")
    protected String host;
    @UriPath @Metadata(required = "true")
    protected int port;
    @UriParam(label = "consumer")
    protected boolean broadcast;
    @UriParam(label = "advanced", defaultValue = "65536")
    protected int sendBufferSize = 65536;
    @UriParam(label = "advanced", defaultValue = "65536")
    protected int receiveBufferSize = 65536;
    @UriParam(label = "advanced")
    protected int receiveBufferSizePredictor;
    @UriParam(label = "consumer,advanced", defaultValue = "1")
    protected int bossCount = 1;
    @UriParam(label = "advanced")
    protected int workerCount;
    @UriParam(defaultValue = "true")
    protected boolean keepAlive = true;
    @UriParam(defaultValue = "true")
    protected boolean tcpNoDelay = true;
    @UriParam(defaultValue = "true")
    protected boolean reuseAddress = true;
    @UriParam(label = "producer", defaultValue = "10000")
    protected int connectTimeout = 10000;
    @UriParam(label = "consumer,advanced")
    protected int backlog;
    @UriParam(label = "consumer,advanced")
    protected ServerInitializerFactory serverInitializerFactory;
    @UriParam(label = "consumer,advanced")
    protected NettyServerBootstrapFactory nettyServerBootstrapFactory;
    @UriParam(label = "advanced", prefix = "option.", multiValue = true)
    protected Map<String, Object> options;
    // SSL options is also part of the server bootstrap as the server listener on port X is either plain or SSL
    @UriParam(label = "security")
    protected boolean ssl;
    @UriParam(label = "security")
    protected boolean sslClientCertHeaders;
    @UriParam(label = "security")
    protected SslHandler sslHandler;
    @UriParam(label = "security")
    protected SSLContextParameters sslContextParameters;
    @UriParam(label = "consumer,security")
    protected boolean needClientAuth;
    @UriParam(label = "security")
    protected File keyStoreFile;
    @UriParam(label = "security")
    protected File trustStoreFile;
    @UriParam(label = "security")
    protected String keyStoreResource;
    @UriParam(label = "security")
    protected String trustStoreResource;
    @UriParam(label = "security")
    protected String keyStoreFormat;
    @UriParam(label = "security")
    protected String securityProvider;
    @UriParam(defaultValue = DEFAULT_ENABLED_PROTOCOLS, label = "security")
    protected String enabledProtocols = DEFAULT_ENABLED_PROTOCOLS;
    @UriParam(label = "security", secret = true)
    protected String passphrase;
    @UriParam(label = "advanced")
    protected boolean nativeTransport;
    @UriParam(label = "consumer,advanced")
    protected EventLoopGroup bossGroup;
    @UriParam(label = "advanced")
    protected EventLoopGroup workerGroup;
    @UriParam(label = "advanced")
    protected ChannelGroup channelGroup;
    @UriParam(label = "consumer,advanced")
    protected String networkInterface;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean reconnect = true;
    @UriParam(label = "consumer", defaultValue = "10000")
    private int reconnectInterval = 10000;

    public String getAddress() {
        return host + ":" + port;
    }

    public boolean isTcp() {
        return protocol.equalsIgnoreCase("tcp");
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol to use which can be tcp or udp.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    /**
     * The hostname.
     * <p/>
     * For the consumer the hostname is localhost or 0.0.0.0
     * For the producer the hostname is the remote host to connect to
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * The host port number
     */
    public void setPort(int port) {
        this.port = port;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    /**
     * Setting to choose Multicast over UDP
     */
    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    /**
     * The TCP/UDP buffer sizes to be used during outbound communication. Size is bytes.
     */
    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    /**
     * The TCP/UDP buffer sizes to be used during inbound communication. Size is bytes.
     */
    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public int getReceiveBufferSizePredictor() {
        return receiveBufferSizePredictor;
    }

    /**
     * Configures the buffer size predictor. See details at Jetty documentation and this mail thread.
     */
    public void setReceiveBufferSizePredictor(int receiveBufferSizePredictor) {
        this.receiveBufferSizePredictor = receiveBufferSizePredictor;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    /**
     * When netty works on nio mode, it uses default workerCount parameter from Netty, which is cpu_core_threads*2.
     * User can use this operation to override the default workerCount from Netty
     */
    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getBossCount() {
        return bossCount;
    }

    /**
     * When netty works on nio mode, it uses default bossCount parameter from Netty, which is 1.
     * User can use this operation to override the default bossCount from Netty
     */
    public void setBossCount(int bossCount) {
        this.bossCount = bossCount;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Setting to ensure socket is not closed due to inactivity
     */
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Setting to improve TCP protocol performance
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    /**
     * Setting to facilitate socket multiplexing
     */
    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Time to wait for a socket connection to be available. Value is in millis.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getBacklog() {
        return backlog;
    }

    /**
     * Allows to configure a backlog for netty consumer (server).
     * Note the backlog is just a best effort depending on the OS.
     * Setting this option to a value such as 200, 500 or 1000, tells the TCP stack how long the "accept" queue can be
     * If this option is not configured, then the backlog depends on OS setting.
     */
    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    public boolean isSsl() {
        return ssl;
    }

    /**
     * Setting to specify whether SSL encryption is applied to this endpoint
     */
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public boolean isSslClientCertHeaders() {
        return sslClientCertHeaders;
    }

    /**
     * When enabled and in SSL mode, then the Netty consumer will enrich the Camel Message with headers having
     * information about the client certificate such as subject name, issuer name, serial number, and the valid date range.
     */
    public void setSslClientCertHeaders(boolean sslClientCertHeaders) {
        this.sslClientCertHeaders = sslClientCertHeaders;
    }

    public SslHandler getSslHandler() {
        return sslHandler;
    }

    /**
     * Reference to a class that could be used to return an SSL Handler
     */
    public void setSslHandler(SslHandler sslHandler) {
        this.sslHandler = sslHandler;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * Configures whether the server needs client authentication when using SSL.
     */
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    @Deprecated
    public File getKeyStoreFile() {
        return keyStoreFile;
    }

    /**
     * Client side certificate keystore to be used for encryption
     */
    @Deprecated
    public void setKeyStoreFile(File keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    @Deprecated
    public File getTrustStoreFile() {
        return trustStoreFile;
    }

    /**
     * Server side certificate keystore to be used for encryption
     */
    @Deprecated
    public void setTrustStoreFile(File trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public String getKeyStoreResource() {
        return keyStoreResource;
    }

    /**
     * Client side certificate keystore to be used for encryption. Is loaded by default from classpath,
     * but you can prefix with "classpath:", "file:", or "http:" to load the resource from different systems.
     */
    public void setKeyStoreResource(String keyStoreResource) {
        this.keyStoreResource = keyStoreResource;
    }

    public String getTrustStoreResource() {
        return trustStoreResource;
    }

    /**
     * Server side certificate keystore to be used for encryption.
     * Is loaded by default from classpath, but you can prefix with "classpath:", "file:", or "http:" to load the resource from different systems.
     */
    public void setTrustStoreResource(String trustStoreResource) {
        this.trustStoreResource = trustStoreResource;
    }

    public String getKeyStoreFormat() {
        return keyStoreFormat;
    }

    /**
     * Keystore format to be used for payload encryption. Defaults to "JKS" if not set
     */
    public void setKeyStoreFormat(String keyStoreFormat) {
        this.keyStoreFormat = keyStoreFormat;
    }

    public String getSecurityProvider() {
        return securityProvider;
    }

    /**
     * Security provider to be used for payload encryption. Defaults to "SunX509" if not set.
     */
    public void setSecurityProvider(String securityProvider) {
        this.securityProvider = securityProvider;
    }

    public String getPassphrase() {
        return passphrase;
    }

    /**
     * Password setting to use in order to encrypt/decrypt payloads sent using SSH
     */
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

    /**
     * To use a custom ServerInitializerFactory
     */
    public void setServerInitializerFactory(ServerInitializerFactory serverInitializerFactory) {
        this.serverInitializerFactory = serverInitializerFactory;
    }

    public NettyServerBootstrapFactory getNettyServerBootstrapFactory() {
        return nettyServerBootstrapFactory;
    }

    /**
     * To use a custom NettyServerBootstrapFactory
     */
    public void setNettyServerBootstrapFactory(NettyServerBootstrapFactory nettyServerBootstrapFactory) {
        this.nettyServerBootstrapFactory = nettyServerBootstrapFactory;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    /**
     * Allows to configure additional netty options using "option." as prefix.
     * For example "option.child.keepAlive=false" to set the netty option "child.keepAlive=false". See the Netty documentation for possible options that can be used.
     */
    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public boolean isNativeTransport() {
        return nativeTransport;
    }

    /**
     * Whether to use native transport instead of NIO. Native transport takes advantage of the host operating system and is only supported on some platforms.
     * You need to add the netty JAR for the host operating system you are using. See more details at: http://netty.io/wiki/native-transports.html
     */
    public void setNativeTransport(boolean nativeTransport) {
        this.nativeTransport = nativeTransport;
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    /**
     * Set the BossGroup which could be used for handling the new connection of the server side across the NettyEndpoint 
     */
    public void setBossGroup(EventLoopGroup bossGroup) {
        this.bossGroup = bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    /**
     * To use a explicit EventLoopGroup as the boss thread pool.
     * For example to share a thread pool with multiple consumers or producers. By default each consumer or producer has their own worker pool with 2 x cpu count core threads.
     */
    public void setWorkerGroup(EventLoopGroup workerGroup) {
        this.workerGroup = workerGroup;
    }

    public ChannelGroup getChannelGroup() {
        return channelGroup;
    }

    /**
     * To use a explicit ChannelGroup.
     */
    public void setChannelGroup(ChannelGroup channelGroup) {
        this.channelGroup = channelGroup;
    }

    public String getNetworkInterface() {
        return networkInterface;
    }

    /**
     * When using UDP then this option can be used to specify a network interface by its name, such as eth0 to join a multicast group.
     */
    public void setNetworkInterface(String networkInterface) {
        this.networkInterface = networkInterface;
    }

    public String getEnabledProtocols() {
        return enabledProtocols;
    }

    /**
     * Which protocols to enable when using SSL
     */
    public void setEnabledProtocols(String enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    /**
     * Used only in clientMode in consumer, the consumer will attempt to reconnect on disconnection if this is enabled
     */
    public boolean isReconnect() {
        return reconnect;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    /**
     * Used if reconnect and clientMode is enabled. The interval in milli seconds to attempt reconnection
     */
    public int getReconnectInterval() {
        return reconnectInterval;
    }

    public void setReconnectInterval(int reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
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
        } else if (reconnect != other.reconnect) {
            isCompatible = false;
        } else if (reconnectInterval != other.reconnectInterval) {
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
                + ", reconnect='" + reconnect + '\''
                + ", reconnectInterval='" + reconnectInterval + '\''
                + '}';
    }
}
