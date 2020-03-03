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
package org.apache.camel.component.stomp;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.stomp.client.Callback;
import org.fusesource.stomp.client.CallbackConnection;
import org.fusesource.stomp.client.Promise;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;

import static org.fusesource.hawtbuf.UTF8Buffer.utf8;
import static org.fusesource.stomp.client.Constants.DESTINATION;
import static org.fusesource.stomp.client.Constants.DISCONNECT;
import static org.fusesource.stomp.client.Constants.ID;
import static org.fusesource.stomp.client.Constants.SEND;
import static org.fusesource.stomp.client.Constants.SUBSCRIBE;
import static org.fusesource.stomp.client.Constants.UNSUBSCRIBE;

/**
 * The stomp component is used for communicating with Stomp compliant message brokers.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "stomp", title = "Stomp", syntax = "stomp:destination", label = "messaging")
public class StompEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware {

    private CallbackConnection connection;
    private Stomp stomp;
    private final List<StompConsumer> consumers = new CopyOnWriteArrayList<>();

    @UriPath(description = "Name of the queue") @Metadata(required = true)
    private String destination;
    @UriParam
    private StompConfiguration configuration;
    @UriParam(label = "advanced", description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;

    public StompEndpoint(String uri, StompComponent component, StompConfiguration configuration, String destination) {
        super(uri, component);
        this.configuration = configuration;
        this.destination = destination;
    }

    public StompConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new StompProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        StompConsumer consumer = new StompConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        final Promise<CallbackConnection> promise = new Promise<>();

        stomp = new Stomp(configuration.getBrokerURL());
        stomp.setLogin(configuration.getLogin());
        stomp.setPasscode(configuration.getPasscode());
        if (configuration.getSslContextParameters() != null) {
            stomp.setSslContext(configuration.getSslContextParameters().createSSLContext(getCamelContext()));
        }
        if (configuration.getVersion() != null && !configuration.getVersion().isEmpty()) {
            stomp.setVersion(configuration.getVersion());
        }
        if (configuration.getCustomHeaders() != null && !configuration.getCustomHeaders().isEmpty()) {
            stomp.setCustomHeaders(configuration.getCustomHeaders());
        }
        stomp.connectCallback(promise);
        if (configuration.getHost() != null && !configuration.getHost().isEmpty()) {
            stomp.setHost(configuration.getHost());
        }

        connection = promise.await();

        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                connection.receive(new Callback<StompFrame>() {
                    @Override
                    public void onFailure(Throwable value) {
                        if (isStarted()) {
                            connection.close(null);
                        }
                    }

                    @Override
                    public void onSuccess(StompFrame value) {
                        if (!consumers.isEmpty()) {
                            Exchange exchange = createExchange();
                            exchange.getIn().setBody(value.content());
                            exchange.getIn().setHeaders(value.headerMap().entrySet().stream()
                                    .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue)));
                            for (StompConsumer consumer : consumers) {
                                consumer.processExchange(exchange);
                            }
                        }
                    }
                });
                connection.resume();
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                StompFrame frame = new StompFrame(DISCONNECT);
                connection.send(frame, null);
            }
        });
        connection.close(null);
    }

    protected void send(final Exchange exchange, final AsyncCallback callback) {
        final StompFrame frame = new StompFrame(SEND);
        populateCamelMessageHeadersToStompFrames(exchange, frame);
        frame.addHeader(DESTINATION, StompFrame.encodeHeader(destination));
        //Fix for CAMEL-9506 leveraging the camel converter to do the change
        frame.content(utf8(exchange.getIn().getBody(String.class)));

        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                connection.send(frame, new Callback<Void>() {
                    @Override
                    public void onFailure(Throwable e) {
                        exchange.setException(e);
                        callback.done(false);
                    }

                    @Override
                    public void onSuccess(Void v) {
                        callback.done(false);
                    }
                });
            }
        });
    }

    private void populateCamelMessageHeadersToStompFrames(final Exchange exchange, final StompFrame frame) {
        Set<Map.Entry<String, Object>> entries = exchange.getIn().getHeaders().entrySet();        
        for (Map.Entry<String, Object> entry : entries) {
            String headerName = entry.getKey();
            Object headerValue = entry.getValue();
            if (!headerName.toLowerCase().startsWith("camel") 
                && !headerFilterStrategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {
                if (headerValue != null) {
                    frame.addHeader(new AsciiBuffer(headerName), StompFrame.encodeHeader(headerValue.toString()));
                }
            }
        }
    }

    private void populateCustomHeadersToStompFrames(final StompFrame frame) {
        Properties customHeaders = configuration.getCustomHeaders();
        if (customHeaders != null) {
            for (Object key : customHeaders.keySet()) {
                frame.addHeader(StompFrame.encodeHeader(key.toString()), StompFrame.encodeHeader(customHeaders.get(key).toString()));
            }
        }
    }

    void addConsumer(final StompConsumer consumer) {
        final StompFrame frame = new StompFrame(SUBSCRIBE);
        populateCustomHeadersToStompFrames(frame);
        frame.addHeader(DESTINATION, StompFrame.encodeHeader(destination));
        frame.addHeader(ID, consumer.id);
        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                connection.send(frame, null);
            }
        });
        consumers.add(consumer);
    }

    void removeConsumer(final StompConsumer consumer) {
        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                StompFrame frame = new StompFrame(UNSUBSCRIBE);
                frame.addHeader(DESTINATION, StompFrame.encodeHeader(destination));
                frame.addHeader(ID, consumer.id);
                connection.send(frame, null);
            }
        });
        consumers.remove(consumer);
    }

    AsciiBuffer getNextId() {
        return connection.nextId();
    }
    
    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new DefaultHeaderFilterStrategy();
        }
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }
}
