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
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public class NettyConfiguration extends NettyServerBootstrapConfiguration implements Cloneable {
    private static final Logger LOG = LoggerFactory.getLogger(NettyConfiguration.class);

    @UriParam
    private long requestTimeout;
    @UriParam
    private boolean sync = true;
    @UriParam
    private boolean textline;
    @UriParam
    private TextLineDelimiter delimiter = TextLineDelimiter.LINE;
    @UriParam
    private boolean autoAppendDelimiter = true;
    @UriParam
    private int decoderMaxLineLength = 1024;
    @UriParam
    private String encoding;
    private List<ChannelHandler> encoders = new ArrayList<ChannelHandler>();
    private List<ChannelHandler> decoders = new ArrayList<ChannelHandler>();
    @UriParam
    private boolean disconnect;
    @UriParam
    private boolean lazyChannelCreation = true;
    @UriParam
    private boolean transferExchange;
    @UriParam
    private boolean disconnectOnNoReply = true;
    @UriParam
    private LoggingLevel noReplyLogLevel = LoggingLevel.WARN;
    @UriParam
    private LoggingLevel serverExceptionCaughtLogLevel = LoggingLevel.WARN;
    @UriParam
    private LoggingLevel serverClosedChannelExceptionCaughtLogLevel = LoggingLevel.DEBUG;
    @UriParam
    private boolean allowDefaultCodec = true;
    private ClientPipelineFactory clientPipelineFactory;
    @UriParam
    private int maximumPoolSize = 16;
    @UriParam
    // TODO we need to rename this property
    private boolean orderedThreadPoolExecutor = true;
    @UriParam
    private int producerPoolMaxActive = -1;
    @UriParam
    private int producerPoolMinIdle;
    @UriParam
    private int producerPoolMaxIdle = 100;
    @UriParam
    private long producerPoolMinEvictableIdle = 5 * 60 * 1000L;
    @UriParam
    private boolean producerPoolEnabled = true;

    /**
     * Returns a copy of this configuration
     */
    public NettyConfiguration copy() {
        try {
            NettyConfiguration answer = (NettyConfiguration) clone();
            // make sure the lists is copied in its own instance
            List<ChannelHandler> encodersCopy = new ArrayList<ChannelHandler>(encoders);
            answer.setEncoders(encodersCopy);
            List<ChannelHandler> decodersCopy = new ArrayList<ChannelHandler>(decoders);
            answer.setDecoders(decodersCopy);
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void validateConfiguration() {
        // validate that the encoders is either shareable or is a handler factory
        for (ChannelHandler encoder : encoders) {
            if (encoder instanceof ChannelHandlerFactory) {
                continue;
            }
            if (ObjectHelper.getAnnotation(encoder, ChannelHandler.Sharable.class) != null) {
                continue;
            }
            LOG.warn("The encoder {} is not @Shareable or an ChannelHandlerFactory instance. The encoder cannot safely be used.", encoder);
        }

        // validate that the decoders is either shareable or is a handler factory
        for (ChannelHandler decoder : decoders) {
            if (decoder instanceof ChannelHandlerFactory) {
                continue;
            }
            if (ObjectHelper.getAnnotation(decoder, ChannelHandler.Sharable.class) != null) {
                continue;
            }
            LOG.warn("The decoder {} is not @Shareable or an ChannelHandlerFactory instance. The decoder cannot safely be used.", decoder);
        }
        if (sslHandler != null) {
            boolean factory = sslHandler instanceof ChannelHandlerFactory;
            boolean shareable = ObjectHelper.getAnnotation(sslHandler, ChannelHandler.Sharable.class) != null;
            if (!factory && !shareable) {
                LOG.warn("The sslHandler {} is not @Shareable or an ChannelHandlerFactory instance. The sslHandler cannot safely be used.", sslHandler);
            }
        }
    }

    public void parseURI(URI uri, Map<String, Object> parameters, NettyComponent component, String... supportedProtocols) throws Exception {
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
        setPort(uri.getPort());

        ssl = component.getAndRemoveOrResolveReferenceParameter(parameters, "ssl", boolean.class, false);
        sslHandler = component.getAndRemoveOrResolveReferenceParameter(parameters, "sslHandler", SslHandler.class, sslHandler);
        passphrase = component.getAndRemoveOrResolveReferenceParameter(parameters, "passphrase", String.class, passphrase);
        keyStoreFormat = component.getAndRemoveOrResolveReferenceParameter(parameters, "keyStoreFormat", String.class, keyStoreFormat == null ? "JKS" : keyStoreFormat);
        securityProvider = component.getAndRemoveOrResolveReferenceParameter(parameters, "securityProvider", String.class, securityProvider == null ? "SunX509" : securityProvider);
        keyStoreFile = component.getAndRemoveOrResolveReferenceParameter(parameters, "keyStoreFile", File.class, keyStoreFile);
        trustStoreFile = component.getAndRemoveOrResolveReferenceParameter(parameters, "trustStoreFile", File.class, trustStoreFile);
        keyStoreResource = component.getAndRemoveOrResolveReferenceParameter(parameters, "keyStoreResource", String.class, keyStoreResource);
        trustStoreResource = component.getAndRemoveOrResolveReferenceParameter(parameters, "trustStoreResource", String.class, trustStoreResource);
        clientPipelineFactory = component.getAndRemoveOrResolveReferenceParameter(parameters, "clientPipelineFactory", ClientPipelineFactory.class, clientPipelineFactory);
        serverPipelineFactory = component.getAndRemoveOrResolveReferenceParameter(parameters, "serverPipelineFactory", ServerPipelineFactory.class, serverPipelineFactory);

        // set custom encoders and decoders first
        List<ChannelHandler> referencedEncoders = component.resolveAndRemoveReferenceListParameter(parameters, "encoders", ChannelHandler.class, null);
        addToHandlersList(encoders, referencedEncoders, ChannelHandler.class);
        List<ChannelHandler> referencedDecoders = component.resolveAndRemoveReferenceListParameter(parameters, "decoders", ChannelHandler.class, null);
        addToHandlersList(decoders, referencedDecoders, ChannelHandler.class);

        // then set parameters with the help of the camel context type converters
        EndpointHelper.setReferenceProperties(component.getCamelContext(), this, parameters);
        EndpointHelper.setProperties(component.getCamelContext(), this, parameters);

        // additional netty options, we don't want to store an empty map, so set it as null if empty
        options = IntrospectionSupport.extractProperties(parameters, "option.");
        if (options !=  null && options.isEmpty()) {
            options = null;
        }

        // add default encoders and decoders
        if (encoders.isEmpty() && decoders.isEmpty()) {
            if (allowDefaultCodec) {
                // are we textline or object?
                if (isTextline()) {
                    Charset charset = getEncoding() != null ? Charset.forName(getEncoding()) : CharsetUtil.UTF_8;
                    encoders.add(ChannelHandlerFactories.newStringEncoder(charset));
                    ByteBuf[] delimiters = delimiter == TextLineDelimiter.LINE ? Delimiters.lineDelimiter() : Delimiters.nulDelimiter();
                    decoders.add(ChannelHandlerFactories.newDelimiterBasedFrameDecoder(decoderMaxLineLength, delimiters));
                    decoders.add(ChannelHandlerFactories.newStringDecoder(charset));

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using textline encoders and decoders with charset: {}, delimiter: {} and decoderMaxLineLength: {}",
                                new Object[]{charset, delimiter, decoderMaxLineLength});
                    }
                } else {
                    // object serializable is then used
                    encoders.add(ChannelHandlerFactories.newObjectEncoder());
                    decoders.add(ChannelHandlerFactories.newObjectDecoder());

                    LOG.debug("Using object encoders and decoders");
                }
            } else {
                LOG.debug("No encoders and decoders will be used");
            }
        } else {
            LOG.debug("Using configured encoders and/or decoders");
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

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isTextline() {
        return textline;
    }

    public void setTextline(boolean textline) {
        this.textline = textline;
    }

    public int getDecoderMaxLineLength() {
        return decoderMaxLineLength;
    }

    public void setDecoderMaxLineLength(int decoderMaxLineLength) {
        this.decoderMaxLineLength = decoderMaxLineLength;
    }

    public TextLineDelimiter getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(TextLineDelimiter delimiter) {
        this.delimiter = delimiter;
    }

    public boolean isAutoAppendDelimiter() {
        return autoAppendDelimiter;
    }

    public void setAutoAppendDelimiter(boolean autoAppendDelimiter) {
        this.autoAppendDelimiter = autoAppendDelimiter;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public List<ChannelHandler> getDecoders() {
        return decoders;
    }

    public void setDecoders(List<ChannelHandler> decoders) {
        this.decoders = decoders;
    }

    public List<ChannelHandler> getEncoders() {
        return encoders;
    }

    public void setEncoders(List<ChannelHandler> encoders) {
        this.encoders = encoders;
    }

    public ChannelHandler getEncoder() {
        return encoders.isEmpty() ? null : encoders.get(0);
    }

    public void setEncoder(ChannelHandler encoder) {
        if (!encoders.contains(encoder)) {
            encoders.add(encoder);
        }
    }

    public ChannelHandler getDecoder() {
        return decoders.isEmpty() ? null : decoders.get(0);
    }

    public void setDecoder(ChannelHandler decoder) {
        if (!decoders.contains(decoder)) {
            decoders.add(decoder);
        }
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public boolean isLazyChannelCreation() {
        return lazyChannelCreation;
    }

    public void setLazyChannelCreation(boolean lazyChannelCreation) {
        this.lazyChannelCreation = lazyChannelCreation;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    public boolean isDisconnectOnNoReply() {
        return disconnectOnNoReply;
    }

    public void setDisconnectOnNoReply(boolean disconnectOnNoReply) {
        this.disconnectOnNoReply = disconnectOnNoReply;
    }

    public LoggingLevel getNoReplyLogLevel() {
        return noReplyLogLevel;
    }

    public void setNoReplyLogLevel(LoggingLevel noReplyLogLevel) {
        this.noReplyLogLevel = noReplyLogLevel;
    }

    public LoggingLevel getServerExceptionCaughtLogLevel() {
        return serverExceptionCaughtLogLevel;
    }

    public void setServerExceptionCaughtLogLevel(LoggingLevel serverExceptionCaughtLogLevel) {
        this.serverExceptionCaughtLogLevel = serverExceptionCaughtLogLevel;
    }

    public LoggingLevel getServerClosedChannelExceptionCaughtLogLevel() {
        return serverClosedChannelExceptionCaughtLogLevel;
    }

    public void setServerClosedChannelExceptionCaughtLogLevel(LoggingLevel serverClosedChannelExceptionCaughtLogLevel) {
        this.serverClosedChannelExceptionCaughtLogLevel = serverClosedChannelExceptionCaughtLogLevel;
    }

    public boolean isAllowDefaultCodec() {
        return allowDefaultCodec;
    }

    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        this.allowDefaultCodec = allowDefaultCodec;
    }

    public void setClientPipelineFactory(ClientPipelineFactory clientPipelineFactory) {
        this.clientPipelineFactory = clientPipelineFactory;
    }

    public ClientPipelineFactory getClientPipelineFactory() {
        return clientPipelineFactory;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public boolean isOrderedThreadPoolExecutor() {
        return orderedThreadPoolExecutor;
    }

    public void setOrderedThreadPoolExecutor(boolean orderedThreadPoolExecutor) {
        this.orderedThreadPoolExecutor = orderedThreadPoolExecutor;
    }

    public int getProducerPoolMaxActive() {
        return producerPoolMaxActive;
    }

    public void setProducerPoolMaxActive(int producerPoolMaxActive) {
        this.producerPoolMaxActive = producerPoolMaxActive;
    }

    public int getProducerPoolMinIdle() {
        return producerPoolMinIdle;
    }

    public void setProducerPoolMinIdle(int producerPoolMinIdle) {
        this.producerPoolMinIdle = producerPoolMinIdle;
    }

    public int getProducerPoolMaxIdle() {
        return producerPoolMaxIdle;
    }

    public void setProducerPoolMaxIdle(int producerPoolMaxIdle) {
        this.producerPoolMaxIdle = producerPoolMaxIdle;
    }

    public long getProducerPoolMinEvictableIdle() {
        return producerPoolMinEvictableIdle;
    }

    public void setProducerPoolMinEvictableIdle(long producerPoolMinEvictableIdle) {
        this.producerPoolMinEvictableIdle = producerPoolMinEvictableIdle;
    }

    public boolean isProducerPoolEnabled() {
        return producerPoolEnabled;
    }

    public void setProducerPoolEnabled(boolean producerPoolEnabled) {
        this.producerPoolEnabled = producerPoolEnabled;
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
