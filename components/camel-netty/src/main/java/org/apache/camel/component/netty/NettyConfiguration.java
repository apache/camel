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
package org.apache.camel.component.netty;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
@Configurer
public class NettyConfiguration extends NettyServerBootstrapConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(NettyConfiguration.class);

    private transient List<ChannelHandler> encodersList = new ArrayList<>();
    private transient List<ChannelHandler> decodersList = new ArrayList<>();

    @UriParam(label = "producer")
    private long requestTimeout;
    @UriParam(defaultValue = "true")
    private boolean sync = true;
    @UriParam(label = "codec")
    private boolean textline;
    @UriParam(label = "codec", defaultValue = "LINE")
    private TextLineDelimiter delimiter = TextLineDelimiter.LINE;
    @UriParam(label = "codec", defaultValue = "true")
    private boolean autoAppendDelimiter = true;
    @UriParam(label = "codec", defaultValue = "1024")
    private int decoderMaxLineLength = 1024;
    @UriParam(label = "codec")
    private String encoding;
    @UriParam(label = "codec")
    private String encoders;
    @UriParam(label = "codec")
    private String decoders;
    @UriParam(label = "common, security", defaultValue = "false")
    private boolean hostnameVerification;
    @UriParam
    private boolean disconnect;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean lazyChannelCreation = true;
    @UriParam(label = "advanced")
    private boolean transferExchange;
    @UriParam(label = "advanced", defaultValue = "false")
    private boolean allowSerializedHeaders;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean disconnectOnNoReply = true;
    @UriParam(label = "consumer,advanced", defaultValue = "WARN")
    private LoggingLevel noReplyLogLevel = LoggingLevel.WARN;
    @UriParam(label = "consumer,advanced", defaultValue = "WARN")
    private LoggingLevel serverExceptionCaughtLogLevel = LoggingLevel.WARN;
    @UriParam(label = "consumer,advanced", defaultValue = "DEBUG")
    private LoggingLevel serverClosedChannelExceptionCaughtLogLevel = LoggingLevel.DEBUG;
    @UriParam(label = "codec", defaultValue = "true")
    private boolean allowDefaultCodec = true;
    @UriParam(label = "producer,advanced")
    private ClientInitializerFactory clientInitializerFactory;
    @UriParam(label = "consumer,advanced", defaultValue = "true")
    private boolean usingExecutorService = true;
    @UriParam(label = "producer,advanced", defaultValue = "-1")
    private int producerPoolMaxTotal = -1;
    @UriParam(label = "producer,advanced")
    private int producerPoolMinIdle;
    @UriParam(label = "producer,advanced", defaultValue = "100")
    private int producerPoolMaxIdle = 100;
    @UriParam(label = "producer,advanced", defaultValue = "" + 5 * 60 * 1000L)
    private long producerPoolMinEvictableIdle = 5 * 60 * 1000L;
    @UriParam(label = "producer,advanced", defaultValue = "-1")
    private long producerPoolMaxWait = -1;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean producerPoolBlockWhenExhausted = true;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean producerPoolEnabled = true;
    @UriParam(label = "producer,advanced")
    private boolean udpConnectionlessSending;
    @UriParam(label = "consumer")
    private boolean clientMode;
    @UriParam(label = "producer,advanced")
    private boolean useByteBuf;
    @UriParam(label = "advanced")
    private boolean udpByteArrayCodec;
    @UriParam(label = "common")
    private boolean reuseChannel;
    @UriParam(label = "producer,advanced")
    private NettyCamelStateCorrelationManager correlationManager;

    /**
     * Returns a copy of this configuration
     */
    public NettyConfiguration copy() {
        try {
            NettyConfiguration answer = (NettyConfiguration) clone();
            // make sure the lists is copied in its own instance
            answer.setEncodersAsList(new ArrayList<>(getEncodersAsList()));
            answer.setDecodersAsList(new ArrayList<>(getDecodersAsList()));
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void validateConfiguration() {
        // validate that the encoders is either shareable or is a handler factory
        for (ChannelHandler encoder : encodersList) {
            if (encoder instanceof ChannelHandlerFactory) {
                continue;
            }
            if (ObjectHelper.getAnnotation(encoder, ChannelHandler.Sharable.class) != null) {
                continue;
            }
            LOG.warn(
                    "The encoder {} is not @Shareable or an ChannelHandlerFactory instance. The encoder cannot safely be used.",
                    encoder);
        }

        // validate that the decoders is either shareable or is a handler factory
        for (ChannelHandler decoder : decodersList) {
            if (decoder instanceof ChannelHandlerFactory) {
                continue;
            }
            if (ObjectHelper.getAnnotation(decoder, ChannelHandler.Sharable.class) != null) {
                continue;
            }
            LOG.warn(
                    "The decoder {} is not @Shareable or an ChannelHandlerFactory instance. The decoder cannot safely be used.",
                    decoder);
        }
        if (sslHandler != null) {
            boolean factory = sslHandler instanceof ChannelHandlerFactory;
            boolean shareable = ObjectHelper.getAnnotation(sslHandler, ChannelHandler.Sharable.class) != null;
            if (!factory && !shareable) {
                LOG.warn(
                        "The sslHandler {} is not @Shareable or an ChannelHandlerFactory instance. The sslHandler cannot safely be used.",
                        sslHandler);
            }
        }
    }

    public void parseURI(URI uri, Map<String, Object> parameters, NettyComponent component, String... supportedProtocols)
            throws Exception {
        protocol = uri.getScheme();

        boolean found = false;
        for (String supportedProtocol : supportedProtocols) {
            if (protocol != null && protocol.equalsIgnoreCase(supportedProtocol)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("Unrecognized Netty protocol: " + protocol + " for uri: " + uri);
        }

        setHost(uri.getHost());
        if (uri.getPort() != -1) {
            setPort(uri.getPort());
        }

        ssl = component.getAndRemoveOrResolveReferenceParameter(parameters, "ssl", boolean.class, ssl);
        sslHandler = component.getAndRemoveOrResolveReferenceParameter(parameters, "sslHandler", SslHandler.class, sslHandler);
        passphrase = component.getAndRemoveOrResolveReferenceParameter(parameters, "passphrase", String.class, passphrase);
        keyStoreFormat = component.getAndRemoveOrResolveReferenceParameter(parameters, "keyStoreFormat", String.class,
                keyStoreFormat == null ? "JKS" : keyStoreFormat);
        securityProvider = component.getAndRemoveOrResolveReferenceParameter(parameters, "securityProvider", String.class,
                securityProvider == null ? "SunX509" : securityProvider);
        keyStoreResource = uriRef(component, parameters, "keyStoreResource", keyStoreResource);
        trustStoreResource = uriRef(component, parameters, "trustStoreResource", trustStoreResource);
        clientInitializerFactory = component.getAndRemoveOrResolveReferenceParameter(parameters, "clientInitializerFactory",
                ClientInitializerFactory.class, clientInitializerFactory);
        serverInitializerFactory = component.getAndRemoveOrResolveReferenceParameter(parameters, "serverInitializerFactory",
                ServerInitializerFactory.class, serverInitializerFactory);

        // set custom encoders and decoders first
        List<ChannelHandler> referencedEncoders
                = component.resolveAndRemoveReferenceListParameter(parameters, "encoders", ChannelHandler.class, null);
        addToHandlersList(encodersList, referencedEncoders, ChannelHandler.class);
        List<ChannelHandler> referencedDecoders
                = component.resolveAndRemoveReferenceListParameter(parameters, "decoders", ChannelHandler.class, null);
        addToHandlersList(decodersList, referencedDecoders, ChannelHandler.class);

        // set custom encoders and decoders from config
        List<ChannelHandler> configEncoders
                = EndpointHelper.resolveReferenceListParameter(component.getCamelContext(), encoders, ChannelHandler.class);
        addToHandlersList(encodersList, configEncoders, ChannelHandler.class);
        List<ChannelHandler> configDecoders
                = EndpointHelper.resolveReferenceListParameter(component.getCamelContext(), decoders, ChannelHandler.class);
        addToHandlersList(decodersList, configDecoders, ChannelHandler.class);

        // then set parameters with the help of the camel context type converters
        // and use configurer to avoid any reflection calls
        PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(component.getCamelContext())
                .resolvePropertyConfigurer(this.getClass().getName(), component.getCamelContext());
        PropertyBindingSupport.build()
                .withCamelContext(component.getCamelContext())
                .withTarget(this)
                .withReflection(false)
                .withIgnoreCase(true)
                .withConfigurer(configurer)
                .withProperties(parameters)
                .bind();

        // additional netty options, we don't want to store an empty map, so set it as null if empty
        options = PropertiesHelper.extractProperties(parameters, "option.");
        if (options.isEmpty()) {
            options = null;
        }

        // add default encoders and decoders
        if (encodersList.isEmpty() && decodersList.isEmpty()) {
            if (isAllowDefaultCodec()) {
                if ("udp".equalsIgnoreCase(protocol)) {
                    encodersList.add(ChannelHandlerFactories.newDatagramPacketEncoder());
                }
                // are we textline or byte array
                if (isTextline()) {
                    Charset charset = getEncoding() != null ? Charset.forName(getEncoding()) : CharsetUtil.UTF_8;
                    encodersList.add(ChannelHandlerFactories.newStringEncoder(charset, protocol));
                    ByteBuf[] delimiters
                            = delimiter == TextLineDelimiter.LINE ? Delimiters.lineDelimiter() : Delimiters.nulDelimiter();
                    decodersList.add(
                            ChannelHandlerFactories.newDelimiterBasedFrameDecoder(decoderMaxLineLength, delimiters, protocol));
                    decodersList.add(ChannelHandlerFactories.newStringDecoder(charset, protocol));

                    LOG.debug(
                            "Using textline encoders and decoders with charset: {}, delimiter: {} and decoderMaxLineLength: {}",
                            charset, delimiter, decoderMaxLineLength);

                } else if ("udp".equalsIgnoreCase(protocol) && isUdpByteArrayCodec()) {
                    encodersList.add(ChannelHandlerFactories.newByteArrayEncoder(protocol));
                    decodersList.add(ChannelHandlerFactories.newByteArrayDecoder(protocol));
                } else {
                    // Fall back to allowing Strings to be serialized only
                    Charset charset = getEncoding() != null ? Charset.forName(getEncoding()) : CharsetUtil.UTF_8;
                    encodersList.add(ChannelHandlerFactories.newStringEncoder(charset, protocol));
                    decodersList.add(ChannelHandlerFactories.newStringDecoder(charset, protocol));
                }
                if ("udp".equalsIgnoreCase(protocol)) {
                    decodersList.add(ChannelHandlerFactories.newDatagramPacketDecoder());
                }
            } else {
                LOG.debug("No encoders and decoders will be used");
            }
        } else {
            LOG.debug("Using configured encoders and/or decoders");
        }
    }

    private String uriRef(NettyComponent component, Map<String, Object> parameters, String key, String defaultValue) {
        Object value = parameters.remove(key);
        if (value == null) {
            value = defaultValue;
        } else if (value instanceof String && EndpointHelper.isReferenceParameter((String) value)) {
            String name = ((String) value).replace("#", "");
            value = CamelContextHelper.mandatoryLookup(component.getCamelContext(), name);
        }
        if (value instanceof File) {
            return "file:" + value.toString();
        } else if (value != null) {
            return value.toString();
        } else {
            return null;
        }
    }

    public String getCharsetName() {
        if (encoding == null) {
            return null;
        }
        if (!Charset.isSupported(encoding)) {
            throw new IllegalArgumentException("The encoding: " + encoding + " is not supported");
        }

        return Charset.forName(encoding).name();
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Allows to use a timeout for the Netty producer when calling a remote server. By default no timeout is in use. The
     * value is in milli seconds, so eg 30000 is 30 seconds. The requestTimeout is using Netty's ReadTimeoutHandler to
     * trigger the timeout.
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isSync() {
        return sync;
    }

    /**
     * Setting to set endpoint as one-way or request-response
     */
    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isTextline() {
        return textline;
    }

    /**
     * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line based codec; if not
     * specified or the value is false, then Object Serialization is assumed over TCP - however only Strings are allowed
     * to be serialized by default.
     */
    public void setTextline(boolean textline) {
        this.textline = textline;
    }

    public int getDecoderMaxLineLength() {
        return decoderMaxLineLength;
    }

    /**
     * The max line length to use for the textline codec.
     */
    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        this.decoderMaxLineLength = decoderMaxLineLength;
    }

    public TextLineDelimiter getDelimiter() {
        return delimiter;
    }

    /**
     * The delimiter to use for the textline codec. Possible values are LINE and NULL.
     */
    public void setDelimiter(TextLineDelimiter delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isAutoAppendDelimiter() {
        return autoAppendDelimiter;
    }

    /**
     * Whether or not to auto append missing end delimiter when sending using the textline codec.
     */
    public void setAutoAppendDelimiter(boolean autoAppendDelimiter) {
        this.autoAppendDelimiter = autoAppendDelimiter;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * The encoding (a charset name) to use for the textline codec. If not provided, Camel will use the JVM default
     * Charset.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public List<ChannelHandler> getDecodersAsList() {
        return decodersList;
    }

    public void setDecodersAsList(List<ChannelHandler> decoders) {
        this.decodersList = decoders;
    }

    /**
     * A list of decoders to be used. You can use a String which have values separated by comma, and have the values be
     * looked up in the Registry. Just remember to prefix the value with # so Camel knows it should lookup.
     */
    public void setDecoders(List<ChannelHandler> decoders) {
        this.decodersList = decoders;
    }

    /**
     * A list of decoders to be used. You can use a String which have values separated by comma, and have the values be
     * looked up in the Registry. Just remember to prefix the value with # so Camel knows it should lookup.
     */
    public void setDecoders(String decoders) {
        this.decoders = decoders;
    }

    public String getDecoders() {
        return decoders;
    }

    public List<ChannelHandler> getEncodersAsList() {
        return encodersList;
    }

    public void setEncodersAsList(List<ChannelHandler> encoders) {
        this.encodersList = encoders;
    }

    /**
     * A list of encoders to be used. You can use a String which have values separated by comma, and have the values be
     * looked up in the Registry. Just remember to prefix the value with # so Camel knows it should lookup.
     */
    public void setEncoders(List<ChannelHandler> encoders) {
        this.encodersList = encoders;
    }

    /**
     * A list of encoders to be used. You can use a String which have values separated by comma, and have the values be
     * looked up in the Registry. Just remember to prefix the value with # so Camel knows it should lookup.
     */
    public void setEncoders(String encoders) {
        this.encoders = encoders;
    }

    public String getEncoders() {
        return encoders;
    }

    /**
     * Adds a custom ChannelHandler class that can be used to perform special marshalling of outbound payloads.
     */
    public void addEncoder(ChannelHandler encoder) {
        if (!encodersList.contains(encoder)) {
            encodersList.add(encoder);
        }
    }

    /**
     * Adds a custom ChannelHandler class that can be used to perform special marshalling of inbound payloads.
     */
    public void addDecoder(ChannelHandler decoder) {
        if (!decodersList.contains(decoder)) {
            decodersList.add(decoder);
        }
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    /**
     * Whether or not to disconnect(close) from Netty Channel right after use. Can be used for both consumer and
     * producer.
     */
    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isLazyChannelCreation() {
        return lazyChannelCreation;
    }

    /**
     * Channels can be lazily created to avoid exceptions, if the remote server is not up and running when the Camel
     * producer is started.
     */
    public void setLazyChannelCreation(boolean lazyChannelCreation) {
        this.lazyChannelCreation = lazyChannelCreation;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

    /**
     * Only used for TCP. You can transfer the exchange over the wire instead of just the body. The following fields are
     * transferred: In body, Out body, fault body, In headers, Out headers, fault headers, exchange properties, exchange
     * exception. This requires that the objects are serializable. Camel will exclude any non-serializable objects and
     * log it at WARN level.
     */
    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    /**
     * Only used for TCP when transferExchange is true. When set to true, serializable objects in headers and properties
     * will be added to the exchange. Otherwise Camel will exclude any non-serializable objects and log it at WARN
     * level.
     */
    public void setAllowSerializedHeaders(final boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    public boolean isDisconnectOnNoReply() {
        return disconnectOnNoReply;
    }

    /**
     * If sync is enabled then this option dictates NettyConsumer if it should disconnect where there is no reply to
     * send back.
     */
    public void setDisconnectOnNoReply(boolean disconnectOnNoReply) {
        this.disconnectOnNoReply = disconnectOnNoReply;
    }

    public LoggingLevel getNoReplyLogLevel() {
        return noReplyLogLevel;
    }

    /**
     * If sync is enabled this option dictates NettyConsumer which logging level to use when logging a there is no reply
     * to send back.
     */
    public void setNoReplyLogLevel(LoggingLevel noReplyLogLevel) {
        this.noReplyLogLevel = noReplyLogLevel;
    }

    public LoggingLevel getServerExceptionCaughtLogLevel() {
        return serverExceptionCaughtLogLevel;
    }

    /**
     * If the server (NettyConsumer) catches an exception then its logged using this logging level.
     */
    public void setServerExceptionCaughtLogLevel(LoggingLevel serverExceptionCaughtLogLevel) {
        this.serverExceptionCaughtLogLevel = serverExceptionCaughtLogLevel;
    }

    public LoggingLevel getServerClosedChannelExceptionCaughtLogLevel() {
        return serverClosedChannelExceptionCaughtLogLevel;
    }

    /**
     * If the server (NettyConsumer) catches an java.nio.channels.ClosedChannelException then its logged using this
     * logging level. This is used to avoid logging the closed channel exceptions, as clients can disconnect abruptly
     * and then cause a flood of closed exceptions in the Netty server.
     */
    public void setServerClosedChannelExceptionCaughtLogLevel(LoggingLevel serverClosedChannelExceptionCaughtLogLevel) {
        this.serverClosedChannelExceptionCaughtLogLevel = serverClosedChannelExceptionCaughtLogLevel;
    }

    public boolean isAllowDefaultCodec() {
        return allowDefaultCodec;
    }

    /**
     * The netty component installs a default codec if both, encoder/decoder is null and textline is false. Setting
     * allowDefaultCodec to false prevents the netty component from installing a default codec as the first element in
     * the filter chain.
     */
    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        this.allowDefaultCodec = allowDefaultCodec;
    }

    /**
     * @deprecated use #setClientInitializerFactory
     */
    @Deprecated
    public void setClientPipelineFactory(ClientInitializerFactory clientPipelineFactory) {
        this.clientInitializerFactory = clientPipelineFactory;
    }

    /**
     * @deprecated use #getClientInitializerFactory
     */
    @Deprecated
    public ClientInitializerFactory getClientPipelineFactory() {
        return clientInitializerFactory;
    }

    public ClientInitializerFactory getClientInitializerFactory() {
        return clientInitializerFactory;
    }

    /**
     * To use a custom ClientInitializerFactory
     */
    public void setClientInitializerFactory(ClientInitializerFactory clientInitializerFactory) {
        this.clientInitializerFactory = clientInitializerFactory;
    }

    public boolean isUsingExecutorService() {
        return usingExecutorService;
    }

    /**
     * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
     */
    public void setUsingExecutorService(boolean usingExecutorService) {
        this.usingExecutorService = usingExecutorService;
    }

    public int getProducerPoolMaxTotal() {
        return producerPoolMaxTotal;
    }

    /**
     * Sets the cap on the number of objects that can be allocated by the pool (checked out to clients, or idle awaiting
     * checkout) at a given time. Use a negative value for no limit.
     */
    public void setProducerPoolMaxTotal(int producerPoolMaxTotal) {
        this.producerPoolMaxTotal = producerPoolMaxTotal;
    }

    public int getProducerPoolMinIdle() {
        return producerPoolMinIdle;
    }

    /**
     * Sets the minimum number of instances allowed in the producer pool before the evictor thread (if active) spawns
     * new objects.
     */
    public void setProducerPoolMinIdle(int producerPoolMinIdle) {
        this.producerPoolMinIdle = producerPoolMinIdle;
    }

    public int getProducerPoolMaxIdle() {
        return producerPoolMaxIdle;
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool.
     */
    public void setProducerPoolMaxIdle(int producerPoolMaxIdle) {
        this.producerPoolMaxIdle = producerPoolMaxIdle;
    }

    public long getProducerPoolMinEvictableIdle() {
        return producerPoolMinEvictableIdle;
    }

    /**
     * Sets the minimum amount of time (value in millis) an object may sit idle in the pool before it is eligible for
     * eviction by the idle object evictor.
     */
    public void setProducerPoolMinEvictableIdle(long producerPoolMinEvictableIdle) {
        this.producerPoolMinEvictableIdle = producerPoolMinEvictableIdle;
    }

    public long getProducerPoolMaxWait() {
        return producerPoolMaxWait;
    }

    /**
     * Sets the maximum duration (value in millis) the borrowObject() method should block before throwing an exception
     * when the pool is exhausted and producerPoolBlockWhenExhausted is true. When less than 0, the borrowObject()
     * method may block indefinitely.
     */
    public void setProducerPoolMaxWait(long producerPoolMaxWait) {
        this.producerPoolMaxWait = producerPoolMaxWait;
    }

    public boolean isProducerPoolBlockWhenExhausted() {
        return producerPoolBlockWhenExhausted;
    }

    /**
     * Sets the value for the blockWhenExhausted configuration attribute. It determines whether to block when the
     * borrowObject() method is invoked when the pool is exhausted (the maximum number of "active" objects has been
     * reached).
     */
    public void setProducerPoolBlockWhenExhausted(boolean producerPoolBlockWhenExhausted) {
        this.producerPoolBlockWhenExhausted = producerPoolBlockWhenExhausted;
    }

    public boolean isProducerPoolEnabled() {
        return producerPoolEnabled;
    }

    /**
     * Whether producer pool is enabled or not.
     * <p>
     * Important: If you turn this off then a single shared connection is used for the producer, also if you are doing
     * request/reply. That means there is a potential issue with interleaved responses if replies comes back
     * out-of-order. Therefore you need to have a correlation id in both the request and reply messages so you can
     * properly correlate the replies to the Camel callback that is responsible for continue processing the message in
     * Camel. To do this you need to implement {@link NettyCamelStateCorrelationManager} as correlation manager and
     * configure it via the <tt>correlationManager</tt> option.
     * <p/>
     * See also the <tt>correlationManager</tt> option for more details.
     */
    public void setProducerPoolEnabled(boolean producerPoolEnabled) {
        this.producerPoolEnabled = producerPoolEnabled;
    }

    public boolean isUdpConnectionlessSending() {
        return udpConnectionlessSending;
    }

    /**
     * This option supports connection less udp sending which is a real fire and forget. A connected udp send receive
     * the PortUnreachableException if no one is listen on the receiving port.
     */
    public void setUdpConnectionlessSending(boolean udpConnectionlessSending) {
        this.udpConnectionlessSending = udpConnectionlessSending;
    }

    public boolean isClientMode() {
        return clientMode;
    }

    /**
     * If the clientMode is true, netty consumer will connect the address as a TCP client.
     */
    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
    }

    public boolean isUseByteBuf() {
        return useByteBuf;
    }

    /**
     * If the useByteBuf is true, netty producer will turn the message body into {@link ByteBuf} before sending it out.
     */
    public void setUseByteBuf(boolean useByteBuf) {
        this.useByteBuf = useByteBuf;
    }

    public boolean isUdpByteArrayCodec() {
        return udpByteArrayCodec;
    }

    /**
     * For UDP only. If enabled the using byte array codec instead of Java serialization protocol.
     */
    public void setUdpByteArrayCodec(boolean udpByteArrayCodec) {
        this.udpByteArrayCodec = udpByteArrayCodec;
    }

    public boolean isReuseChannel() {
        return reuseChannel;
    }

    /**
     * This option allows producers and consumers (in client mode) to reuse the same Netty {@link Channel} for the
     * lifecycle of processing the {@link Exchange}. This is useful if you need to call a server multiple times in a
     * Camel route and want to use the same network connection. When using this, the channel is not returned to the
     * connection pool until the {@link Exchange} is done; or disconnected if the disconnect option is set to true.
     * <p/>
     * The reused {@link Channel} is stored on the {@link Exchange} as an exchange property with the key
     * {@link NettyConstants#NETTY_CHANNEL} which allows you to obtain the channel during routing and use it as well.
     */
    public void setReuseChannel(boolean reuseChannel) {
        this.reuseChannel = reuseChannel;
    }

    public NettyCamelStateCorrelationManager getCorrelationManager() {
        return correlationManager;
    }

    /**
     * To use a custom correlation manager to manage how request and reply messages are mapped when using request/reply
     * with the netty producer. This should only be used if you have a way to map requests together with replies such as
     * if there is correlation ids in both the request and reply messages. This can be used if you want to multiplex
     * concurrent messages on the same channel (aka connection) in netty. When doing this you must have a way to
     * correlate the request and reply messages so you can store the right reply on the inflight Camel Exchange before
     * its continued routed.
     * <p/>
     * We recommend extending the {@link TimeoutCorrelationManagerSupport} when you build custom correlation managers.
     * This provides support for timeout and other complexities you otherwise would need to implement as well.
     * <p/>
     * See also the <tt>producerPoolEnabled</tt> option for more details.
     */
    public void setCorrelationManager(NettyCamelStateCorrelationManager correlationManager) {
        this.correlationManager = correlationManager;
    }

    public boolean isHostnameVerification() {
        return hostnameVerification;
    }

    /**
     * To enable/disable hostname verification on SSLEngine
     */
    public void setHostnameVerification(boolean hostnameVerification) {
        this.hostnameVerification = hostnameVerification;
    }

    private static <T> void addToHandlersList(List<T> configured, List<T> handlers, Class<T> handlerType) {
        if (handlers != null) {
            for (T handler : handlers) {
                if (handlerType.isInstance(handler)) {
                    configured.add(handler);
                }
            }
        }
    }
}
