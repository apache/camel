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
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.http.common.HttpConsumer;

/**
 * This servlet is used to add some websocket specific handling at the moment.
 * 
 * REVISIT
 * we might be able to get rid of this servlet by overriding some of the binding
 * code that is executed between the servlet and the consumer.
 */
public class CamelWebSocketServlet extends CamelHttpTransportServlet {
    private static final long serialVersionUID = 1764707448550670635L;
    private static final String RESEND_ALL_WEBSOCKET_EVENTS_PARAM_KEY = "events";
    private boolean enableEventsResending;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        initParameters(config);

        enrichConsumers(config);
    }

    @Override
    protected void doService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.trace("Service: {}", request);

        // Is there a consumer registered for the request.
        HttpConsumer consumer = getServletResolveConsumerStrategy().resolve(request, getConsumers());
        if (consumer == null) {
            log.debug("No consumer to service request {}", request);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }       
        
        // are we suspended?
        if (consumer.isSuspended()) {
            log.debug("Consumer suspended, cannot service request {}", request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        
        if (consumer.getEndpoint().getHttpMethodRestrict() != null 
            && !consumer.getEndpoint().getHttpMethodRestrict().equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if ("TRACE".equals(request.getMethod()) && !consumer.isTraceEnabled()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (!(consumer instanceof WebsocketConsumer)) {
            log.debug("Consumer not supporting websocket {}", request);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        
        log.debug("Dispatching to Websocket Consumer at {}", consumer.getPath());
        ((WebsocketConsumer)consumer).service(request, response);
    }

    private void initParameters(ServletConfig config) {
        String eventsResendingParameter = config.getInitParameter(RESEND_ALL_WEBSOCKET_EVENTS_PARAM_KEY);
        if ("true".equals(eventsResendingParameter)) {
            log.debug("Events resending enabled");
            enableEventsResending = true;
        }
    }

    private void enrichConsumers(ServletConfig config) throws ServletException {
        for (Map.Entry<String, HttpConsumer> httpConsumerEntry : getConsumers().entrySet()) {
            WebsocketConsumer consumer = (WebsocketConsumer) httpConsumerEntry.getValue();
            consumer.configureFramework(config);
            consumer.configureEventsResending(enableEventsResending);
        }
    }
}
