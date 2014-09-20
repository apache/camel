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
package org.apache.camel.component.atmosphere.websocket;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.servlet.ServletConsumer;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.websocket.WebSocketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class WebsocketConsumer extends ServletConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(WebsocketConsumer.class);
    
    private AtmosphereFramework framework;
    
    public WebsocketConsumer(WebsocketEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.framework = new AtmosphereFramework(false, true);

        framework.setUseNativeImplementation(false);
        framework.addInitParameter(ApplicationConfig.WEBSOCKET_SUPPORT, "true");
        framework.addInitParameter(ApplicationConfig.WEBSOCKET_PROTOCOL, 
            endpoint.isUseStreaming() ? WebsocketStreamHandler.class.getName() : WebsocketHandler.class.getName());
        framework.init();
        
        WebSocketProtocol wsp = framework.getWebSocketProtocol();
        if (wsp instanceof WebsocketHandler) {
            ((WebsocketHandler)wsp).setConsumer(this);            
        } else {
            // this should not normally happen
            LOG.error("unexpected WebSocketHandler: {}", wsp);
        }
    }

    @Override
    public WebsocketEndpoint getEndpoint() {
        return (WebsocketEndpoint)super.getEndpoint();
    }
    
    void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        framework.doCometSupport(AtmosphereRequest.wrap(request), AtmosphereResponse.wrap(response));
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
}
