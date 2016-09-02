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
package org.apache.camel.component.mina;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.mina.common.DefaultIoFilterChainBuilder;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoFilter;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.ThreadModel;
import org.apache.mina.filter.LoggingFilter;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;
import org.apache.mina.transport.socket.nio.DatagramAcceptorConfig;
import org.apache.mina.transport.socket.nio.DatagramConnector;
import org.apache.mina.transport.socket.nio.DatagramConnectorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for Apache MINA.
 *
 * @version 
 */
public class MinaComponent extends UriEndpointComponent {
    private static final Logger LOG = LoggerFactory.getLogger(MinaComponent.class);
    private MinaConfiguration configuration;

    public MinaComponent() {
        super(MinaEndpoint.class);
    }

    public MinaComponent(CamelContext context) {
        super(context, MinaEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Using the configuration which set by the component as a default one
        // Since the configuration's properties will be set by the URI
        // we need to copy or create a new MinaConfiguration here
        MinaConfiguration config;
        if (configuration != null) {        
            config = configuration.copy();
        } else {
            config = new MinaConfiguration();
        }

        URI u = new URI(remaining);
        config.setHost(u.getHost());
        config.setPort(u.getPort());
        config.setProtocol(u.getScheme());
        config.setFilters(resolveAndRemoveReferenceListParameter(parameters, "filters", IoFilter.class));
        setProperties(config, parameters);

        return createEndpoint(uri, config);
    }

    public Endpoint createEndpoint(MinaConfiguration config) throws Exception {
        return createEndpoint(config.getUriString(), config);
    }

    private Endpoint createEndpoint(String uri, MinaConfiguration config) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "camelContext");

        String protocol = config.getProtocol();
        // if mistyped uri then protocol can be null
        if (protocol != null) {
            if (protocol.equals("tcp")) {
                return createSocketEndpoint(uri, config);
            } else if (config.isDatagramProtocol()) {
                return createDatagramEndpoint(uri, config);
            } else if (protocol.equals("vm")) {
                return createVmEndpoint(uri, config);
            }
        }
        // protocol not resolved so error
        throw new IllegalArgumentException("Unrecognised MINA protocol: " + protocol + " for uri: " + uri);

    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected MinaEndpoint createVmEndpoint(String uri, MinaConfiguration configuration) {
        boolean minaLogger = configuration.isMinaLogger();
        boolean sync = configuration.isSync();
        List<IoFilter> filters = configuration.getFilters();

        IoAcceptor acceptor = new VmPipeAcceptor();
        SocketAddress address = new VmPipeAddress(configuration.getPort());
        IoConnector connector = new VmPipeConnector();

        // connector config
        configureCodecFactory("MinaProducer", connector.getDefaultConfig(), configuration);
        if (minaLogger) {
            connector.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, connector.getFilterChain());

        // acceptor connectorConfig
        configureCodecFactory("MinaConsumer", acceptor.getDefaultConfig(), configuration);
        if (minaLogger) {
            acceptor.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, acceptor.getFilterChain());

        MinaEndpoint endpoint = new MinaEndpoint(uri, this);
        endpoint.setAddress(address);
        endpoint.setAcceptor(acceptor);
        endpoint.setConnector(connector);
        endpoint.setConfiguration(configuration);

        // set sync or async mode after endpoint is created
        if (sync) {
            endpoint.setExchangePattern(ExchangePattern.InOut);
        } else {
            endpoint.setExchangePattern(ExchangePattern.InOnly);
        }

        return endpoint;
    }

