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
package org.apache.camel.component.mina2;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor;
import org.apache.mina.transport.socket.nio.NioProcessor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Consumer Consumer} implementation for Apache MINA.
 *
 * @version 
 */
public class Mina2Consumer extends DefaultConsumer {

    private static final transient Logger LOG = LoggerFactory.getLogger(Mina2Consumer.class);
    private SocketAddress address;
    private IoAcceptor acceptor;
    private Mina2Configuration configuration;

    public Mina2Consumer(final Mina2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.configuration = endpoint.getConfiguration();
        //
        // All mina2 endpoints are InOut. The endpoints are asynchronous. 
        // Endpoints can send "n" messages and receive "m" messages.
        //
        this.getEndpoint().setExchangePattern(ExchangePattern.InOut);

        String protocol = configuration.getProtocol();
        if (protocol.equals("tcp")) {
            createSocketEndpoint(protocol, configuration);
        } else if (configuration.isDatagramProtocol()) {
            createDatagramEndpoint(protocol, configuration);
        } else if (protocol.equals("vm")) {
            createVmEndpoint(protocol, configuration);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        acceptor.setHandler(new ReceiveHandler());
        acceptor.bind(address);
        LOG.info("Bound to server address: {} using acceptor: {}", address, acceptor);
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Unbinding from server address: {} using acceptor: {}", address, acceptor);
        acceptor.unbind(address);
        super.doStop();
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void createVmEndpoint(String uri, Mina2Configuration configuration) {

        boolean minaLogger = configuration.isMinaLogger();
        List<IoFilter> filters = configuration.getFilters();

        address = new VmPipeAddress(configuration.getPort());
        acceptor = new VmPipeAcceptor();

        // acceptor connectorConfig
        configureCodecFactory("Mina2Consumer", acceptor, configuration);
        if (minaLogger) {
            acceptor.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, acceptor.getFilterChain());
    }

    protected void createSocketEndpoint(String uri, Mina2Configuration configuration) {
        LOG.debug("createSocketEndpoint");
        boolean minaLogger = configuration.isMinaLogger();
        List<IoFilter> filters = configuration.getFilters();

        address = new InetSocketAddress(configuration.getHost(), configuration.getPort());

        acceptor = new NioSocketAcceptor(
            new NioProcessor(this.getEndpoint().getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "MinaSocketAcceptor")));

        // acceptor connectorConfig
        configureCodecFactory("Mina2Consumer", acceptor, configuration);
        ((NioSocketAcceptor) acceptor).setReuseAddress(true);
        acceptor.setCloseOnDeactivation(true);
        acceptor.getFilterChain().addLast("threadPool",
                                          new ExecutorFilter(this.getEndpoint().getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "MinaThreadPool")));
        if (minaLogger) {
            acceptor.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, acceptor.getFilterChain());
    }

    protected void configureCodecFactory(String type, IoService service, Mina2Configuration configuration) {
        if (configuration.getCodec() != null) {
            addCodecFactory(service, configuration.getCodec());
        } else if (configuration.isAllowDefaultCodec()) {
            configureDefaultCodecFactory(type, service, configuration);
        }
    }

