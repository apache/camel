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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.URISupport;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

@SuppressWarnings("unchecked")
public class NettyConfiguration {
    private String protocol;
    private String host;
    private int port;
    private boolean keepAlive;
    private boolean tcpNoDelay;
    private boolean broadcast;
    private long connectTimeoutMillis;
    private long receiveTimeoutMillis;
    private boolean reuseAddress;
    private boolean sync;
    private String passphrase;
    private File keyStoreFile;
    private File trustStoreFile;
    private SslHandler sslHandler;
    private List<ChannelDownstreamHandler> encoders = new ArrayList<ChannelDownstreamHandler>();
    private List<ChannelUpstreamHandler> decoders = new ArrayList<ChannelUpstreamHandler>();
    private ChannelHandler handler;
    private boolean ssl;
    private long sendBufferSize;
    private long receiveBufferSize;
    private int corePoolSize;
    private int maxPoolSize;
    private String keyStoreFormat;
    private String securityProvider;
    private boolean disconnect;

    public NettyConfiguration() {
        setKeepAlive(true);
        setTcpNoDelay(true);
        setBroadcast(false);
        setReuseAddress(true);
        setSync(false);
        setConnectTimeoutMillis(10000);
        setReceiveTimeoutMillis(10000);
        setSendBufferSize(65536);
        setReceiveBufferSize(65536);
        setSsl(false);
        setCorePoolSize(10);
        setMaxPoolSize(100);
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

        List<ChannelDownstreamHandler> referencedEncoders = component.resolveAndRemoveReferenceListParameter(parameters, "encoders", ChannelDownstreamHandler.class, null);
        addToHandlersList(encoders, referencedEncoders, ChannelDownstreamHandler.class);
        List<ChannelUpstreamHandler> referencedDecoders = component.resolveAndRemoveReferenceListParameter(parameters, "decoders", ChannelUpstreamHandler.class, null);
        addToHandlersList(decoders, referencedDecoders, ChannelUpstreamHandler.class);

        if (encoders.isEmpty() && decoders.isEmpty()) {
            encoders.add(component.resolveAndRemoveReferenceParameter(parameters, "encoder", ChannelDownstreamHandler.class, new ObjectEncoder()));
            decoders.add(component.resolveAndRemoveReferenceParameter(parameters, "decoder", ChannelUpstreamHandler.class, new ObjectDecoder()));
        }

        handler = component.resolveAndRemoveReferenceParameter(parameters, "handler", SimpleChannelHandler.class, null);

        Map<String, Object> settings = URISupport.parseParameters(uri);
        if (settings.containsKey("keepAlive")) {
            setKeepAlive(Boolean.valueOf((String) settings.get("keepAlive")));
        }
        if (settings.containsKey("tcpNoDelay")) {
            setTcpNoDelay(Boolean.valueOf((String) settings.get("tcpNoDelay")));
        }
        if (settings.containsKey("broadcast")) {
            setBroadcast(Boolean.valueOf((String) settings.get("broadcast")));
        }
        if (settings.containsKey("reuseAddress")) {
            setReuseAddress(Boolean.valueOf((String) settings.get("reuseAddress")));
        }
        if (settings.containsKey("connectTimeoutMillis")) {
            setConnectTimeoutMillis(Long.valueOf((String) settings.get("connectTimeoutMillis")));
        }
        if (settings.containsKey("sync")) {
            setTcpNoDelay(Boolean.valueOf((String) settings.get("sync")));
        }
        if (settings.containsKey("receiveTimeoutMillis")) {
            setReceiveTimeoutMillis(Long.valueOf((String) settings.get("receiveTimeoutMillis")));
        }
        if (settings.containsKey("sendBufferSize")) {
            setSendBufferSize(Long.valueOf((String) settings.get("sendBufferSize")));
        }
        if (settings.containsKey("receiveBufferSize")) {
            setReceiveBufferSize(Long.valueOf((String) settings.get("receiveBufferSize")));
        }
        if (settings.containsKey("ssl")) {
            setTcpNoDelay(Boolean.valueOf((String) settings.get("ssl")));
        }
        if (settings.containsKey("corePoolSize")) {
            setCorePoolSize(Integer.valueOf((String) settings.get("corePoolSize")));
        }
        if (settings.containsKey("maxPoolSize")) {
            setMaxPoolSize(Integer.valueOf((String) settings.get("maxPoolSize")));
        }
        if (settings.containsKey("disconnect")) {
            setDisconnect(Boolean.valueOf((String) settings.get("disconnect")));
        }
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

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
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

    public ChannelHandler getHandler() {
        return handler;
    }

    public void setHandler(ChannelHandler handler) {
        this.handler = handler;
    }

    public long getReceiveTimeoutMillis() {
        return receiveTimeoutMillis;
    }

    public void setReceiveTimeoutMillis(long receiveTimeoutMillis) {
        this.receiveTimeoutMillis = receiveTimeoutMillis;
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

}
