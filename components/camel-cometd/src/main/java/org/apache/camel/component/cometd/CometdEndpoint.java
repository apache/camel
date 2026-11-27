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
package org.apache.camel.component.cometd;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Publish messages to web clients and receive their messages with the CometD/Bayeux protocol, through an embedded Jetty
 * server.
 */
@UriEndpoint(firstVersion = "2.0.0", scheme = "cometd,cometds", title = "CometD", syntax = "cometd:host:port/channelName",
             category = { Category.NETWORKING, Category.MESSAGING }, headersClass = CometdBinding.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.TEXT, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.NULL },
                                   description = "The data of the Bayeux message, parsed from JSON: a String, a number or a"
                                                 + " boolean, a Map for an object, a List for an array, or null. From a cometd"
                                                 + " producer in the same JVM the body that producer sent, as is (a stream or"
                                                 + " a file as its text)"),
           consumerReply = @BodyType(value = {
                   Carrier.TEXT, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.STREAM,
                   Carrier.FILE, Carrier.NULL },
                                     description = "Only when the exchange is InOut (exchangePattern=InOut, or set in the"
                                                   + " route): the body the route ends with is sent back to the client that"
                                                   + " published, as JSON. A stream or a file is read as text, and null is"
                                                   + " sent as a JSON null. Bytes become an array of numbers; another object"
                                                   + " is sent as the text of its toString"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.STREAM,
                   Carrier.FILE, Carrier.NULL },
                                  description = "The data published to the channel, sent as JSON to the subscribed clients:"
                                                + " text, a number or a boolean, a Map or a List. A stream or a file is read"
                                                + " as text, and null is published as a JSON null. Bytes become an array of"
                                                + " numbers; another object is sent as the text of its toString: convert it"
                                                + " first. The headers go in the ext field of the message"),
           producerOut = @BodyType(Carrier.UNCHANGED))
