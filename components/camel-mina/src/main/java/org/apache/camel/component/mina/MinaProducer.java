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
package org.apache.camel.component.mina;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.executor.OrderedThreadPoolExecutor;
import org.apache.mina.filter.executor.UnorderedThreadPoolExecutor;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioDatagramConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Producer} implementation for MINA
 */
public class MinaProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MinaProducer.class);
    private final ResponseHandler handler;
    private IoSession session;
    private CountDownLatch responseLatch;
    private CountDownLatch closeLatch;
    private boolean lazySessionCreation;
    private long writeTimeout;
    private long timeout;
    private SocketAddress address;
    private IoConnector connector;
    private boolean sync;
    private CamelLogger noReplyLogger;
    private MinaConfiguration configuration;
    private IoSessionConfig connectorConfig;
    private ExecutorService workerPool;

    public MinaProducer(MinaEndpoint endpoint) throws Exception {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        this.lazySessionCreation = configuration.isLazySessionCreation();
        this.writeTimeout = configuration.getWriteTimeout();
        this.timeout = configuration.getTimeout();
        this.sync = configuration.isSync();
        this.noReplyLogger = new CamelLogger(LOG, configuration.getNoReplyLogLevel());

        String protocol = configuration.getProtocol();
        if (protocol.equals("tcp")) {
            setupSocketProtocol(protocol);
        } else if (configuration.isDatagramProtocol()) {
            setupDatagramProtocol(protocol);
        } else if (protocol.equals("vm")) {
            setupVmProtocol(protocol);
        }
        handler = new ResponseHandler();
        connector.setHandler(handler);
    }

    @Override
    public MinaEndpoint getEndpoint() {
        return (MinaEndpoint) super.getEndpoint();
    }

    @Override
    public boolean isSingleton() {
        // the producer should not be singleton otherwise cannot use concurrent producers and safely
        // use request/reply with correct correlation
        return !sync;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            doProcess(exchange);
        } finally {
            // ensure we always disconnect if configured
            maybeDisconnectOnDone(exchange);
        }
    }

    protected void doProcess(Exchange exchange) throws Exception {
        if (session == null && !lazySessionCreation) {
            throw new IllegalStateException("Not started yet!");
        }
        if (session == null || !session.isConnected()) {
            openConnection();
        }

        // set the exchange encoding property
        if (getEndpoint().getConfiguration().getCharsetName() != null) {
            exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(getEndpoint().getConfiguration().getCharsetName()));
        }

        Object body = MinaPayloadHelper.getIn(getEndpoint(), exchange);
        if (body == null) {
            noReplyLogger.log("No payload to send for exchange: " + exchange);
            return; // exit early since nothing to write
        }

        // if textline enabled then covert to a String which must be used for textline
        if (getEndpoint().getConfiguration().isTextline()) {
            body = getEndpoint().getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, body);
        }

        // if sync is true then we should also wait for a response (synchronous mode)
        if (sync) {
            // only initialize responseLatch if we should get a response
            responseLatch = new CountDownLatch(1);
            // reset handler if we expect a response
            handler.reset();
        }

        // log what we are writing
        if (LOG.isDebugEnabled()) {
            Object out = body;
            if (body instanceof byte[]) {
                // byte arrays is not readable so convert to string
                out = exchange.getContext().getTypeConverter().convertTo(String.class, body);
            }
            LOG.debug("Writing body: {}", out);
        }
        // write the body
        MinaHelper.writeBody(session, body, exchange, writeTimeout);

        if (sync) {
            // wait for response, consider timeout
            LOG.debug("Waiting for response using timeout {} millis.", timeout);
            boolean done = responseLatch.await(timeout, TimeUnit.MILLISECONDS);
            if (!done) {
                throw new ExchangeTimedOutException(exchange, timeout);
            }

            // did we get a response
            if (handler.getCause() != null) {
                throw new CamelExchangeException("Error occurred in ResponseHandler", exchange, handler.getCause());
            } else if (!handler.isMessageReceived()) {
                // no message received
                throw new ExchangeTimedOutException(exchange, timeout);
            } else {
                // set the result on either IN or OUT on the original exchange depending on its pattern
                if (ExchangeHelper.isOutCapable(exchange)) {
                    MinaPayloadHelper.setOut(exchange, handler.getMessage());
                } else {
                    MinaPayloadHelper.setIn(exchange, handler.getMessage());
                }
            }
        }
    }

    protected void maybeDisconnectOnDone(Exchange exchange) throws InterruptedException {
        if (session == null) {
            return;
        }

        // should session be closed after complete?
        Boolean close;
        if (ExchangeHelper.isOutCapable(exchange)) {
            close = exchange.getOut().getHeader(MinaConstants.MINA_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
        } else {
            close = exchange.getIn().getHeader(MinaConstants.MINA_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
        }

        // should we disconnect, the header can override the configuration
        boolean disconnect = getEndpoint().getConfiguration().isDisconnect();
        if (close != null) {
            disconnect = close;
        }
        if (disconnect) {
            LOG.debug("Closing session when complete at address: {}", address);
            closeSessionIfNeededAndAwaitCloseInHandler(session);
        }
    }

    private void closeSessionIfNeededAndAwaitCloseInHandler(IoSession sessionToBeClosed) throws InterruptedException {
        closeLatch = new CountDownLatch(1);
        if (!sessionToBeClosed.isClosing()) {
            CloseFuture closeFuture = sessionToBeClosed.closeNow();
            closeFuture.await(timeout, TimeUnit.MILLISECONDS);
            closeLatch.await(timeout, TimeUnit.MILLISECONDS);
        }
    }

    public DefaultIoFilterChainBuilder getFilterChain() {
        return connector.getFilterChain();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!lazySessionCreation) {
            openConnection();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping connector: {} at address: {}", connector, address);
        }
        closeConnection();
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        if (workerPool != null) {
            workerPool.shutdown();
        }
        super.doShutdown();
    }

    private void closeConnection() throws InterruptedException {
        if (session != null) {
            closeSessionIfNeededAndAwaitCloseInHandler(session);
        }

        connector.dispose(true);
    }

    private void openConnection() {
        if (this.address == null || !this.configuration.isCachedAddress()) {
            setSocketAddress(this.configuration.getProtocol());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating connector to address: {} using connector: {} timeout: {} millis.", address, connector, timeout);
        }
        // connect and wait until the connection is established
        if (connectorConfig != null) {
            connector.getSessionConfig().setAll(connectorConfig);
        }

        ConnectFuture future = connector.connect(address);
        future.awaitUninterruptibly();
        session = future.getSession();
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void setupVmProtocol(String uri) {
        boolean minaLogger = configuration.isMinaLogger();
        List<IoFilter> filters = configuration.getFilters();

        address = new VmPipeAddress(configuration.getPort());
        connector = new VmPipeConnector();

        // connector config
        if (minaLogger) {
            connector.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, connector.getFilterChain());
        if (configuration.getSslContextParameters() != null) {
            LOG.warn("Using vm protocol"
                     + ", but an SSLContextParameters instance was provided.  SSLContextParameters is only supported on the TCP protocol.");
        }
        configureCodecFactory("MinaProducer", connector);
    }

    protected void setupSocketProtocol(String uri) throws Exception {
        boolean minaLogger = configuration.isMinaLogger();
        long timeout = configuration.getTimeout();
        List<IoFilter> filters = configuration.getFilters();

        address = new InetSocketAddress(configuration.getHost(), configuration.getPort());

        final int processorCount = Runtime.getRuntime().availableProcessors() + 1;
        connector = new NioSocketConnector(processorCount);

        // connector config
        connectorConfig = connector.getSessionConfig();

        if (configuration.isOrderedThreadPoolExecutor()) {
            workerPool = new OrderedThreadPoolExecutor(configuration.getMaximumPoolSize());
        } else {
            workerPool = new UnorderedThreadPoolExecutor(configuration.getMaximumPoolSize());
        }
        connector.getFilterChain().addLast("threadPool", new ExecutorFilter(workerPool));
        if (minaLogger) {
            connector.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, connector.getFilterChain());
        if (configuration.getSslContextParameters() != null) {
            SslFilter filter = new SslFilter(configuration.getSslContextParameters().createSSLContext(getEndpoint().getCamelContext()), configuration.isAutoStartTls());
            filter.setUseClientMode(true);
            connector.getFilterChain().addFirst("sslFilter", filter);
        }
        configureCodecFactory("MinaProducer", connector);
        connector.setConnectTimeoutMillis(timeout);
    }

    protected void configureCodecFactory(String type, IoService service) {
        if (configuration.getCodec() != null) {
            addCodecFactory(service, configuration.getCodec());
        } else if (configuration.isAllowDefaultCodec()) {
            configureDefaultCodecFactory(type, service);
        }
    }

    protected void configureDefaultCodecFactory(String type, IoService service) {
        if (configuration.isTextline()) {
            Charset charset = getEncodingParameter(type, configuration);
            LineDelimiter delimiter = getLineDelimiterParameter(configuration.getTextlineDelimiter());
            MinaTextLineCodecFactory codecFactory = new MinaTextLineCodecFactory(charset, delimiter);
            if (configuration.getEncoderMaxLineLength() > 0) {
                codecFactory.setEncoderMaxLineLength(configuration.getEncoderMaxLineLength());
            }
            if (configuration.getDecoderMaxLineLength() > 0) {
                codecFactory.setDecoderMaxLineLength(configuration.getDecoderMaxLineLength());
            }
            addCodecFactory(service, codecFactory);
            LOG.debug("{}: Using TextLineCodecFactory: {} using encoding: {} line delimiter: {}({})",
                    type, codecFactory, charset, configuration.getTextlineDelimiter(), delimiter);
            LOG.debug("Encoder maximum line length: {}. Decoder maximum line length: {}",
                    codecFactory.getEncoderMaxLineLength(), codecFactory.getDecoderMaxLineLength());
        } else {
            ObjectSerializationCodecFactory codecFactory = new ObjectSerializationCodecFactory();
            addCodecFactory(service, codecFactory);
            LOG.debug("{}: Using ObjectSerializationCodecFactory: {}", type, codecFactory);
        }
    }

    protected void setupDatagramProtocol(String uri) {
        boolean minaLogger = configuration.isMinaLogger();
        boolean transferExchange = configuration.isTransferExchange();
        List<IoFilter> filters = configuration.getFilters();

        if (transferExchange) {
            throw new IllegalArgumentException("transferExchange=true is not supported for datagram protocol");
        }

        address = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        final int processorCount = Runtime.getRuntime().availableProcessors() + 1;
        connector = new NioDatagramConnector(processorCount);

        if (configuration.isOrderedThreadPoolExecutor()) {
            workerPool = new OrderedThreadPoolExecutor(configuration.getMaximumPoolSize());
        } else {
            workerPool = new UnorderedThreadPoolExecutor(configuration.getMaximumPoolSize());
        }
        connectorConfig = connector.getSessionConfig();
        connector.getFilterChain().addLast("threadPool", new ExecutorFilter(workerPool));
        if (minaLogger) {
            connector.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, connector.getFilterChain());
        if (configuration.getSslContextParameters() != null) {
            LOG.warn("Using datagram protocol, " + configuration.getProtocol()
                     + ", but an SSLContextParameters instance was provided.  SSLContextParameters is only supported on the TCP protocol.");
        }
        configureDataGramCodecFactory("MinaProducer", connector, configuration);
        // set connect timeout to mina in seconds
        connector.setConnectTimeoutMillis(timeout);
    }

    /**
     * For datagrams the entire message is available as a single IoBuffer so lets just pass those around by default
     * and try converting whatever they payload is into IoBuffer unless some custom converter is specified
     */
    protected void configureDataGramCodecFactory(final String type, final IoService service, final MinaConfiguration configuration) {
        ProtocolCodecFactory codecFactory = configuration.getCodec();
        if (codecFactory == null) {
            codecFactory = new MinaUdpProtocolCodecFactory(this.getEndpoint().getCamelContext());

            if (LOG.isDebugEnabled()) {
                LOG.debug("{}: Using CodecFactory: {}", type, codecFactory);
            }
        }

        addCodecFactory(service, codecFactory);
    }

    private void addCodecFactory(IoService service, ProtocolCodecFactory codecFactory) {
        LOG.debug("addCodecFactory name: {}", codecFactory.getClass().getName());

        service.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
    }

    private static LineDelimiter getLineDelimiterParameter(MinaTextLineDelimiter delimiter) {
        if (delimiter == null) {
            return LineDelimiter.DEFAULT;
        }
        return delimiter.getLineDelimiter();
    }

    private Charset getEncodingParameter(String type, MinaConfiguration configuration) {
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

    private void setSocketAddress(String protocol) {
        if (protocol.equals("tcp")) {
            this.address = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        } else if (configuration.isDatagramProtocol()) {
            this.address = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        } else if (protocol.equals("vm")) {
            this.address = new VmPipeAddress(configuration.getPort());
        }
    }

    /**
     * Handles response from session writes
     */
    private final class ResponseHandler extends IoHandlerAdapter {

        private Object message;
        private Throwable cause;
        private boolean messageReceived;

        public void reset() {
            this.message = null;
            this.cause = null;
            this.messageReceived = false;
        }

        @Override
        public void messageReceived(IoSession ioSession, Object message) throws Exception {
            LOG.debug("Message received: {}", message);
            this.message = message;
            messageReceived = true;
            cause = null;
            notifyResultAvailable();
        }

        protected void notifyResultAvailable() {
            CountDownLatch downLatch = responseLatch;
            if (downLatch != null) {
                downLatch.countDown();
            }
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            if (sync && !messageReceived) {
                // sync=true (InOut mode) so we expected a message as reply but did not get one before the session is closed
                LOG.debug("Session closed but no message received from address: {}", address);
                // session was closed but no message received. This could be because the remote server had an internal error
                // and could not return a response. We should count down to stop waiting for a response
                notifyResultAvailable();
            }
            notifySessionClosed();
        }

        private void notifySessionClosed() {
            if (closeLatch != null) {
                closeLatch.countDown();
            }
        }

        @Override
        public void exceptionCaught(IoSession ioSession, Throwable cause) {
            this.message = null;
            this.messageReceived = false;
            this.cause = cause;
            if (ioSession != null && !closedByMina(cause)) {
                CloseFuture closeFuture = ioSession.closeNow();
                closeFuture.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS);
            }
        }

        private boolean closedByMina(Throwable cause) {
            return cause instanceof IOException;
        }

        public Throwable getCause() {
            return this.cause;
        }

        public Object getMessage() {
            return this.message;
        }

        public boolean isMessageReceived() {
            return messageReceived;
        }
    }
}
