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
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
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
    @Metadata(label = "advanced")
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
     * To use the shared mina configuration.
     */
    public void setConfiguration(MinaConfiguration configuration) {
        this.configuration = configuration;
    }
}
