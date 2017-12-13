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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLContext;

import io.undertow.server.HttpServerExchange;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.undertow.UndertowConstants.EventType;
import org.apache.camel.component.undertow.handlers.CamelWebSocketHandler;
import org.apache.camel.http.common.cookie.CookieHandler;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * The undertow component provides HTTP-based endpoints for consuming and producing HTTP requests.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "undertow", title = "Undertow", syntax = "undertow:httpURI",
        consumerClass = UndertowConsumer.class, label = "http", lenientProperties = true)
public class UndertowEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(UndertowEndpoint.class);
    private UndertowComponent component;
    private SSLContext sslContext;
    private OptionMap optionMap;
    private HttpHandlerRegistrationInfo registrationInfo;
    private CamelWebSocketHandler webSocketHttpHandler;
    private boolean isWebSocket;

    @UriPath @Metadata(required = "true")
    private URI httpURI;
    @UriParam(label = "advanced")
    private UndertowHttpBinding undertowHttpBinding;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy = new UndertowHeaderFilterStrategy();
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "consumer")
    private String httpMethodRestrict;
    @UriParam(label = "consumer", defaultValue = "false")
    private Boolean matchOnUriPrefix = Boolean.FALSE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean throwExceptionOnFailure = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "false")
    private Boolean transferException = Boolean.FALSE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean keepAlive = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean tcpNoDelay = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean reuseAddresses = Boolean.TRUE;
    @UriParam(label = "producer", prefix = "option.", multiValue = true)
    private Map<String, Object> options;
    @UriParam(label = "consumer",
            description = "Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is turned off.")
    private boolean optionsEnabled;
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;
    @UriParam(label = "producer,websocket")
    private Boolean sendToAll;
    @UriParam(label = "producer,websocket", defaultValue = "30000")
    private Integer sendTimeout = 30000;
    @UriParam(label = "consumer,websocket", defaultValue = "false")
    private boolean useStreaming;
    @UriParam(label = "consumer,websocket", defaultValue = "false")
    private boolean fireWebSocketChannelEvents;

    public UndertowEndpoint(String uri, UndertowComponent component) throws URISyntaxException {
        super(uri, component);
        this.component = component;
    }

    @Override
    public UndertowComponent getComponent() {
        return component;
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
        //throw exception as polling consumer is not supported
        throw new UnsupportedOperationException("This component does not support polling consumer");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the UndertowProducer
        return true;
    }

    public Exchange createExchange(HttpServerExchange httpExchange) throws Exception {
        Exchange exchange = createExchange(ExchangePattern.InOut);

        Message in = getUndertowHttpBinding().toCamelMessage(httpExchange, exchange);

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

    /**
     * Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.
     */
    public void setMatchOnUriPrefix(Boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
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

    public UndertowHttpBinding getUndertowHttpBinding() {
        if (undertowHttpBinding == null) {
            // create a new binding and use the options from this endpoint
            undertowHttpBinding = new DefaultUndertowHttpBinding();
            undertowHttpBinding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            undertowHttpBinding.setTransferException(getTransferException());
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
     * if {@code true}, text and binary messages coming through a WebSocket will be wrapped as java.io.Reader and
     * java.io.InputStream respectively before they are passed to an {@link Exchange}; otherwise they will be passed as
     * String and byte[] respectively.
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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

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

}
