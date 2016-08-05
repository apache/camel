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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.apache.camel.util.jsse.SSLContextParameters;

public class NettyComponent extends UriEndpointComponent {
    private NettyConfiguration configuration;
    private int maximumPoolSize = 16;
    private volatile EventExecutorGroup executorService;

    public NettyComponent() {
        super(NettyEndpoint.class);
    }

    public NettyComponent(Class<? extends Endpoint> endpointClass) {
        super(endpointClass);
    }

    public NettyComponent(CamelContext context) {
        super(context, NettyEndpoint.class);
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * The thread pool size for the EventExecutorGroup if its in use.
     * <p/>
     * The default value is 16.
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyConfiguration config;
        if (configuration != null) {
            config = configuration.copy();
        } else {
            config = new NettyConfiguration();
        }
        config = parseConfiguration(config, remaining, parameters);

        // merge any custom bootstrap configuration on the config
        NettyServerBootstrapConfiguration bootstrapConfiguration = resolveAndRemoveReferenceParameter(parameters, "bootstrapConfiguration", NettyServerBootstrapConfiguration.class);
        if (bootstrapConfiguration != null) {
            Map<String, Object> options = new HashMap<String, Object>();
            if (IntrospectionSupport.getProperties(bootstrapConfiguration, options, null, false)) {
                IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(), config, options);
            }
        }

        // validate config
        config.validateConfiguration();