    protected MinaEndpoint createSocketEndpoint(String uri, MinaConfiguration configuration) {
        boolean minaLogger = configuration.isMinaLogger();
        long timeout = configuration.getTimeout();
        boolean sync = configuration.isSync();
        List<IoFilter> filters = configuration.getFilters();
        final int processorCount = Runtime.getRuntime().availableProcessors() + 1;

        ExecutorService acceptorPool = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "MinaSocketAcceptor");
        ExecutorService connectorPool = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "MinaSocketConnector");
        ExecutorService workerPool = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "MinaThreadPool");

        IoAcceptor acceptor = new SocketAcceptor(processorCount, acceptorPool);
        IoConnector connector = new SocketConnector(processorCount, connectorPool);
        SocketAddress address = new InetSocketAddress(configuration.getHost(), configuration.getPort());

        // connector config
        SocketConnectorConfig connectorConfig = new SocketConnectorConfig();
        // must use manual thread model according to Mina documentation
        connectorConfig.setThreadModel(ThreadModel.MANUAL);
        configureCodecFactory("MinaProducer", connectorConfig, configuration);
        connectorConfig.getFilterChain().addLast("threadPool", new ExecutorFilter(workerPool));
        if (minaLogger) {
            connectorConfig.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, connectorConfig.getFilterChain());

        // set connect timeout to mina in seconds
        connectorConfig.setConnectTimeout((int) (timeout / 1000));

        // acceptor connectorConfig
        SocketAcceptorConfig acceptorConfig = new SocketAcceptorConfig();
        // must use manual thread model according to Mina documentation
        acceptorConfig.setThreadModel(ThreadModel.MANUAL);
        configureCodecFactory("MinaConsumer", acceptorConfig, configuration);
        acceptorConfig.setReuseAddress(true);
        acceptorConfig.setDisconnectOnUnbind(true);
        acceptorConfig.getFilterChain().addLast("threadPool", new ExecutorFilter(workerPool));
        if (minaLogger) {
            acceptorConfig.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, acceptorConfig.getFilterChain());

        MinaEndpoint endpoint = new MinaEndpoint(uri, this);
        endpoint.setAddress(address);
        endpoint.setAcceptor(acceptor);
        endpoint.setAcceptorConfig(acceptorConfig);
        endpoint.setConnector(connector);
        endpoint.setConnectorConfig(connectorConfig);
        endpoint.setConfiguration(configuration);

        // enlist threads pools in use on endpoint
        endpoint.addThreadPool(acceptorPool);
        endpoint.addThreadPool(connectorPool);
        endpoint.addThreadPool(workerPool);

        // set sync or async mode after endpoint is created
        if (sync) {
            endpoint.setExchangePattern(ExchangePattern.InOut);
        } else {
            endpoint.setExchangePattern(ExchangePattern.InOnly);
        }

        return endpoint;
    }

    protected void configureCodecFactory(String type, IoServiceConfig config, MinaConfiguration configuration) {
        if (configuration.getCodec() != null) {
            addCodecFactory(config, configuration.getCodec());
        } else if (configuration.isAllowDefaultCodec()) {
            configureDefaultCodecFactory(type, config, configuration);
        }
    }

    protected void configureDefaultCodecFactory(String type, IoServiceConfig config, MinaConfiguration configuration) {
        if (configuration.isTextline()) {
            Charset charset = getEncodingParameter(type, configuration);
            LineDelimiter delimiter = getLineDelimiterParameter(configuration.getTextlineDelimiter());
            TextLineCodecFactory codecFactory = new TextLineCodecFactory(charset, delimiter);
            if (configuration.getEncoderMaxLineLength() > 0) {
                codecFactory.setEncoderMaxLineLength(configuration.getEncoderMaxLineLength());
            }
            if (configuration.getDecoderMaxLineLength() > 0) {
                codecFactory.setDecoderMaxLineLength(configuration.getDecoderMaxLineLength());
            }
            addCodecFactory(config, codecFactory);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}: Using TextLineCodecFactory: {} using encoding: {} line delimiter: {}({})",
                        new Object[]{type, codecFactory, charset, configuration.getTextlineDelimiter(), delimiter});
                LOG.debug("Encoder maximum line length: {}. Decoder maximum line length: {}",
                        codecFactory.getEncoderMaxLineLength(), codecFactory.getDecoderMaxLineLength());
            }
        } else {
            ObjectSerializationCodecFactory codecFactory = new ObjectSerializationCodecFactory();
            addCodecFactory(config, codecFactory);
            LOG.debug("{}: Using ObjectSerializationCodecFactory: {}", type, codecFactory);
        }
        
    }
    
    protected MinaEndpoint createDatagramEndpoint(String uri, MinaConfiguration configuration) {
        boolean minaLogger = configuration.isMinaLogger();
        long timeout = configuration.getTimeout();
        boolean transferExchange = configuration.isTransferExchange();
        boolean sync = configuration.isSync();
        List<IoFilter> filters = configuration.getFilters();

        ExecutorService acceptorPool = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "MinaDatagramAcceptor");
        ExecutorService connectorPool = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "MinaDatagramConnector");
        ExecutorService workerPool = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "MinaThreadPool");

        IoAcceptor acceptor = new DatagramAcceptor(acceptorPool);
        IoConnector connector = new DatagramConnector(connectorPool);
        SocketAddress address = new InetSocketAddress(configuration.getHost(), configuration.getPort());

        if (transferExchange) {
            throw new IllegalArgumentException("transferExchange=true is not supported for datagram protocol");
        }

        DatagramConnectorConfig connectorConfig = new DatagramConnectorConfig();
        // must use manual thread model according to Mina documentation
        connectorConfig.setThreadModel(ThreadModel.MANUAL);
        configureDataGramCodecFactory("MinaProducer", connectorConfig, configuration);
        connectorConfig.getFilterChain().addLast("threadPool", new ExecutorFilter(workerPool));
        if (minaLogger) {
            connectorConfig.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, connectorConfig.getFilterChain());
        // set connect timeout to mina in seconds
        connectorConfig.setConnectTimeout((int) (timeout / 1000));

        DatagramAcceptorConfig acceptorConfig = new DatagramAcceptorConfig();
        // must use manual thread model according to Mina documentation
        acceptorConfig.setThreadModel(ThreadModel.MANUAL);
        configureDataGramCodecFactory("MinaConsumer", acceptorConfig, configuration);
        acceptorConfig.setDisconnectOnUnbind(true);
        // reuse address is default true for datagram
        acceptorConfig.getFilterChain().addLast("threadPool", new ExecutorFilter(workerPool));
        if (minaLogger) {
            acceptorConfig.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, acceptorConfig.getFilterChain());

        MinaEndpoint endpoint = new MinaEndpoint(uri, this);
        endpoint.setAddress(address);
        endpoint.setAcceptor(acceptor);
        endpoint.setAcceptorConfig(acceptorConfig);
        endpoint.setConnector(connector);
        endpoint.setConnectorConfig(connectorConfig);
        endpoint.setConfiguration(configuration);

        // enlist threads pools in use on endpoint
        endpoint.addThreadPool(acceptorPool);
        endpoint.addThreadPool(connectorPool);
        endpoint.addThreadPool(workerPool);

        // set sync or async mode after endpoint is created
        if (sync) {
            endpoint.setExchangePattern(ExchangePattern.InOut);
        } else {
            endpoint.setExchangePattern(ExchangePattern.InOnly);
        }

        return endpoint;
    }

    /**
     * For datagrams the entire message is available as a single ByteBuffer so lets just pass those around by default
     * and try converting whatever they payload is into ByteBuffers unless some custom converter is specified
     */
    protected void configureDataGramCodecFactory(final String type, final IoServiceConfig config, final MinaConfiguration configuration) {
        ProtocolCodecFactory codecFactory = configuration.getCodec();
        if (codecFactory == null) {
            codecFactory = new MinaUdpProtocolCodecFactory(getCamelContext());

            if (LOG.isDebugEnabled()) {
                LOG.debug("{}: Using CodecFactory: {}", new Object[]{type, codecFactory});
            }
        }

        addCodecFactory(config, codecFactory);
    }

    private void addCodecFactory(IoServiceConfig config, ProtocolCodecFactory codecFactory) {
        config.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
    }

    private static LineDelimiter getLineDelimiterParameter(TextLineDelimiter delimiter) {
        if (delimiter == null) {
            return LineDelimiter.DEFAULT;
        }

        switch (delimiter) {
        case DEFAULT:
            return LineDelimiter.DEFAULT;
        case AUTO:
            return LineDelimiter.AUTO;
        case UNIX:
            return LineDelimiter.UNIX;
        case WINDOWS:
            return LineDelimiter.WINDOWS;
        case MAC:
            return LineDelimiter.MAC;
        default:
            throw new IllegalArgumentException("Unknown textline delimiter: " + delimiter);
        }
    }

    private static Charset getEncodingParameter(String type, MinaConfiguration configuration) {
        String encoding = configuration.getEncoding();
        if (encoding == null) {
            encoding = Charset.defaultCharset().name();
            // set in on configuration so its updated
            configuration.setEncoding(encoding);
            LOG.debug("{}: No encoding parameter using default charset: {}", type, encoding);
        }
        if (!Charset.isSupported(encoding)) {
            throw new IllegalArgumentException("The encoding: " + encoding + " is not supported");
        }

        return Charset.forName(encoding);
    }

    private void appendIoFiltersToChain(List<IoFilter> filters, DefaultIoFilterChainBuilder filterChain) {
        if (filters != null && filters.size() > 0) {
            for (IoFilter ioFilter : filters) {
                filterChain.addLast(ioFilter.getClass().getCanonicalName(), ioFilter);
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    public MinaConfiguration getConfiguration() {        
        return configuration;
    }

    /**
     * To use the shared mina configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(MinaConfiguration configuration) {
        this.configuration = configuration;
    }

    private MinaConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new MinaConfiguration());
        }
        return this.getConfiguration();
    }

    public String getCharsetName() {
        return getConfigurationOrCreate().getCharsetName();
    }

    public String getProtocol() {
        return getConfigurationOrCreate().getProtocol();
    }

    /**
     * Protocol to use
     * @param protocol
     */
    public void setProtocol(String protocol) {
        getConfigurationOrCreate().setProtocol(protocol);
    }

    public String getHost() {
        return getConfigurationOrCreate().getHost();
    }

    /**
     * Hostname to use. Use localhost or 0.0.0.0 for local server as consumer. For producer use the hostname or ip address of the remote server.
     * @param host
     */
    public void setHost(String host) {
        getConfigurationOrCreate().setHost(host);
    }

    public int getPort() {
        return getConfigurationOrCreate().getPort();
    }

    /**
     * Port number
     * @param port
     */
    public void setPort(int port) {
        getConfigurationOrCreate().setPort(port);
    }

    public boolean isSync() {
        return getConfigurationOrCreate().isSync();
    }

    /**
     * Setting to set endpoint as one-way or request-response.
     * @param sync
     */
    public void setSync(boolean sync) {
        getConfigurationOrCreate().setSync(sync);
    }

    public boolean isTextline() {
        return getConfigurationOrCreate().isTextline();
    }

    /**
     * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line based codec;
     * if not specified or the value is false, then Object Serialization is assumed over TCP.
     * @param textline
     */
    public void setTextline(boolean textline) {
        getConfigurationOrCreate().setTextline(textline);
    }

    public TextLineDelimiter getTextlineDelimiter() {
        return getConfigurationOrCreate().getTextlineDelimiter();
    }

    /**
     * Only used for TCP and if textline=true. Sets the text line delimiter to use.
     * If none provided, Camel will use DEFAULT.
     * This delimiter is used to mark the end of text.
     * @param textlineDelimiter
     */
    public void setTextlineDelimiter(TextLineDelimiter textlineDelimiter) {
        getConfigurationOrCreate().setTextlineDelimiter(textlineDelimiter);
    }

    public ProtocolCodecFactory getCodec() {
        return getConfigurationOrCreate().getCodec();
    }

    /**
     * To use a custom minda codec implementation.
     * @param codec
     */
    public void setCodec(ProtocolCodecFactory codec) {
        getConfigurationOrCreate().setCodec(codec);
    }

    public String getEncoding() {
        return getConfigurationOrCreate().getEncoding();
    }

    /**
     * You can configure the encoding (a charset name) to use for the TCP textline codec and the UDP protocol.
     * If not provided, Camel will use the JVM default Charset
     * @param encoding
     */
    public void setEncoding(String encoding) {
        getConfigurationOrCreate().setEncoding(encoding);
    }

    public long getTimeout() {
        return getConfigurationOrCreate().getTimeout();
    }

    /**
     * You can configure the timeout that specifies how long to wait for a response from a remote server.
     * The timeout unit is in milliseconds, so 60000 is 60 seconds.
     * @param timeout
     */
    public void setTimeout(long timeout) {
        getConfigurationOrCreate().setTimeout(timeout);
    }

    public boolean isLazySessionCreation() {
        return getConfigurationOrCreate().isLazySessionCreation();
    }

    /**
     * Sessions can be lazily created to avoid exceptions, if the remote server is not up and running when the Camel producer is started.
     * @param lazySessionCreation
     */
    public void setLazySessionCreation(boolean lazySessionCreation) {
        getConfigurationOrCreate().setLazySessionCreation(lazySessionCreation);
    }

    public boolean isTransferExchange() {
        return getConfigurationOrCreate().isTransferExchange();
    }

    /**
     * Only used for TCP. You can transfer the exchange over the wire instead of just the body.
     * The following fields are transferred: In body, Out body, fault body, In headers, Out headers, fault headers, exchange properties, exchange exception.
     * This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level.
     * @param transferExchange
     */
    public void setTransferExchange(boolean transferExchange) {
        getConfigurationOrCreate().setTransferExchange(transferExchange);
    }

    /**
     * To set the textline protocol encoder max line length. By default the default value of Mina itself is used which are Integer.MAX_VALUE.
     * @param encoderMaxLineLength
     */
    public void setEncoderMaxLineLength(int encoderMaxLineLength) {
        getConfigurationOrCreate().setEncoderMaxLineLength(encoderMaxLineLength);
    }

    public int getEncoderMaxLineLength() {
        return getConfigurationOrCreate().getEncoderMaxLineLength();
    }

    /**
     * To set the textline protocol decoder max line length. By default the default value of Mina itself is used which are 1024.
     * @param decoderMaxLineLength
     */
    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        getConfigurationOrCreate().setDecoderMaxLineLength(decoderMaxLineLength);
    }

    public int getDecoderMaxLineLength() {
        return getConfigurationOrCreate().getDecoderMaxLineLength();
    }

    public boolean isMinaLogger() {
        return getConfigurationOrCreate().isMinaLogger();
    }

    /**
     * You can enable the Apache MINA logging filter. Apache MINA uses slf4j logging at INFO level to log all input and output.
     * @param minaLogger
     */
    public void setMinaLogger(boolean minaLogger) {
        getConfigurationOrCreate().setMinaLogger(minaLogger);
    }

    public List<IoFilter> getFilters() {
        return getConfigurationOrCreate().getFilters();
    }

    /**
     * You can set a list of Mina IoFilters to use.
     * @param filters
     */
    public void setFilters(List<IoFilter> filters) {
        getConfigurationOrCreate().setFilters(filters);
    }

    public boolean isDatagramProtocol() {
        return getConfigurationOrCreate().isDatagramProtocol();
    }

    /**
     * The mina component installs a default codec if both, codec is null and textline is false.
     * Setting allowDefaultCodec to false prevents the mina component from installing a default codec as the first element in the filter chain.
     * This is useful in scenarios where another filter must be the first in the filter chain, like the SSL filter.
     * @param allowDefaultCodec
     */
    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        getConfigurationOrCreate().setAllowDefaultCodec(allowDefaultCodec);
    }

    public boolean isAllowDefaultCodec() {
        return getConfigurationOrCreate().isAllowDefaultCodec();
    }

    public boolean isDisconnect() {
        return getConfigurationOrCreate().isDisconnect();
    }

    /**
     * Whether or not to disconnect(close) from Mina session right after use. Can be used for both consumer and producer.
     * @param disconnect
     */
    public void setDisconnect(boolean disconnect) {
        getConfigurationOrCreate().setDisconnect(disconnect);
    }

    public boolean isDisconnectOnNoReply() {
        return getConfigurationOrCreate().isDisconnectOnNoReply();
    }

    /**
     * If sync is enabled then this option dictates MinaConsumer if it should disconnect where there is no reply to send back.
     * @param disconnectOnNoReply
     */
    public void setDisconnectOnNoReply(boolean disconnectOnNoReply) {
        getConfigurationOrCreate().setDisconnectOnNoReply(disconnectOnNoReply);
    }

    public LoggingLevel getNoReplyLogLevel() {
        return getConfigurationOrCreate().getNoReplyLogLevel();
    }

    /**
     * If sync is enabled this option dictates MinaConsumer which logging level to use when logging a there is no reply to send back.
     * @param noReplyLogLevel
     */
    public void setNoReplyLogLevel(LoggingLevel noReplyLogLevel) {
        getConfigurationOrCreate().setNoReplyLogLevel(noReplyLogLevel);
    }

    public boolean isClientMode() {
        return getConfigurationOrCreate().isClientMode();
    }

    /**
     * If the clientMode is true, mina consumer will connect the address as a TCP client.
     * @param clientMode
     */
    public void setClientMode(boolean clientMode) {
        getConfigurationOrCreate().setClientMode(clientMode);
    }

    public String getUriString() {
        return getConfigurationOrCreate().getUriString();
    }
}
