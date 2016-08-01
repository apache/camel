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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

public class NettyComponent extends UriEndpointComponent {
    // use a shared timer for Netty (see javadoc for HashedWheelTimer)
    private Timer timer;
    private NettyConfiguration configuration;
    private int maximumPoolSize = 16;
    private volatile OrderedMemoryAwareThreadPoolExecutor executorService;

    public NettyComponent() {
        super(NettyEndpoint.class);
    }

    public NettyComponent(Class<? extends Endpoint> endpointClass) {
        super(endpointClass);
    }

    public NettyComponent(CamelContext context) {
        super(context, NettyEndpoint.class);
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
        nettyEndpoint.setTimer(getTimer());
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

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * The core pool size for the ordered thread pool, if its in use.
     * <p/>
     * The default value is 16.
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public boolean isOrderedThreadPoolExecutor() {
        return getConfigurationOrCreate().isOrderedThreadPoolExecutor();
    }

    /**
     * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
     * See details at the netty javadoc of org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor for more details.
     * @param orderedThreadPoolExecutor
     */
    public void setOrderedThreadPoolExecutor(boolean orderedThreadPoolExecutor) {
        getConfigurationOrCreate().setOrderedThreadPoolExecutor(orderedThreadPoolExecutor);
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

    public boolean isUseChannelBuffer() {
        return getConfigurationOrCreate().isUseChannelBuffer();
    }

    /**
     * If the useChannelBuffer is true, netty producer will turn the message body into {@link ChannelBuffer} before sending it out.
     * @param useChannelBuffer
     */
    public void setUseChannelBuffer(boolean useChannelBuffer) {
        getConfigurationOrCreate().setUseChannelBuffer(useChannelBuffer);
    }

    public long getMaxChannelMemorySize() {
        return getConfigurationOrCreate().getMaxChannelMemorySize();
    }

    /**
     * The maximum total size of the queued events per channel when using orderedThreadPoolExecutor.
     * Specify 0 to disable.
     * @param maxChannelMemorySize
     */
    public void setMaxChannelMemorySize(long maxChannelMemorySize) {
        getConfigurationOrCreate().setMaxChannelMemorySize(maxChannelMemorySize);
    }

    public long getMaxTotalMemorySize() {
        return getConfigurationOrCreate().getMaxTotalMemorySize();
    }

    /**
     * The maximum total size of the queued events for this pool when using orderedThreadPoolExecutor.
     * Specify 0 to disable.
     * @param maxTotalMemorySize
     */
    public void setMaxTotalMemorySize(long maxTotalMemorySize) {
        getConfigurationOrCreate().setMaxTotalMemorySize(maxTotalMemorySize);
    }

    public Timer getTimer() {
        return timer;
    }

    public synchronized OrderedMemoryAwareThreadPoolExecutor getExecutorService() {
        if (executorService == null) {
            executorService = createExecutorService();
        }
        return executorService;
    }

    @Override
    protected void doStart() throws Exception {
        if (timer == null) {
            HashedWheelTimer hashedWheelTimer = new HashedWheelTimer();
            hashedWheelTimer.start();
            timer = hashedWheelTimer;
        }

        if (configuration == null) {
            configuration = new NettyConfiguration();
        }
        if (configuration.isOrderedThreadPoolExecutor()) {
            executorService = createExecutorService();
        }

        super.doStart();
    }

    protected OrderedMemoryAwareThreadPoolExecutor createExecutorService() {
        // use ordered thread pool, to ensure we process the events in order, and can send back
        // replies in the expected order. eg this is required by TCP.
        // and use a Camel thread factory so we have consistent thread namings
        // we should use a shared thread pool as recommended by Netty
        
        // NOTE: if we don't specify the MaxChannelMemorySize and MaxTotalMemorySize, the thread pool
        // could eat up all the heap memory when the tasks are added very fast
        
        String pattern = getCamelContext().getExecutorServiceManager().getThreadNamePattern();
        ThreadFactory factory = new CamelThreadFactory(pattern, "NettyOrderedWorker", true);
        return new OrderedMemoryAwareThreadPoolExecutor(getMaximumPoolSize(),
                configuration.getMaxChannelMemorySize(), configuration.getMaxTotalMemorySize(),
                30, TimeUnit.SECONDS, factory);
    }

    @Override
    protected void doStop() throws Exception {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
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

    public long getSendBufferSize() {
        return getConfigurationOrCreate().getSendBufferSize();
    }

    /**
     * The TCP/UDP buffer sizes to be used during outbound communication. Size is bytes.
     * @param sendBufferSize
     */
    public void setSendBufferSize(long sendBufferSize) {
        getConfigurationOrCreate().setSendBufferSize(sendBufferSize);
    }

    public long getReceiveBufferSize() {
        return getConfigurationOrCreate().getReceiveBufferSize();
    }

    /**
     * The TCP/UDP buffer sizes to be used during inbound communication. Size is bytes.
     * @param receiveBufferSize
     */
    public void setReceiveBufferSize(long receiveBufferSize) {
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

    public long getConnectTimeout() {
        return getConfigurationOrCreate().getConnectTimeout();
    }

    /**
     * Time to wait for a socket connection to be available. Value is in millis.
     * @param connectTimeout
     */
    public void setConnectTimeout(long connectTimeout) {
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

    public String getCharsetName() {
        return getConfigurationOrCreate().getCharsetName();
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

    public ServerPipelineFactory getServerPipelineFactory() {
        return getConfigurationOrCreate().getServerPipelineFactory();
    }

    public long getRequestTimeout() {
        return getConfigurationOrCreate().getRequestTimeout();
    }

    /**
     * To use a custom ServerPipelineFactory
     * @param serverPipelineFactory
     */
    public void setServerPipelineFactory(ServerPipelineFactory serverPipelineFactory) {
        getConfigurationOrCreate().setServerPipelineFactory(serverPipelineFactory);
    }

    public NettyServerBootstrapFactory getNettyServerBootstrapFactory() {
        return getConfigurationOrCreate().getNettyServerBootstrapFactory();
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
     * To use a custom NettyServerBootstrapFactory
     * @param nettyServerBootstrapFactory
     */
    public void setNettyServerBootstrapFactory(NettyServerBootstrapFactory nettyServerBootstrapFactory) {
        getConfigurationOrCreate().setNettyServerBootstrapFactory(nettyServerBootstrapFactory);
    }

    /**
     * Setting to set endpoint as one-way or request-response
     * @param sync
     */
    public void setSync(boolean sync) {
        getConfigurationOrCreate().setSync(sync);
    }

    public boolean isTextline() {
        return getConfigurationOrCreate().isTextline();
    }

    public Map<String, Object> getOptions() {
        return getConfigurationOrCreate().getOptions();
    }

    /**
     * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line based codec;
     * if not specified or the value is false, then Object Serialization is assumed over TCP.
     * @param textline
     */
    public void setTextline(boolean textline) {
        getConfigurationOrCreate().setTextline(textline);
    }

    /**
     * Allows to configure additional netty options using "option." as prefix.
     * For example "option.child.keepAlive=false" to set the netty option "child.keepAlive=false". See the Netty documentation for possible options that can be used.
     * @param options
     */
    public void setOptions(Map<String, Object> options) {
        getConfigurationOrCreate().setOptions(options);
    }

    public int getDecoderMaxLineLength() {
        return getConfigurationOrCreate().getDecoderMaxLineLength();
    }

    public BossPool getBossPool() {
        return getConfigurationOrCreate().getBossPool();
    }

    /**
     * The max line length to use for the textline codec.
     * @param decoderMaxLineLength
     */
    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        getConfigurationOrCreate().setDecoderMaxLineLength(decoderMaxLineLength);
    }

    public TextLineDelimiter getDelimiter() {
        return getConfigurationOrCreate().getDelimiter();
    }

    /**
     * To use a explicit org.jboss.netty.channel.socket.nio.BossPool as the boss thread pool.
     * For example to share a thread pool with multiple consumers. By default each consumer has their own boss pool with 1 core thread.
     * @param bossPool
     */
    public void setBossPool(BossPool bossPool) {
        getConfigurationOrCreate().setBossPool(bossPool);
    }

    public WorkerPool getWorkerPool() {
        return getConfigurationOrCreate().getWorkerPool();
    }

    /**
     * The delimiter to use for the textline codec. Possible values are LINE and NULL.
     * @param delimiter
     */
    public void setDelimiter(TextLineDelimiter delimiter) {
        getConfigurationOrCreate().setDelimiter(delimiter);
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
     * To use a explicit org.jboss.netty.channel.socket.nio.WorkerPool as the worker thread pool.
     * For example to share a thread pool with multiple consumers. By default each consumer has their own worker pool with 2 x cpu count core threads.
     * @param workerPool
     */
    public void setWorkerPool(WorkerPool workerPool) {
        getConfigurationOrCreate().setWorkerPool(workerPool);
    }

    public ChannelGroup getChannelGroup() {
        return getConfigurationOrCreate().getChannelGroup();
    }

    public String getEncoding() {
        return getConfigurationOrCreate().getEncoding();
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

    public String getEnabledProtocols() {
        return getConfigurationOrCreate().getEnabledProtocols();
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

    /**
     * Which protocols to enable when using SSL
     * @param enabledProtocols
     */
    public void setEnabledProtocols(String enabledProtocols) {
        getConfigurationOrCreate().setEnabledProtocols(enabledProtocols);
    }

    public List<ChannelHandler> getEncoders() {
        return getConfigurationOrCreate().getEncoders();
    }

    /**
     * A list of encoders to be used. You can use a String which have values separated by comma, and have the values be looked up in the Registry.
     * Just remember to prefix the value with # so Camel knows it should lookup.
     * @param encoders
     */
    public void setEncoders(List<ChannelHandler> encoders) {
        getConfigurationOrCreate().setEncoders(encoders);
    }

    public ChannelHandler getEncoder() {
        return getConfigurationOrCreate().getEncoder();
    }

    /**
     * A custom ChannelHandler class that can be used to perform special marshalling of outbound payloads. Must override org.jboss.netty.channel.ChannelDownStreamHandler.
     * @param encoder
     */
    public void setEncoder(ChannelHandler encoder) {
        getConfigurationOrCreate().setEncoder(encoder);
    }

    public ChannelHandler getDecoder() {
        return getConfigurationOrCreate().getDecoder();
    }

    /**
     * A custom ChannelHandler class that can be used to perform special marshalling of inbound payloads. Must override org.jboss.netty.channel.ChannelUpStreamHandler.
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

    /**
     * To use a custom ClientPipelineFactory
     * @param clientPipelineFactory
     */
    public void setClientPipelineFactory(ClientPipelineFactory clientPipelineFactory) {
        getConfigurationOrCreate().setClientPipelineFactory(clientPipelineFactory);
    }

    public ClientPipelineFactory getClientPipelineFactory() {
        return getConfigurationOrCreate().getClientPipelineFactory();
    }
}