public class CometdEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private CometdComponent component;

    private URI uri;
    @UriPath(description = "The host name or IP address that the embedded Jetty server listens on. Use 0.0.0.0 to listen on"
                           + " all network interfaces; localhost accepts only local connections.")
    @Metadata(required = true)
    private String host;
    @UriPath(description = "The TCP port that the embedded Jetty server listens on. The producers and consumers with the same"
                           + " scheme, host and port share one server.")
    @Metadata(required = true)
    private int port;
    @UriPath(description = "The name of the Bayeux channel, which is the path of the URI, such as /channel/orders. The"
                           + " consumer receives the messages that clients publish to this channel, and the producer"
                           + " publishes to the clients that subscribe to it.")
    @Metadata(required = true)
    private String channelName;
    @UriParam(description = "The directory of static web resources that the server serves from the root path, with the"
                            + " prefix file: for the file system or classpath: for the classpath, such as"
                            + " classpath:webapp. Without a value the server serves no static resources. The first endpoint"
                            + " that starts the server of the host and port sets it.")
    private String baseResource;
    @UriParam(defaultValue = "240000",
              description = "The time in milliseconds that the server holds a /meta/connect request (the long poll of a"
                            + " client) while it has no message to deliver, before it replies. The first endpoint that"
                            + " starts the server of the host and port sets it.")
    private int timeout = 240000;
    @UriParam(description = "The time in milliseconds that the server tells the clients to wait after a /meta/connect reply"
                            + " before they send the next /meta/connect request; 0 means at once. The first endpoint that"
                            + " starts the server of the host and port sets it.")
    private int interval;
    @UriParam(defaultValue = "30000",
              description = "The time in milliseconds that the server waits for the next /meta/connect request of a client"
                            + " before it removes the session of that client. The first endpoint that starts the server of"
                            + " the host and port sets it.")
    private int maxInterval = 30000;
    @UriParam(defaultValue = "1500",
              description = "Passed to the CometD servlet as the multiFrameInterval parameter, which CometD 9 does not read,"
                            + " so it has no effect.")
    private int multiFrameInterval = 1500;
    @UriParam(defaultValue = "true",
              description = "Passed to the CometD servlet as the JSONCommented parameter, which CometD 9 does not read, so it"
                            + " has no effect.")
    private boolean jsonCommented = true;
    @UriParam(label = "consumer",
              description = "Whether the consumer adds the attributes of the CometD session of the publishing client as"
                            + " headers, one header per attribute name. Only String, Integer, Long, Double and Boolean"
                            + " values are added; the other attributes are skipped.")
    private boolean sessionHeadersEnabled;
    @UriParam(defaultValue = "1", enums = "0,1,2",
              description = "Passed to the CometD servlet as the logLevel parameter, which CometD 9 does not read, so it has"
                            + " no effect. Set the level of the org.cometd loggers in the logging configuration instead.")
    private int logLevel = 1;
    @UriParam(description = "Whether the server adds the Jetty CrossOriginFilter, which answers the CORS requests of the"
                            + " origins in allowedOrigins. The filter is mapped to filterPath, so set that option too. The"
                            + " first endpoint that starts the server of the host and port sets it.")
    private boolean crossOriginFilterOn;
    @UriParam(defaultValue = "*",
              description = "The comma-separated list of origins that the cross-origin filter allows, such as"
                            + " https://app.example.com. A * in an origin matches any characters, * alone allows every"
                            + " origin, and the filter allows credentials only when every origin is named without a *."
                            + " Used only when crossOriginFilterOn is true.")
    private String allowedOrigins;
    @UriParam(defaultValue = "websocket,long-polling")
    private String allowedTransports = "websocket,long-polling";
    @UriParam(description = "The servlet path pattern that the cross-origin filter is mapped to, such as /cometd/*. Used only"
                            + " when crossOriginFilterOn is true; without a value the filter applies to no request.")
    private String filterPath;
    @UriParam(label = "producer",
              description = "Whether the producer disconnects the local session of its CometD service after each message it"
                            + " publishes to the channel. CometD does not sweep local sessions, so they stay in the server"
                            + " until they are disconnected.")
    private boolean disconnectLocalSession;
    @UriParam(label = "advanced",
              description = "A custom HeaderFilterStrategy for the headers that are copied between the Camel message and the"
                            + " ext field of the Bayeux message, in both directions.")
    private HeaderFilterStrategy headerFilterStrategy;

    public CometdEndpoint(CometdComponent component, String uri, String remaining) {
        super(uri, component);
        this.component = component;
        try {
            this.uri = new URI(uri);
            this.host = this.uri.getHost();
            this.port = this.uri.getPort();
            this.channelName = remaining;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String getServiceUrl() {
        return getProtocol() + ":" + host + ":" + getPort();
    }

    @Override
    public String getServiceProtocol() {
        return getProtocol();
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(component, "component");
        return new CometdProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(component, "component");
        CometdConsumer consumer = new CometdConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public void connect(CometdProducerConsumer prodcons) throws Exception {
        component.connect(prodcons);
    }

    public void disconnect(CometdProducerConsumer prodcons) throws Exception {
        component.disconnect(prodcons);
    }

    @Override
    public CometdComponent getComponent() {
        return component;
    }

    public String getPath() {
        return uri.getPath();
    }

    public int getPort() {
        if (uri.getPort() == -1) {
            if ("cometds".equals(getProtocol())) {
                return 443;
            } else {
                return 80;
            }
        }
        return uri.getPort();
    }

    public String getProtocol() {
        return uri.getScheme();
    }

    public URI getUri() {
        return uri;
    }

    public String getBaseResource() {
        return baseResource;
    }

    public void setBaseResource(String baseResource) {
        this.baseResource = baseResource;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getMaxInterval() {
        return maxInterval;
    }

    public void setMaxInterval(int maxInterval) {
        this.maxInterval = maxInterval;
    }

    public int getMultiFrameInterval() {
        return multiFrameInterval;
    }

    public void setMultiFrameInterval(int multiFrameInterval) {
        this.multiFrameInterval = multiFrameInterval;
    }

    public boolean isJsonCommented() {
        return jsonCommented;
    }

    public void setJsonCommented(boolean commented) {
        jsonCommented = commented;
    }

    public void setSessionHeadersEnabled(boolean enable) {
        this.sessionHeadersEnabled = enable;
    }

    public boolean isSessionHeadersEnabled() {
        return sessionHeadersEnabled;
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public boolean isCrossOriginFilterOn() {
        return crossOriginFilterOn;
    }

    public void setCrossOriginFilterOn(boolean crossOriginFilterOn) {
        this.crossOriginFilterOn = crossOriginFilterOn;
    }

    public String getFilterPath() {
        return filterPath;
    }

    public String getAllowedTransports() {
        return allowedTransports;
    }

    /**
     * A comma-separated list of transport names allowed by the CometD server (e.g. websocket, long-polling,
     * callback-polling). callback-polling is the JSONP transport mechanism. This option applies to the shared CometD
     * server on the host and port; the first endpoint to configure the server sets this value.
     */
    public void setAllowedTransports(String allowedTransports) {
        this.allowedTransports = allowedTransports;
    }

    public void setFilterPath(String filterPath) {
        this.filterPath = filterPath;
    }

    public boolean isDisconnectLocalSession() {
        return disconnectLocalSession;
    }

    public void setDisconnectLocalSession(boolean disconnectLocalSession) {
        this.disconnectLocalSession = disconnectLocalSession;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }
}
