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

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.EndpointHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.CharsetUtil;

@SuppressWarnings("unchecked")
public class NettyConfiguration implements Cloneable {
    private static final transient Log LOG = LogFactory.getLog(NettyConfiguration.class);

    private String protocol;
    private String host;
    private int port;
    private boolean keepAlive = true;
    private boolean tcpNoDelay = true;
    private boolean broadcast;
    private long connectTimeout = 10000;
    private long timeout = 30000;
    private boolean reuseAddress = true;
    private boolean sync = true;
    private boolean textline;
    private TextLineDelimiter delimiter = TextLineDelimiter.LINE;
    private boolean autoAppendDelimiter = true;
    private int decoderMaxLineLength = 1024;
    private String encoding;
    private String passphrase;
    private File keyStoreFile;
    private File trustStoreFile;
    private SslHandler sslHandler;
    private List<ChannelDownstreamHandler> encoders = new ArrayList<ChannelDownstreamHandler>();
    private List<ChannelUpstreamHandler> decoders = new ArrayList<ChannelUpstreamHandler>();
    private boolean ssl;
    private long sendBufferSize = 65536;
    private long receiveBufferSize = 65536;
    private int corePoolSize = 10;
    private int maxPoolSize = 100;
    private String keyStoreFormat;
    private String securityProvider;
    private boolean disconnect;
    private boolean lazyChannelCreation = true;
    private boolean transferExchange;
    private boolean disconnectOnNoReply = true;
    private LoggingLevel noReplyLogLevel = LoggingLevel.WARN;
    private boolean allowDefaultCodec = true;
    private ClientPipelineFactory clientPipelineFactory;
    private ServerPipelineFactory serverPipelineFactory;
    
