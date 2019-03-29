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
package org.apache.camel.component.atmosphere.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.servlet.ServletConsumer;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereFrameworkInitializer;
import org.atmosphere.cpr.AtmosphereRequestImpl;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.websocket.WebSocketProtocol;

/**
 *
 */
public class WebsocketConsumer extends ServletConsumer {
    private boolean enableEventsResending;
    private Map<String, String> queryMap = new HashMap<>();
    private AtmosphereFramework framework;
    private final AtmosphereFrameworkInitializer initializer;


    public WebsocketConsumer(WebsocketEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        initializer = new AtmosphereFrameworkInitializer(false, true);
    }
    
    public void configureEventsResending(final boolean enableEventsResending) {
        this.enableEventsResending = enableEventsResending;
    }

    public void configureFramework(ServletConfig config) throws ServletException {
        initializer.configureFramework(config, false, false, AtmosphereFramework.class);
        this.framework = initializer.framework();
        this.framework.setUseNativeImplementation(false);
        this.framework.addInitParameter(ApplicationConfig.ANALYTICS, "false");
        this.framework.addInitParameter(ApplicationConfig.WEBSOCKET_SUPPORT, "true");
        this.framework.addInitParameter(ApplicationConfig.WEBSOCKET_PROTOCOL,
                                        getEndpoint().isUseStreaming() ? WebsocketStreamHandler.class.getName() : WebsocketHandler.class.getName());
        this.framework.init(config);

        WebSocketProtocol wsp = this.framework.getWebSocketProtocol();
        if (wsp instanceof WebsocketHandler) {
            ((WebsocketHandler)wsp).setConsumer(this);
        } else {
            throw new IllegalArgumentException("Unexpected WebSocketHandler: " + wsp);
        }
    }

    @Override
    public WebsocketEndpoint getEndpoint() {
        return (WebsocketEndpoint)super.getEndpoint();
    }

    void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        this.queryMap = getQueryMap(request.getQueryString());
        framework.doCometSupport(AtmosphereRequestImpl.wrap(request), AtmosphereResponseImpl.wrap(response));
    }

    public void sendMessage(final String connectionKey, Object message) {
        final Exchange exchange = getEndpoint().createExchange();

        // set header and body
        exchange.getIn().setHeader(WebsocketConstants.CONNECTION_KEY, connectionKey);
        exchange.getIn().setBody(message);

        // send exchange using the async routing engine
        getAsyncProcessor().process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }
            }
        });
    }

    public void sendEventNotification(String connectionKey, int eventType) {
        final Exchange exchange = getEndpoint().createExchange();

        // set header
        exchange.getIn().setHeader(WebsocketConstants.CONNECTION_KEY, connectionKey);
        exchange.getIn().setHeader(WebsocketConstants.EVENT_TYPE, eventType);

        for (Map.Entry<String, String> param : queryMap.entrySet()) {
            exchange.getIn().setHeader(param.getKey(), param.getValue());
        }

        // send exchange using the async routing engine
        getAsyncProcessor().process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }
            }
        });
    }

    public void sendNotDeliveredMessage(List<String> failedConnectionKeys, Object message) {
        final Exchange exchange = getEndpoint().createExchange();

        // set header and body
        exchange.getIn().setHeader(WebsocketConstants.CONNECTION_KEY_LIST, failedConnectionKeys);
        exchange.getIn().setHeader(WebsocketConstants.ERROR_TYPE, WebsocketConstants.MESSAGE_NOT_SENT_ERROR_TYPE);
        exchange.getIn().setBody(message);

        // send exchange using the async routing engine
        getAsyncProcessor().process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }
            }
        });
    }

    public boolean isEnableEventsResending() {
        return enableEventsResending;
    }

    private Map<String, String> getQueryMap(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                String[] nameval = param.split("=");
                map.put(nameval[0], nameval[1]);
            }
        }
        return map;
    }
}
