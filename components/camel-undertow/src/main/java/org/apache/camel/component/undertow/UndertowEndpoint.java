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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

import javax.net.ssl.SSLContext;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.accesslog.AccessLogReceiver;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.cloud.DiscoverableService;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.undertow.UndertowConstants.EventType;
import org.apache.camel.component.undertow.handlers.CamelWebSocketHandler;
import org.apache.camel.component.undertow.spi.UndertowSecurityProvider;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.CollectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * The undertow component provides HTTP and WebSocket based endpoints for consuming and producing HTTP/WebSocket requests.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "undertow", title = "Undertow", syntax = "undertow:httpURI",
        label = "http,websocket", lenientProperties = true)
public class UndertowEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware, DiscoverableService {

    private static final Logger LOG = LoggerFactory.getLogger(UndertowEndpoint.class);

    private UndertowComponent component;
    private SSLContext sslContext;
    private OptionMap optionMap;
    private HttpHandlerRegistrationInfo registrationInfo;
    private CamelWebSocketHandler webSocketHttpHandler;
    private boolean isWebSocket;

    @UriPath @Metadata(required = true)
    private URI httpURI;
    @UriParam(label = "common", defaultValue = "false")
    private boolean useStreaming;
    @UriParam(label = "advanced")
    private UndertowHttpBinding undertowHttpBinding;
    @UriParam(label = "advanced")
    private AccessLogReceiver accessLogReceiver;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy = new UndertowHeaderFilterStrategy();
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "consumer")
    private String httpMethodRestrict;
    @UriParam(label = "consumer", defaultValue = "false")
    private Boolean matchOnUriPrefix = Boolean.FALSE;
    @UriParam(label = "consumer", defaultValue = "false")
    private Boolean accessLog = Boolean.FALSE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean throwExceptionOnFailure = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "false")
    private Boolean transferException = Boolean.FALSE;
    @UriParam(label = "consumer", defaultValue = "false")
    private Boolean muteException = Boolean.FALSE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean keepAlive = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean tcpNoDelay = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean reuseAddresses = Boolean.TRUE;
    @UriParam(label = "producer", prefix = "option.", multiValue = true)
    private Map<String, Object> options;
    @UriParam(label = "consumer")
    private boolean optionsEnabled;
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;
    @UriParam(label = "producer,websocket")
    private Boolean sendToAll;
    @UriParam(label = "producer,websocket", defaultValue = "30000")
    private Integer sendTimeout = 30000;
    @UriParam(label = "consumer,websocket", defaultValue = "false")
    private boolean fireWebSocketChannelEvents;
    @UriParam(label = "consumer,advanced",
        description = "Specifies a comma-delimited set of io.undertow.server.HttpHandler instances to lookup in"
        + " your Registry. These handlers are added to the Undertow handler chain (for example, to add security)."
        + " Important: You can not use different handlers with different Undertow endpoints using the same port number."
        + " The handlers is associated to the port number. If you need different handlers, then use different port numbers.")
    private String handlers;
    @UriParam(
            label = "producer", defaultValue = "true",
            description = "If the option is true, UndertowProducer will set the Host header to the value contained in the current exchange Host header,"
            + " useful in reverse proxy applications where you want the Host header received by the downstream server to reflect the URL called by the upstream client,"
            + " this allows applications which use the Host header to generate accurate URL's for a proxied service."
    )
    private boolean preserveHostHeader = true;
    @UriParam(label = "security", description = "OConfiguration used by UndertowSecurityProvider. Security configuration object for use "
            + "from UndertowSecurityProvider. Configuration is UndertowSecurityProvider specific. Each provider decides whether accepts configuration.")
    private Object securityConfiguration;
    @UriParam(label = "security", description = "Configuration used by UndertowSecurityProvider. Comma separated list of allowed roles.")
    private String allowedRoles;
    @UriParam(label = "security", description = "Security provider allows plug in the provider, which will be used to secure requests. "
            + "SPI approach could be used too (endpoint then finds security provider using SPI).")
    private UndertowSecurityProvider securityProvider;

    public UndertowEndpoint(String uri, UndertowComponent component) {
        super(uri, component);
        this.component = component;
    }

    @Override
    public UndertowComponent getComponent() {
        return component;
    }

    public UndertowSecurityProvider getSecurityProvider() {
        return this.securityProvider;
    }

    public void setSecurityProvider(UndertowSecurityProvider securityProvider) {
        this.securityProvider = securityProvider;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new UndertowProducer(this, optionMap);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new UndertowConsumer(this, processor);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        throw new UnsupportedOperationException("This component does not support polling consumer");
    }

    @Override
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the UndertowProducer
        return true;
    }

    // Service Registration
    //-------------------------------------------------------------------------

    @Override
    public Map<String, String> getServiceProperties() {
        return CollectionHelper.immutableMapOf(
            ServiceDefinition.SERVICE_META_PORT, Integer.toString(httpURI.getPort()),
            ServiceDefinition.SERVICE_META_PATH, httpURI.getPath(),
            ServiceDefinition.SERVICE_META_PROTOCOL, httpURI.getScheme()
        );
    }

    public Exchange createExchange(HttpServerExchange httpExchange) throws Exception {
        Exchange exchange = createExchange(ExchangePattern.InOut);

        Message in = getUndertowHttpBinding().toCamelMessage(httpExchange, exchange);

        //securityProvider could add its own header into result exchange
        if (getSecurityProvider() != null) {
            getSecurityProvider().addHeader((key, value) -> in.setHeader(key, value), httpExchange);
        }

        exchange.setProperty(Exchange.CHARSET_NAME, httpExchange.getRequestCharset());
        in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, httpExchange.getRequestCharset());

        exchange.setIn(in);
        return exchange;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public URI getHttpURI() {
        return httpURI;
    }

    /**
     * The url of the HTTP endpoint to use.
     */
    public void setHttpURI(URI httpURI) {
        this.httpURI = UndertowHelper.makeHttpURI(httpURI);
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    /**
     * Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc. Multiple methods can be specified separated by comma.
     */
    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public Boolean getMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix != null && matchOnUriPrefix;
    }

    /**
     * Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.
     */
    public void setMatchOnUriPrefix(Boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Boolean getThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setThrowExceptionOnFailure(Boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public Boolean getTransferException() {
        return transferException;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side and if the caused Exception 
     * was send back serialized in the response as a application/x-java-serialized-object content type. 
     * On the producer side the exception will be deserialized and thrown as is instead of the HttpOperationFailedException. The caused exception is required to be serialized. 
     * This is by default turned off. If you enable this 
     * then be aware that Java will deserialize the incoming data from the request to Java and that can be a potential security risk.
     * 
     */
    public void setTransferException(Boolean transferException) {
        this.transferException = transferException;
    }

    public Boolean getMuteException() {
        return muteException;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side the response's body won't contain the exception's stack trace.
     */
    public void setMuteException(Boolean muteException) {
        this.muteException = muteException;
    }

    public UndertowHttpBinding getUndertowHttpBinding() {
        if (undertowHttpBinding == null) {
            // create a new binding and use the options from this endpoint
            undertowHttpBinding = new DefaultUndertowHttpBinding(useStreaming);
            undertowHttpBinding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            undertowHttpBinding.setTransferException(getTransferException());
            undertowHttpBinding.setMuteException(getMuteException());
        }
        return undertowHttpBinding;
    }

    /**
     * To use a custom UndertowHttpBinding to control the mapping between Camel message and undertow.
     */
    public void setUndertowHttpBinding(UndertowHttpBinding undertowHttpBinding) {
        this.undertowHttpBinding = undertowHttpBinding;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    /**
     * Setting to ensure socket is not closed due to inactivity
     */
    public void setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public Boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Setting to improve TCP protocol performance
     */
    public void setTcpNoDelay(Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public Boolean getReuseAddresses() {
        return reuseAddresses;
    }

    /**
     * Setting to facilitate socket multiplexing
     */
    public void setReuseAddresses(Boolean reuseAddresses) {
        this.reuseAddresses = reuseAddresses;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    /**
     * Sets additional channel options. The options that can be used are defined in {@link org.xnio.Options}.
     * To configure from endpoint uri, then prefix each option with <tt>option.</tt>, such as <tt>option.close-abort=true&option.send-buffer=8192</tt>
     */
    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public boolean isOptionsEnabled() {
        return optionsEnabled;
    }

    /**
     * Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is turned off.
     */
    public void setOptionsEnabled(boolean optionsEnabled) {
        this.optionsEnabled = optionsEnabled;
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    /**
     * Configure a cookie handler to maintain a HTTP session
     */
    public void setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
    }

    public Boolean getSendToAll() {
        return sendToAll;
    }

    /**
     * To send to all websocket subscribers. Can be used to configure on endpoint level, instead of having to use the
     * {@code UndertowConstants.SEND_TO_ALL} header on the message.
     */
    public void setSendToAll(Boolean sendToAll) {
        this.sendToAll = sendToAll;
    }

    public Integer getSendTimeout() {
        return sendTimeout;
    }

    /**
     * Timeout in milliseconds when sending to a websocket channel.
     * The default timeout is 30000 (30 seconds).
     */
    public void setSendTimeout(Integer sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public boolean isUseStreaming() {
        return useStreaming;
    }

    /**
     * <p>
     * For HTTP endpoint:
     * if {@code true}, text and binary messages will be wrapped as {@link java.io.InputStream}
     * before they are passed to an {@link Exchange}; otherwise they will be passed as byte[].
     * </p>
     *
     * <p>
     * For WebSocket endpoint:
     * if {@code true}, text and binary messages will be wrapped as {@link java.io.Reader} and
     * {@link java.io.InputStream} respectively before they are passed to an {@link Exchange};
     * otherwise they will be passed as String and byte[] respectively.
     * </p>
     */
    public void setUseStreaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
    }

    public boolean isFireWebSocketChannelEvents() {
        return fireWebSocketChannelEvents;
    }

    /**
     * if {@code true}, the consumer will post notifications to the route when a new WebSocket peer connects,
     * disconnects, etc. See {@code UndertowConstants.EVENT_TYPE} and {@link EventType}.
     */
    public void setFireWebSocketChannelEvents(boolean fireWebSocketChannelEvents) {
        this.fireWebSocketChannelEvents = fireWebSocketChannelEvents;
    }
    public void setPreserveHostHeader(boolean preserveHostHeader) {
        this.preserveHostHeader = preserveHostHeader;
    }
    public boolean isPreserveHostHeader() {
        return preserveHostHeader;
    }

    public Object getSecurityConfiguration() {
        return this.securityConfiguration;
    }

    public void setSecurityConfiguration(Object securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (this.securityProvider == null) {
            initSecurityProvider();
        }

        final String scheme = httpURI.getScheme();
        this.isWebSocket = UndertowConstants.WS_PROTOCOL.equalsIgnoreCase(scheme) || UndertowConstants.WSS_PROTOCOL.equalsIgnoreCase(scheme);

        if (sslContextParameters != null) {
            sslContext = sslContextParameters.createSSLContext(getCamelContext());
        }

        // create options map
        if (options != null && !options.isEmpty()) {

            // favor to use the classloader that loaded the user application
            ClassLoader cl = getComponent().getCamelContext().getApplicationContextClassLoader();
            if (cl == null) {
                cl = Options.class.getClassLoader();
            }

            OptionMap.Builder builder = OptionMap.builder();
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key != null && value != null) {
                    // upper case and dash as underscore
                    key = key.toUpperCase(Locale.ENGLISH).replace('-', '_');
                    // must be field name
                    key = Options.class.getName() + "." + key;
                    Option option = Option.fromString(key, cl);
                    value = option.parseValue(value.toString(), cl);
                    LOG.trace("Parsed option {}={}", option.getName(), value);
                    builder.set(option, value);
                }
            }
            optionMap = builder.getMap();
        } else {
            // use an empty map
            optionMap = OptionMap.EMPTY;
        }

        // and then configure these default options if they have not been explicit configured
        if (keepAlive != null && !optionMap.contains(Options.KEEP_ALIVE)) {
            // rebuild map
            OptionMap.Builder builder = OptionMap.builder();
            builder.addAll(optionMap).set(Options.KEEP_ALIVE, keepAlive);
            optionMap = builder.getMap();
        }
        if (tcpNoDelay != null && !optionMap.contains(Options.TCP_NODELAY)) {
            // rebuild map
            OptionMap.Builder builder = OptionMap.builder();
            builder.addAll(optionMap).set(Options.TCP_NODELAY, tcpNoDelay);
            optionMap = builder.getMap();
        }
        if (reuseAddresses != null && !optionMap.contains(Options.REUSE_ADDRESSES)) {
            // rebuild map
            OptionMap.Builder builder = OptionMap.builder();
            builder.addAll(optionMap).set(Options.REUSE_ADDRESSES, reuseAddresses);
            optionMap = builder.getMap();
        }
    }

    private void initSecurityProvider() throws Exception {
        Object securityConfiguration = getSecurityConfiguration();
        if (securityConfiguration != null) {
            ServiceLoader<UndertowSecurityProvider> securityProvider = ServiceLoader.load(UndertowSecurityProvider.class);

            Iterator<UndertowSecurityProvider> iter = securityProvider.iterator();
            List<String> providers = new LinkedList();
            while (iter.hasNext()) {
                UndertowSecurityProvider security =  iter.next();
                //only securityProvider, who accepts security configuration, could be used
                if (security.acceptConfiguration(securityConfiguration, getEndpointUri())) {
                    this.securityProvider = security;
                    LOG.info("Security provider found {}", securityProvider.getClass().getName());
                    break;
                }
                providers.add(security.getClass().getName());
            }
            if (this.securityProvider == null) {
                LOG.info("Security provider for configuration {} not found {}", securityConfiguration, providers);
            }
        }
        if (this.securityProvider == null) {
            this.securityProvider = getComponent().getSecurityProvider();
        }
    }

    /**
     * @return {@code true} if {@link #getHttpURI()}'s scheme is {@code ws} or {@code wss}
     */
    public boolean isWebSocket() {
        return isWebSocket;
    }

    public HttpHandlerRegistrationInfo getHttpHandlerRegistrationInfo() {
        if (registrationInfo == null) {
            registrationInfo = new HttpHandlerRegistrationInfo(getHttpURI(), getHttpMethodRestrict(), getMatchOnUriPrefix());
        }
        return registrationInfo;
    }

    public CamelWebSocketHandler getWebSocketHttpHandler() {
        if (webSocketHttpHandler == null) {
            webSocketHttpHandler = new CamelWebSocketHandler();
        }
        return webSocketHttpHandler;
    }

    public Boolean getAccessLog() {
        return accessLog;
    }

    /**
     * Whether or not the consumer should write access log
     */
    public void setAccessLog(Boolean accessLog) {
        this.accessLog = accessLog;
    }

    public AccessLogReceiver getAccessLogReceiver() {
        return accessLogReceiver;
    }

    /**
     * Which Undertow AccessLogReciever should be used
     * Will use JBossLoggingAccessLogReceiver if not specifid
     */
    public void setAccessLogReceiver(AccessLogReceiver accessLogReceiver) {
        this.accessLogReceiver = accessLogReceiver;
    }

    public String getHandlers() {
        return handlers;
    }

    /**
     * Specifies a comma-delimited set of io.undertow.server.HttpHandler instances in your Registry (such as your Spring ApplicationContext).
     * These handlers are added to the Undertow handler chain (for example, to add security).
     * Important: You can not use different handlers with different Undertow endpoints using the same port number.
     * The handlers is associated to the port number. If you need different handlers, then use different port numbers.
     */
    public void setHandlers(String handlers) {
        this.handlers = handlers;
    }

}