    protected void configureDefaultCodecFactory(String type, IoService service, Mina2Configuration configuration) {
        if (configuration.isTextline()) {
            Charset charset = getEncodingParameter(type, configuration);
            LineDelimiter delimiter = getLineDelimiterParameter(configuration.getTextlineDelimiter());
            Mina2TextLineCodecFactory codecFactory = new Mina2TextLineCodecFactory(charset, delimiter);
            if (configuration.getEncoderMaxLineLength() > 0) {
                codecFactory.setEncoderMaxLineLength(configuration.getEncoderMaxLineLength());
            }
            if (configuration.getDecoderMaxLineLength() > 0) {
                codecFactory.setDecoderMaxLineLength(configuration.getDecoderMaxLineLength());
            }
            addCodecFactory(service, codecFactory);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}: Using TextLineCodecFactory: {} using encoding: {} line delimiter: {}({})",
                          new Object[]{type, codecFactory, charset, configuration.getTextlineDelimiter(), delimiter});
                LOG.debug("Encoder maximum line length: {}. Decoder maximum line length: {}",
                          codecFactory.getEncoderMaxLineLength(), codecFactory.getDecoderMaxLineLength());
            }
        } else {
            ObjectSerializationCodecFactory codecFactory = new ObjectSerializationCodecFactory();
            addCodecFactory(service, codecFactory);
            LOG.debug("{}: Using ObjectSerializationCodecFactory: {}", type, codecFactory);
        }

    }

    protected void createDatagramEndpoint(String uri, Mina2Configuration configuration) {
        boolean minaLogger = configuration.isMinaLogger();
        List<IoFilter> filters = configuration.getFilters();

        address = new InetSocketAddress(configuration.getHost(), configuration.getPort());
        acceptor = new NioDatagramAcceptor(this.getEndpoint().getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "MinaDatagramAcceptor"));

        // acceptor connectorConfig
        configureDataGramCodecFactory("MinaConsumer", acceptor, configuration);
        acceptor.setCloseOnDeactivation(true);
        // reuse address is default true for datagram
        //acceptor.getFilterChain().addLast("threadPool",
        //                                  new ExecutorFilter(this.getEndpoint().getCamelContext().getExecutorServiceStrategy().newDefaultThreadPool(this, "MinaThreadPool")));
        if (minaLogger) {
            acceptor.getFilterChain().addLast("logger", new LoggingFilter());
        }
        appendIoFiltersToChain(filters, acceptor.getFilterChain());
    }

    /**
     * For datagrams the entire message is available as a single IoBuffer so lets just pass those around by default
     * and try converting whatever they payload is into IoBuffer unless some custom converter is specified
     */
    protected void configureDataGramCodecFactory(final String type, final IoService service, final Mina2Configuration configuration) {
        ProtocolCodecFactory codecFactory = configuration.getCodec();
        if (codecFactory == null) {
            final Charset charset = getEncodingParameter(type, configuration);

            codecFactory = new Mina2UdpProtocolCodecFactory(this.getEndpoint().getCamelContext(), charset);

            if (LOG.isDebugEnabled()) {
                LOG.debug("{}: Using CodecFactory: {} using encoding: {}", new Object[]{type, codecFactory, charset});
            }
        }

        addCodecFactory(service, codecFactory);
    }

    private void addCodecFactory(IoService service, ProtocolCodecFactory codecFactory) {
        service.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
    }

    private static LineDelimiter getLineDelimiterParameter(Mina2TextLineDelimiter delimiter) {
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

    private Charset getEncodingParameter(String type, Mina2Configuration configuration) {
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

    @Override
    public Mina2Endpoint getEndpoint() {
        return (Mina2Endpoint) super.getEndpoint();
    }

    public IoAcceptor getAcceptor() {
        return acceptor;
    }

    public void setAcceptor(IoAcceptor acceptor) {
        this.acceptor = acceptor;
    }

    /**
     * Handles consuming messages and replying if the exchange is out capable.
     */
    private final class ReceiveHandler extends IoHandlerAdapter {

        @Override
        public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
            // close invalid session
            if (session != null) {
                LOG.warn("Closing session as an exception was thrown from MINA");
                session.close(true);
            }

            // must wrap and rethrow since cause can be of Throwable and we must only throw Exception
            throw new CamelException(cause);
        }

        @Override
        public void messageReceived(IoSession session, Object object) throws Exception {
            // log what we received
            if (LOG.isDebugEnabled()) {
                Object in = object;
                if (in instanceof byte[]) {
                    // byte arrays is not readable so convert to string
                    in = getEndpoint().getCamelContext().getTypeConverter().convertTo(String.class, in);
                }
                LOG.debug("Received body: {}", in);
            }

            Exchange exchange = getEndpoint().createExchange(session, object);
            //Set the exchange charset property for converting
            if (getEndpoint().getConfiguration().getCharsetName() != null) {
                exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.normalizeCharset(getEndpoint().getConfiguration().getCharsetName()));
            }

            try {
                getProcessor().process(exchange);
            } catch (Throwable e) {
                getExceptionHandler().handleException(e);
            }

            //
            // If there's a response to send, send it.
            //
            boolean disconnect = getEndpoint().getConfiguration().isDisconnect();
            Object response = null;
            response = Mina2PayloadHelper.getOut(getEndpoint(), exchange);

            boolean failed = exchange.isFailed();
            if (failed && !getEndpoint().getConfiguration().isTransferExchange()) {
                if (exchange.getException() != null) {
                    response = exchange.getException();
                } else {
                    // failed and no exception, must be a fault
                    response = exchange.getOut().getBody();
                }
            }

            if (response != null) {
                LOG.debug("Writing body: {}", response);
                Mina2Helper.writeBody(session, response, exchange);
            } else {
                LOG.debug("Writing no response");
                disconnect = Boolean.TRUE;
            }

            // should session be closed after complete?
            Boolean close;
            if (ExchangeHelper.isOutCapable(exchange)) {
                close = exchange.getOut().getHeader(Mina2Constants.MINA_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
            } else {
                close = exchange.getIn().getHeader(Mina2Constants.MINA_CLOSE_SESSION_WHEN_COMPLETE, Boolean.class);
            }

            // should we disconnect, the header can override the configuration
            if (close != null) {
                disconnect = close;
            }
            if (disconnect) {
                LOG.debug("Closing session when complete at address: {}", address);
                session.close(true);
            }
        }
    }
}