        NettyEndpoint nettyEndpoint = new NettyEndpoint(remaining, this, config);
        setProperties(nettyEndpoint.getConfiguration(), parameters);
        return nettyEndpoint;
    }

    /**
     * Parses the configuration
     *
     * @return the parsed and valid configuration to use
     */
    protected NettyConfiguration parseConfiguration(NettyConfiguration configuration, String remaining, Map<String, Object> parameters) throws Exception {
        configuration.parseURI(new URI(remaining), parameters, this, "tcp", "udp");
        return configuration;
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the NettyConfiguration as configuration when creating endpoints. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * To use the given EventExecutorGroup
     */
    public void setExecutorService(EventExecutorGroup executorService) {
        this.executorService = executorService;
    }

    public EventExecutorGroup getExecutorService() {
        return executorService;
    }

    @Override
    protected void doStart() throws Exception {
        if (configuration == null) {
            configuration = new NettyConfiguration();
        }

        //Only setup the executorService if it is needed
        if (configuration.isUsingExecutorService() && executorService == null) {
            executorService = createExecutorService();
        }

        super.doStart();
    }

    protected EventExecutorGroup createExecutorService() {
        // Provide the executor service for the application 
        // and use a Camel thread factory so we have consistent thread namings
        // we should use a shared thread pool as recommended by Netty
        String pattern = getCamelContext().getExecutorServiceManager().getThreadNamePattern();
        ThreadFactory factory = new CamelThreadFactory(pattern, "NettyEventExecutorGroup", true);
        return new DefaultEventExecutorGroup(getMaximumPoolSize(), factory);
    }

    @Override
    protected void doStop() throws Exception {
        //Only shutdown the executorService if it is created by netty component
        if (configuration.isUsingExecutorService() && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }

        super.doStop();
    }

    private NettyConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new NettyConfiguration());
        }
        return this.getConfiguration();
    }

    public String getAddress() {
        return getConfigurationOrCreate().getAddress();
    }

    public boolean isTcp() {
        return getConfigurationOrCreate().isTcp();
    }

    public String getProtocol() {
        return getConfigurationOrCreate().getProtocol();
    }

    /**
     * The protocol to use which can be tcp or udp.
     * @param protocol
     */
    public void setProtocol(String protocol) {
        getConfigurationOrCreate().setProtocol(protocol);
    }

    public String getHost() {
        return getConfigurationOrCreate().getHost();
    }

    /**
     * The hostname.
     * <p/>
     * For the consumer the hostname is localhost or 0.0.0.0
     * For the producer the hostname is the remote host to connect to
     * @param host
     */
    public void setHost(String host) {
        getConfigurationOrCreate().setHost(host);
    }

    public int getPort() {
        return getConfigurationOrCreate().getPort();
    }

    /**
     * The host port number
     * @param port
     */
    public void setPort(int port) {
        getConfigurationOrCreate().setPort(port);
    }

    public boolean isBroadcast() {
        return getConfigurationOrCreate().isBroadcast();
    }

    /**
     * Setting to choose Multicast over UDP
     * @param broadcast
     */
    public void setBroadcast(boolean broadcast) {
        getConfigurationOrCreate().setBroadcast(broadcast);
    }

    public int getSendBufferSize() {
        return getConfigurationOrCreate().getSendBufferSize();
    }

    /**
     * The TCP/UDP buffer sizes to be used during outbound communication. Size is bytes.
     * @param sendBufferSize
     */
    public void setSendBufferSize(int sendBufferSize) {
        getConfigurationOrCreate().setSendBufferSize(sendBufferSize);
    }

    public int getReceiveBufferSize() {
        return getConfigurationOrCreate().getReceiveBufferSize();
    }

    /**
     * The TCP/UDP buffer sizes to be used during inbound communication. Size is bytes.
     * @param receiveBufferSize
     */
    public void setReceiveBufferSize(int receiveBufferSize) {
        getConfigurationOrCreate().setReceiveBufferSize(receiveBufferSize);
    }

    public int getReceiveBufferSizePredictor() {
        return getConfigurationOrCreate().getReceiveBufferSizePredictor();
    }

    /**
     * Configures the buffer size predictor. See details at Jetty documentation and this mail thread.
     * @param receiveBufferSizePredictor
     */
    public void setReceiveBufferSizePredictor(int receiveBufferSizePredictor) {
        getConfigurationOrCreate().setReceiveBufferSizePredictor(receiveBufferSizePredictor);
    }

    public int getWorkerCount() {
        return getConfigurationOrCreate().getWorkerCount();
    }

    /**
     * When netty works on nio mode, it uses default workerCount parameter from Netty, which is cpu_core_threads*2.
     * User can use this operation to override the default workerCount from Netty
     * @param workerCount
     */
    public void setWorkerCount(int workerCount) {
        getConfigurationOrCreate().setWorkerCount(workerCount);
    }

    public int getBossCount() {
        return getConfigurationOrCreate().getBossCount();
    }

    /**
     * When netty works on nio mode, it uses default bossCount parameter from Netty, which is 1.
     * User can use this operation to override the default bossCount from Netty
     * @param bossCount
     */
    public void setBossCount(int bossCount) {
        getConfigurationOrCreate().setBossCount(bossCount);
    }

    public boolean isKeepAlive() {
        return getConfigurationOrCreate().isKeepAlive();
    }

    /**
     * Setting to ensure socket is not closed due to inactivity
     * @param keepAlive
     */
    public void setKeepAlive(boolean keepAlive) {
        getConfigurationOrCreate().setKeepAlive(keepAlive);
    }

    public boolean isTcpNoDelay() {
        return getConfigurationOrCreate().isTcpNoDelay();
    }

    /**
     * Setting to improve TCP protocol performance
     * @param tcpNoDelay
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        getConfigurationOrCreate().setTcpNoDelay(tcpNoDelay);
    }

    public boolean isReuseAddress() {
        return getConfigurationOrCreate().isReuseAddress();
    }

    /**
     * Setting to facilitate socket multiplexing
     * @param reuseAddress
     */
    public void setReuseAddress(boolean reuseAddress) {
        getConfigurationOrCreate().setReuseAddress(reuseAddress);
    }

    public int getConnectTimeout() {
        return getConfigurationOrCreate().getConnectTimeout();
    }

    /**
     * Time to wait for a socket connection to be available. Value is in millis.
     * @param connectTimeout
     */
    public void setConnectTimeout(int connectTimeout) {
        getConfigurationOrCreate().setConnectTimeout(connectTimeout);
    }

    public int getBacklog() {
        return getConfigurationOrCreate().getBacklog();
    }

    /**
     * Allows to configure a backlog for netty consumer (server).
     * Note the backlog is just a best effort depending on the OS.
     * Setting this option to a value such as 200, 500 or 1000, tells the TCP stack how long the "accept" queue can be
     * If this option is not configured, then the backlog depends on OS setting.
     * @param backlog
     */
    public void setBacklog(int backlog) {
        getConfigurationOrCreate().setBacklog(backlog);
    }

    public boolean isSsl() {
        return getConfigurationOrCreate().isSsl();
    }

    /**
     * Setting to specify whether SSL encryption is applied to this endpoint
     * @param ssl
     */
    public void setSsl(boolean ssl) {
        getConfigurationOrCreate().setSsl(ssl);
    }

    public boolean isSslClientCertHeaders() {
        return getConfigurationOrCreate().isSslClientCertHeaders();
    }

    /**
     * When enabled and in SSL mode, then the Netty consumer will enrich the Camel Message with headers having
     * information about the client certificate such as subject name, issuer name, serial number, and the valid date range.
     * @param sslClientCertHeaders
     */
    public void setSslClientCertHeaders(boolean sslClientCertHeaders) {
        getConfigurationOrCreate().setSslClientCertHeaders(sslClientCertHeaders);
    }

    public SslHandler getSslHandler() {
        return getConfigurationOrCreate().getSslHandler();
    }

    /**
     * Reference to a class that could be used to return an SSL Handler
     * @param sslHandler
     */
    public void setSslHandler(SslHandler sslHandler) {
        getConfigurationOrCreate().setSslHandler(sslHandler);
    }

    public SSLContextParameters getSslContextParameters() {
        return getConfigurationOrCreate().getSslContextParameters();
    }

    /**
     * To configure security using SSLContextParameters
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        getConfigurationOrCreate().setSslContextParameters(sslContextParameters);
    }

    public boolean isNeedClientAuth() {
        return getConfigurationOrCreate().isNeedClientAuth();
    }

    /**
     * Configures whether the server needs client authentication when using SSL.
     * @param needClientAuth
     */
    public void setNeedClientAuth(boolean needClientAuth) {
        getConfigurationOrCreate().setNeedClientAuth(needClientAuth);
    }

    public String getKeyStoreResource() {
        return getConfigurationOrCreate().getKeyStoreResource();
    }

    /**
     * Client side certificate keystore to be used for encryption. Is loaded by default from classpath,
     * but you can prefix with "classpath:", "file:", or "http:" to load the resource from different systems.
     * @param keyStoreResource
     */
    public void setKeyStoreResource(String keyStoreResource) {
        getConfigurationOrCreate().setKeyStoreResource(keyStoreResource);
    }

    public String getTrustStoreResource() {
        return getConfigurationOrCreate().getTrustStoreResource();
    }

    /**
     * Server side certificate keystore to be used for encryption.
     * Is loaded by default from classpath, but you can prefix with "classpath:", "file:", or "http:" to load the resource from different systems.
     * @param trustStoreResource
     */
    public void setTrustStoreResource(String trustStoreResource) {
        getConfigurationOrCreate().setTrustStoreResource(trustStoreResource);
    }

    public String getKeyStoreFormat() {
        return getConfigurationOrCreate().getKeyStoreFormat();
    }

    /**
     * Keystore format to be used for payload encryption. Defaults to "JKS" if not set
     * @param keyStoreFormat
     */
    public void setKeyStoreFormat(String keyStoreFormat) {
        getConfigurationOrCreate().setKeyStoreFormat(keyStoreFormat);
    }

    public String getSecurityProvider() {
        return getConfigurationOrCreate().getSecurityProvider();
    }

    /**
     * Security provider to be used for payload encryption. Defaults to "SunX509" if not set.
     * @param securityProvider
     */
    public void setSecurityProvider(String securityProvider) {
        getConfigurationOrCreate().setSecurityProvider(securityProvider);
    }

    public String getPassphrase() {
        return getConfigurationOrCreate().getPassphrase();
    }

    /**
     * Password setting to use in order to encrypt/decrypt payloads sent using SSH
     * @param passphrase
     */
    public void setPassphrase(String passphrase) {
        getConfigurationOrCreate().setPassphrase(passphrase);
    }

    public ServerInitializerFactory getServerInitializerFactory() {
        return getConfigurationOrCreate().getServerInitializerFactory();
    }

    public String getCharsetName() {
        return getConfigurationOrCreate().getCharsetName();
    }

    /**
     * To use a custom ServerInitializerFactory
     * @param serverInitializerFactory
     */
    public void setServerInitializerFactory(ServerInitializerFactory serverInitializerFactory) {
        getConfigurationOrCreate().setServerInitializerFactory(serverInitializerFactory);
    }

    public NettyServerBootstrapFactory getNettyServerBootstrapFactory() {
        return getConfigurationOrCreate().getNettyServerBootstrapFactory();
    }

    public long getRequestTimeout() {
        return getConfigurationOrCreate().getRequestTimeout();
    }

    /**
     * To use a custom NettyServerBootstrapFactory
     * @param nettyServerBootstrapFactory
     */
    public void setNettyServerBootstrapFactory(NettyServerBootstrapFactory nettyServerBootstrapFactory) {
        getConfigurationOrCreate().setNettyServerBootstrapFactory(nettyServerBootstrapFactory);
    }

    public Map<String, Object> getOptions() {
        return getConfigurationOrCreate().getOptions();
    }

    /**
     * Allows to use a timeout for the Netty producer when calling a remote server.
     * By default no timeout is in use. The value is in milli seconds, so eg 30000 is 30 seconds.
     * The requestTimeout is using Netty's ReadTimeoutHandler to trigger the timeout.
     * @param requestTimeout
     */
    public void setRequestTimeout(long requestTimeout) {
        getConfigurationOrCreate().setRequestTimeout(requestTimeout);
    }

    public boolean isSync() {
        return getConfigurationOrCreate().isSync();
    }

    /**
     * Setting to set endpoint as one-way or request-response
     * @param sync
     */
    public void setSync(boolean sync) {
        getConfigurationOrCreate().setSync(sync);
    }

    /**
     * Allows to configure additional netty options using "option." as prefix.
     * For example "option.child.keepAlive=false" to set the netty option "child.keepAlive=false". See the Netty documentation for possible options that can be used.
     * @param options
     */
    public void setOptions(Map<String, Object> options) {
        getConfigurationOrCreate().setOptions(options);
    }

    public boolean isTextline() {
        return getConfigurationOrCreate().isTextline();
    }

    public boolean isNativeTransport() {
        return getConfigurationOrCreate().isNativeTransport();
    }

    /**
     * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line based codec;
     * if not specified or the value is false, then Object Serialization is assumed over TCP.
     * @param textline
     */
    public void setTextline(boolean textline) {
        getConfigurationOrCreate().setTextline(textline);
    }

    public int getDecoderMaxLineLength() {
        return getConfigurationOrCreate().getDecoderMaxLineLength();
    }

    /**
     * Whether to use native transport instead of NIO. Native transport takes advantage of the host operating system and is only supported on some platforms.
     * You need to add the netty JAR for the host operating system you are using. See more details at: http://netty.io/wiki/native-transports.html
     * @param nativeTransport
     */
    public void setNativeTransport(boolean nativeTransport) {
        getConfigurationOrCreate().setNativeTransport(nativeTransport);
    }

    /**
     * The max line length to use for the textline codec.
     * @param decoderMaxLineLength
     */
    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        getConfigurationOrCreate().setDecoderMaxLineLength(decoderMaxLineLength);
    }

    public EventLoopGroup getBossGroup() {
        return getConfigurationOrCreate().getBossGroup();
    }

    public TextLineDelimiter getDelimiter() {
        return getConfigurationOrCreate().getDelimiter();
    }

    /**
     * Set the BossGroup which could be used for handling the new connection of the server side across the NettyEndpoint
     * @param bossGroup
     */
    public void setBossGroup(EventLoopGroup bossGroup) {
        getConfigurationOrCreate().setBossGroup(bossGroup);
    }

    /**
     * The delimiter to use for the textline codec. Possible values are LINE and NULL.
     * @param delimiter
     */
    public void setDelimiter(TextLineDelimiter delimiter) {
        getConfigurationOrCreate().setDelimiter(delimiter);
    }

    public EventLoopGroup getWorkerGroup() {
        return getConfigurationOrCreate().getWorkerGroup();
    }

    public boolean isAutoAppendDelimiter() {
        return getConfigurationOrCreate().isAutoAppendDelimiter();
    }

    /**
     * Whether or not to auto append missing end delimiter when sending using the textline codec.
     * @param autoAppendDelimiter
     */
    public void setAutoAppendDelimiter(boolean autoAppendDelimiter) {
        getConfigurationOrCreate().setAutoAppendDelimiter(autoAppendDelimiter);
    }

    /**
     * To use a explicit EventLoopGroup as the boss thread pool.
     * For example to share a thread pool with multiple consumers. By default each consumer has their own boss pool with 1 core thread.
     * @param workerGroup
     */
    public void setWorkerGroup(EventLoopGroup workerGroup) {
        getConfigurationOrCreate().setWorkerGroup(workerGroup);
    }

    public String getEncoding() {
        return getConfigurationOrCreate().getEncoding();
    }

    public ChannelGroup getChannelGroup() {
        return getConfigurationOrCreate().getChannelGroup();
    }

    /**
     * To use a explicit ChannelGroup.
     * @param channelGroup
     */
    public void setChannelGroup(ChannelGroup channelGroup) {
        getConfigurationOrCreate().setChannelGroup(channelGroup);
    }

    /**
     * The encoding (a charset name) to use for the textline codec. If not provided, Camel will use the JVM default Charset.
     * @param encoding
     */
    public void setEncoding(String encoding) {
        getConfigurationOrCreate().setEncoding(encoding);
    }

    public String getNetworkInterface() {
        return getConfigurationOrCreate().getNetworkInterface();
    }

    public List<ChannelHandler> getDecoders() {
        return getConfigurationOrCreate().getDecoders();
    }

    /**
     * When using UDP then this option can be used to specify a network interface by its name, such as eth0 to join a multicast group.
     * @param networkInterface
     */
    public void setNetworkInterface(String networkInterface) {
        getConfigurationOrCreate().setNetworkInterface(networkInterface);
    }

    /**
     * A list of decoders to be used.
     * You can use a String which have values separated by comma, and have the values be looked up in the Registry.
     * Just remember to prefix the value with # so Camel knows it should lookup.
     * @param decoders
     */
    public void setDecoders(List<ChannelHandler> decoders) {
        getConfigurationOrCreate().setDecoders(decoders);
    }

    public String getEnabledProtocols() {
        return getConfigurationOrCreate().getEnabledProtocols();
    }

    public List<ChannelHandler> getEncoders() {
        return getConfigurationOrCreate().getEncoders();
    }

    /**
     * Which protocols to enable when using SSL
     * @param enabledProtocols
     */
    public void setEnabledProtocols(String enabledProtocols) {
        getConfigurationOrCreate().setEnabledProtocols(enabledProtocols);
    }

    /**
     * Used only in clientMode in consumer, the consumer will attempt to reconnect on disconnection if this is enabled
     */
    public boolean isReconnect() {
        return getConfigurationOrCreate().isReconnect();
    }

    /**
     * A list of encoders to be used. You can use a String which have values separated by comma, and have the values be looked up in the Registry.
     * Just remember to prefix the value with # so Camel knows it should lookup.
     * @param encoders
     */
    public void setEncoders(List<ChannelHandler> encoders) {
        getConfigurationOrCreate().setEncoders(encoders);
    }

    public void setReconnect(boolean reconnect) {
        getConfigurationOrCreate().setReconnect(reconnect);
    }

    public ChannelHandler getEncoder() {
        return getConfigurationOrCreate().getEncoder();
    }

    /**
     * Used if reconnect and clientMode is enabled. The interval in milli seconds to attempt reconnection
     */
    public int getReconnectInterval() {
        return getConfigurationOrCreate().getReconnectInterval();
    }

    /**
     * A custom ChannelHandler class that can be used to perform special marshalling of outbound payloads.
     * @param encoder
     */
    public void setEncoder(ChannelHandler encoder) {
        getConfigurationOrCreate().setEncoder(encoder);
    }

    public void setReconnectInterval(int reconnectInterval) {
        getConfigurationOrCreate().setReconnectInterval(reconnectInterval);
    }

    public ChannelHandler getDecoder() {
        return getConfigurationOrCreate().getDecoder();
    }

    /**
     * A custom ChannelHandler class that can be used to perform special marshalling of inbound payloads.
     * @param decoder
     */
    public void setDecoder(ChannelHandler decoder) {
        getConfigurationOrCreate().setDecoder(decoder);
    }

    public boolean isDisconnect() {
        return getConfigurationOrCreate().isDisconnect();
    }

    /**
     * Whether or not to disconnect(close) from Netty Channel right after use. Can be used for both consumer and producer.
     * @param disconnect
     */
    public void setDisconnect(boolean disconnect) {
        getConfigurationOrCreate().setDisconnect(disconnect);
    }

    public boolean isLazyChannelCreation() {
        return getConfigurationOrCreate().isLazyChannelCreation();
    }

    /**
     * Channels can be lazily created to avoid exceptions, if the remote server is not up and running when the Camel producer is started.
     * @param lazyChannelCreation
     */
    public void setLazyChannelCreation(boolean lazyChannelCreation) {
        getConfigurationOrCreate().setLazyChannelCreation(lazyChannelCreation);
    }

    public boolean isTransferExchange() {
        return getConfigurationOrCreate().isTransferExchange();
    }

    /**
     * Only used for TCP. You can transfer the exchange over the wire instead of just the body.
     * The following fields are transferred: In body, Out body, fault body, In headers, Out headers, fault headers,
     * exchange properties, exchange exception.
     * This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level.
     * @param transferExchange
     */
    public void setTransferExchange(boolean transferExchange) {
        getConfigurationOrCreate().setTransferExchange(transferExchange);
    }

    public boolean isDisconnectOnNoReply() {
        return getConfigurationOrCreate().isDisconnectOnNoReply();
    }

    /**
     * If sync is enabled then this option dictates NettyConsumer if it should disconnect where there is no reply to send back.
     * @param disconnectOnNoReply
     */
    public void setDisconnectOnNoReply(boolean disconnectOnNoReply) {
        getConfigurationOrCreate().setDisconnectOnNoReply(disconnectOnNoReply);
    }

    public LoggingLevel getNoReplyLogLevel() {
        return getConfigurationOrCreate().getNoReplyLogLevel();
    }

    /**
     * If sync is enabled this option dictates NettyConsumer which logging level to use when logging a there is no reply to send back.
     * @param noReplyLogLevel
     */
    public void setNoReplyLogLevel(LoggingLevel noReplyLogLevel) {
        getConfigurationOrCreate().setNoReplyLogLevel(noReplyLogLevel);
    }

    public LoggingLevel getServerExceptionCaughtLogLevel() {
        return getConfigurationOrCreate().getServerExceptionCaughtLogLevel();
    }

    /**
     * If the server (NettyConsumer) catches an exception then its logged using this logging level.
     * @param serverExceptionCaughtLogLevel
     */
    public void setServerExceptionCaughtLogLevel(LoggingLevel serverExceptionCaughtLogLevel) {
        getConfigurationOrCreate().setServerExceptionCaughtLogLevel(serverExceptionCaughtLogLevel);
    }

    public LoggingLevel getServerClosedChannelExceptionCaughtLogLevel() {
        return getConfigurationOrCreate().getServerClosedChannelExceptionCaughtLogLevel();
    }

    /**
     * If the server (NettyConsumer) catches an java.nio.channels.ClosedChannelException then its logged using this logging level.
     * This is used to avoid logging the closed channel exceptions, as clients can disconnect abruptly and then cause a flood of closed exceptions in the Netty server.
     * @param serverClosedChannelExceptionCaughtLogLevel
     */
    public void setServerClosedChannelExceptionCaughtLogLevel(LoggingLevel serverClosedChannelExceptionCaughtLogLevel) {
        getConfigurationOrCreate().setServerClosedChannelExceptionCaughtLogLevel(serverClosedChannelExceptionCaughtLogLevel);
    }

    public boolean isAllowDefaultCodec() {
        return getConfigurationOrCreate().isAllowDefaultCodec();
    }

    /**
     * The netty component installs a default codec if both, encoder/deocder is null and textline is false.
     * Setting allowDefaultCodec to false prevents the netty component from installing a default codec as the first element in the filter chain.
     * @param allowDefaultCodec
     */
    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        getConfigurationOrCreate().setAllowDefaultCodec(allowDefaultCodec);
    }

    public ClientInitializerFactory getClientInitializerFactory() {
        return getConfigurationOrCreate().getClientInitializerFactory();
    }

    /**
     * To use a custom ClientInitializerFactory
     * @param clientInitializerFactory
     */
    public void setClientInitializerFactory(ClientInitializerFactory clientInitializerFactory) {
        getConfigurationOrCreate().setClientInitializerFactory(clientInitializerFactory);
    }

    public boolean isUsingExecutorService() {
        return getConfigurationOrCreate().isUsingExecutorService();
    }

    /**
     * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
     * @param usingExecutorService
     */
    public void setUsingExecutorService(boolean usingExecutorService) {
        getConfigurationOrCreate().setUsingExecutorService(usingExecutorService);
    }

    public int getProducerPoolMaxActive() {
        return getConfigurationOrCreate().getProducerPoolMaxActive();
    }

    /**
     * Sets the cap on the number of objects that can be allocated by the pool
     * (checked out to clients, or idle awaiting checkout) at a given time. Use a negative value for no limit.
     * @param producerPoolMaxActive
     */
    public void setProducerPoolMaxActive(int producerPoolMaxActive) {
        getConfigurationOrCreate().setProducerPoolMaxActive(producerPoolMaxActive);
    }

    public int getProducerPoolMinIdle() {
        return getConfigurationOrCreate().getProducerPoolMinIdle();
    }

    /**
     * Sets the minimum number of instances allowed in the producer pool before the evictor thread (if active) spawns new objects.
     * @param producerPoolMinIdle
     */
    public void setProducerPoolMinIdle(int producerPoolMinIdle) {
        getConfigurationOrCreate().setProducerPoolMinIdle(producerPoolMinIdle);
    }

    public int getProducerPoolMaxIdle() {
        return getConfigurationOrCreate().getProducerPoolMaxIdle();
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool.
     * @param producerPoolMaxIdle
     */
    public void setProducerPoolMaxIdle(int producerPoolMaxIdle) {
        getConfigurationOrCreate().setProducerPoolMaxIdle(producerPoolMaxIdle);
    }

    public long getProducerPoolMinEvictableIdle() {
        return getConfigurationOrCreate().getProducerPoolMinEvictableIdle();
    }

    /**
     * Sets the minimum amount of time (value in millis) an object may sit idle in the pool before it is eligible for eviction by the idle object evictor.
     * @param producerPoolMinEvictableIdle
     */
    public void setProducerPoolMinEvictableIdle(long producerPoolMinEvictableIdle) {
        getConfigurationOrCreate().setProducerPoolMinEvictableIdle(producerPoolMinEvictableIdle);
    }

    public boolean isProducerPoolEnabled() {
        return getConfigurationOrCreate().isProducerPoolEnabled();
    }

    /**
     * Whether producer pool is enabled or not.
     * Important: Do not turn this off, as the pooling is needed for handling concurrency and reliable request/reply.
     * @param producerPoolEnabled
     */
    public void setProducerPoolEnabled(boolean producerPoolEnabled) {
        getConfigurationOrCreate().setProducerPoolEnabled(producerPoolEnabled);
    }

    public boolean isUdpConnectionlessSending() {
        return getConfigurationOrCreate().isUdpConnectionlessSending();
    }

    /**
     * This option supports connection less udp sending which is a real fire and forget.
     * A connected udp send receive the PortUnreachableException if no one is listen on the receiving port.
     * @param udpConnectionlessSending
     */
    public void setUdpConnectionlessSending(boolean udpConnectionlessSending) {
        getConfigurationOrCreate().setUdpConnectionlessSending(udpConnectionlessSending);
    }

    public boolean isClientMode() {
        return getConfigurationOrCreate().isClientMode();
    }

    /**
     * If the clientMode is true, netty consumer will connect the address as a TCP client.
     * @param clientMode
     */
    public void setClientMode(boolean clientMode) {
        getConfigurationOrCreate().setClientMode(clientMode);
    }

    public boolean isUseByteBuf() {
        return getConfigurationOrCreate().isUseByteBuf();
    }

    /**
     * If the useByteBuf is true, netty producer will turn the message body into {@link ByteBuf} before sending it out.
     * @param useByteBuf
     */
    public void setUseByteBuf(boolean useByteBuf) {
        getConfigurationOrCreate().setUseByteBuf(useByteBuf);
    }

    public boolean isUdpByteArrayCodec() {
        return getConfigurationOrCreate().isUdpByteArrayCodec();
    }

    /**
     * For UDP only. If enabled the using byte array codec instead of Java serialization protocol.
     * @param udpByteArrayCodec
     */
    public void setUdpByteArrayCodec(boolean udpByteArrayCodec) {
        getConfigurationOrCreate().setUdpByteArrayCodec(udpByteArrayCodec);
    }

    public boolean isReuseChannel() {
        return getConfigurationOrCreate().isReuseChannel();
    }

    /**
     * This option allows producers to reuse the same Netty {@link Channel} for the lifecycle of processing the {@link Exchange}.
     * This is useable if you need to call a server multiple times in a Camel route and want to use the same network connection.
     * When using this the channel is not returned to the connection pool until the {@link Exchange} is done; or disconnected
     * if the disconnect option is set to true.
     * <p/>
     * The reused {@link Channel} is stored on the {@link Exchange} as an exchange property with the key {@link NettyConstants#NETTY_CHANNEL}
     * which allows you to obtain the channel during routing and use it as well.
     * @param reuseChannel
     */
    public void setReuseChannel(boolean reuseChannel) {
        getConfigurationOrCreate().setReuseChannel(reuseChannel);
    }
}
