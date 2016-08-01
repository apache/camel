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

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.filter.codec.ProtocolCodecFactory;

/**
 * Component for Apache MINA 2.x.
 *
 * @version
 */
public class Mina2Component extends UriEndpointComponent {

    private Mina2Configuration configuration;

    public Mina2Component() {
        super(Mina2Endpoint.class);
    }

    public Mina2Component(CamelContext context) {
        super(context, Mina2Endpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Using the configuration which set by the component as a default one
        // Since the configuration's properties will be set by the URI
        // we need to copy or create a new MinaConfiguration here
        // Using the configuration which set by the component as a default one
        // Since the configuration's properties will be set by the URI
        // we need to copy or create a new MinaConfiguration here
        Mina2Configuration config;
        if (configuration != null) {
            config = configuration.copy();
        } else {
            config = new Mina2Configuration();
        }

        URI u = new URI(remaining);
        config.setHost(u.getHost());
        config.setPort(u.getPort());
        config.setProtocol(u.getScheme());
        config.setFilters(resolveAndRemoveReferenceListParameter(parameters, "filters", IoFilter.class));
        setProperties(config, parameters);

        return createEndpoint(uri, config);
    }

    public Endpoint createEndpoint(Mina2Configuration config) throws Exception {
        return createEndpoint(config.getUriString(), config);
    }

    private Endpoint createEndpoint(String uri, Mina2Configuration config) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "camelContext");
        String protocol = config.getProtocol();
        // if mistyped uri then protocol can be null

        Mina2Endpoint endpoint = null;
        if (protocol != null) {
            if (protocol.equals("tcp") || config.isDatagramProtocol() || protocol.equals("vm")) {
                endpoint = new Mina2Endpoint(uri, this, config);
            }
        }
        if (endpoint == null) {
            // protocol not resolved so error
            throw new IllegalArgumentException("Unrecognised MINA protocol: " + protocol + " for uri: " + uri);
        }

        // set sync or async mode after endpoint is created
        if (config.isSync()) {
            endpoint.setExchangePattern(ExchangePattern.InOut);
        } else {
            endpoint.setExchangePattern(ExchangePattern.InOnly);
        }

        return endpoint;
    }

    // Properties
    //-------------------------------------------------------------------------
    public Mina2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared mina configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(Mina2Configuration configuration) {
        this.configuration = configuration;
    }

    private Mina2Configuration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new Mina2Configuration());
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

    public Mina2TextLineDelimiter getTextlineDelimiter() {
        return getConfigurationOrCreate().getTextlineDelimiter();
    }

    /**
     * Only used for TCP and if textline=true. Sets the text line delimiter to use.
     * If none provided, Camel will use DEFAULT.
     * This delimiter is used to mark the end of text.
     * @param textlineDelimiter
     */
    public void setTextlineDelimiter(Mina2TextLineDelimiter textlineDelimiter) {
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

    public SSLContextParameters getSslContextParameters() {
        return getConfigurationOrCreate().getSslContextParameters();
    }

    /**
     * To configure SSL security.
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        getConfigurationOrCreate().setSslContextParameters(sslContextParameters);
    }

    public boolean isAutoStartTls() {
        return getConfigurationOrCreate().isAutoStartTls();
    }

    /**
     * Whether to auto start SSL handshake.
     * @param autoStartTls
     */
    public void setAutoStartTls(boolean autoStartTls) {
        getConfigurationOrCreate().setAutoStartTls(autoStartTls);
    }

    public int getMaximumPoolSize() {
        return getConfigurationOrCreate().getMaximumPoolSize();
    }

    /**
     * Number of worker threads in the worker pool for TCP and UDP
     * @param maximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        getConfigurationOrCreate().setMaximumPoolSize(maximumPoolSize);
    }

    public boolean isOrderedThreadPoolExecutor() {
        return getConfigurationOrCreate().isOrderedThreadPoolExecutor();
    }

    /**
     * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
     * @param orderedThreadPoolExecutor
     */
    public void setOrderedThreadPoolExecutor(boolean orderedThreadPoolExecutor) {
        getConfigurationOrCreate().setOrderedThreadPoolExecutor(orderedThreadPoolExecutor);
    }

    /**
     * Whether to create the InetAddress once and reuse. Setting this to false allows to pickup DNS changes in the network.
     * @param shouldCacheAddress
     */
    public void setCachedAddress(boolean shouldCacheAddress) {
        getConfigurationOrCreate().setCachedAddress(shouldCacheAddress);
    }

    public boolean isCachedAddress() {
        return getConfigurationOrCreate().isCachedAddress();
    }

    /**
     * If the clientMode is true, mina consumer will connect the address as a TCP client.
     * @param clientMode
     */
    public void setClientMode(boolean clientMode) {
        getConfigurationOrCreate().setClientMode(clientMode);
    }

    public boolean isClientMode() {
        return getConfigurationOrCreate().isClientMode();
    }

    public String getUriString() {
        return getConfigurationOrCreate().getUriString();
    }
}