    /**
     * Returns a copy of this configuration
     */
    public NettyConfiguration copy() {
        try {
            NettyConfiguration answer = (NettyConfiguration) clone();
            // make sure the lists is copied in its own instance
            List<ChannelDownstreamHandler> encodersCopy = new ArrayList<ChannelDownstreamHandler>(encoders);
            answer.setEncoders(encodersCopy);
            List<ChannelUpstreamHandler> decodersCopy = new ArrayList<ChannelUpstreamHandler>(decoders);
            answer.setDecoders(decodersCopy);
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void parseURI(URI uri, Map<String, Object> parameters, NettyComponent component) throws Exception {
        protocol = uri.getScheme();

        if ((!protocol.equalsIgnoreCase("tcp")) && (!protocol.equalsIgnoreCase("udp"))) {
            throw new IllegalArgumentException("Unrecognized Netty protocol: " + protocol + " for uri: " + uri);
        }

        setHost(uri.getHost());
        setPort(uri.getPort());

        sslHandler = component.resolveAndRemoveReferenceParameter(parameters, "sslHandler", SslHandler.class, null);
        passphrase = component.resolveAndRemoveReferenceParameter(parameters, "passphrase", String.class, null);
        keyStoreFormat = component.getAndRemoveParameter(parameters, "keyStoreFormat", String.class, "JKS");
        securityProvider = component.getAndRemoveParameter(parameters, "securityProvider", String.class, "SunX509");
        keyStoreFile = component.resolveAndRemoveReferenceParameter(parameters, "keyStoreFile", File.class, null);
        trustStoreFile = component.resolveAndRemoveReferenceParameter(parameters, "trustStoreFile", File.class, null);
        clientPipelineFactory = component.resolveAndRemoveReferenceParameter(parameters, "clientPipelineFactory", ClientPipelineFactory.class, null);
        serverPipelineFactory = component.resolveAndRemoveReferenceParameter(parameters, "serverPipelineFactory", ServerPipelineFactory.class, null);

        // set custom encoders and decoders first
        List<ChannelDownstreamHandler> referencedEncoders = component.resolveAndRemoveReferenceListParameter(parameters, "encoders", ChannelDownstreamHandler.class, null);
        addToHandlersList(encoders, referencedEncoders, ChannelDownstreamHandler.class);
        List<ChannelUpstreamHandler> referencedDecoders = component.resolveAndRemoveReferenceListParameter(parameters, "decoders", ChannelUpstreamHandler.class, null);
        addToHandlersList(decoders, referencedDecoders, ChannelUpstreamHandler.class);

        // then set parameters with the help of the camel context type converters
        EndpointHelper.setProperties(component.getCamelContext(), this, parameters);

        // add default encoders and decoders
        if (encoders.isEmpty() && decoders.isEmpty()) {
            if (allowDefaultCodec) {
                // are we textline or object?
                if (isTextline()) {
                    Charset charset = getEncoding() != null ? Charset.forName(getEncoding()) : CharsetUtil.UTF_8;
                    encoders.add(new StringEncoder(charset));
                    decoders.add(new DelimiterBasedFrameDecoder(decoderMaxLineLength, true, delimiter == TextLineDelimiter.LINE ? Delimiters.lineDelimiter() : Delimiters.nulDelimiter()));
                    decoders.add(new StringDecoder(charset));

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using textline encoders and decoders with charset: " + charset + ", delimiter: "
                            + delimiter + " and decoderMaxLineLength: " + decoderMaxLineLength);
                    }
                } else {
                    // object serializable is then used
                    encoders.add(new ObjectEncoder());
                    decoders.add(new ObjectDecoder());

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Using object encoders and decoders");
                    }
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No encoders and decoders will be used");
                }
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

    public boolean isTcp() {
        return protocol.equalsIgnoreCase("tcp");
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
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

    public SslHandler getSslHandler() {
        return sslHandler;
    }

    public void setSslHandler(SslHandler sslHandler) {
        this.sslHandler = sslHandler;
    }

    public List<ChannelDownstreamHandler> getEncoders() {
        return encoders;
    }

    public List<ChannelUpstreamHandler> getDecoders() {
        return decoders;
    }

    public ChannelDownstreamHandler getEncoder() {
        return encoders.isEmpty() ? null : encoders.get(0);
    }

    public void setEncoder(ChannelDownstreamHandler encoder) {
        if (!encoders.contains(encoder)) {
            encoders.add(encoder);
        }
    }

    public void setEncoders(List<ChannelDownstreamHandler> encoders) {
        this.encoders = encoders;
    }

    public ChannelUpstreamHandler getDecoder() {
        return decoders.isEmpty() ? null : decoders.get(0);
    }

    public void setDecoder(ChannelUpstreamHandler decoder) {
        if (!decoders.contains(decoder)) {
            decoders.add(decoder);
        }
    }

    public void setDecoders(List<ChannelUpstreamHandler> decoders) {
        this.decoders = decoders;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(long sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public long getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(long receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public File getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(File keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public File getTrustStoreFile() {
        return trustStoreFile;
    }

    public void setTrustStoreFile(File trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public String getKeyStoreFormat() {
        return keyStoreFormat;
    }

    public void setKeyStoreFormat(String keyStoreFormat) {
        this.keyStoreFormat = keyStoreFormat;
    }

    public String getSecurityProvider() {
        return securityProvider;
    }

    public void setSecurityProvider(String securityProvider) {
        this.securityProvider = securityProvider;
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

    public boolean isAllowDefaultCodec() {
        return allowDefaultCodec;
    }

    public void setAllowDefaultCodec(boolean allowDefaultCodec) {
        this.allowDefaultCodec = allowDefaultCodec;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    private <T> void addToHandlersList(List configured, List handlers, Class<? extends T> handlerType) {
        if (handlers != null) {
            for (int x = 0; x < handlers.size(); x++) {
                Object handler = handlers.get(x);
                if (handlerType.isInstance(handler)) {
                    configured.add(handler);
                }
            }
        }
    }

    public void setClientPipelineFactory(ClientPipelineFactory clientPipelineFactory) {
        this.clientPipelineFactory = clientPipelineFactory;
    }

    public ClientPipelineFactory getClientPipelineFactory() {
        return clientPipelineFactory;
    }

    public void setServerPipelineFactory(ServerPipelineFactory serverPipelineFactory) {
        this.serverPipelineFactory = serverPipelineFactory;
    }

    public ServerPipelineFactory getServerPipelineFactory() {
        return serverPipelineFactory;
    }

}
