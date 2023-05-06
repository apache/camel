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
package org.apache.camel.component.as2;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.entity.EntityParser;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.support.component.AbstractApiConsumer;
import org.apache.camel.support.component.ApiConsumerHelper;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodHelper;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Optional.ofNullable;

/**
 * The AS2 consumer.
 *
 * Implementation detail. This AS2 consumer extends AbstractApiConsumer but its not scheduled polling based. Instead it
 * uses a HTTP listener to connect to AS2 server and listen for events.
 */
public class AS2Consumer extends AbstractApiConsumer<AS2ApiName, AS2Configuration> implements HttpRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AS2Consumer.class);

    private static final String HANDLER_PROPERTY = "handler";
    private static final String REQUEST_URI_PROPERTY = "requestUri";

    private AS2ServerConnection as2ServerConnection;
    private AS2ServerManager apiProxy;
    private final ApiMethod apiMethod;
    private final Map<String, Object> properties;

    public AS2Consumer(AS2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);

        apiMethod = ApiConsumerHelper.findMethod(endpoint, this);

        properties = new HashMap<>();
        properties.putAll(endpoint.getEndpointProperties());
        properties.put(HANDLER_PROPERTY, this);
    }

    @Override
    public void interceptPropertyNames(Set<String> propertyNames) {
        propertyNames.add(HANDLER_PROPERTY);
    }

    @Override
    protected int poll() throws Exception {
        return 0;
    }

    @Override
    public AS2Endpoint getEndpoint() {
        return (AS2Endpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Add listener property to register this consumer as listener for events
        as2ServerConnection = getEndpoint().getAS2ServerConnection();
        apiProxy = new AS2ServerManager(as2ServerConnection);

        // invoke the API method to start listening
        ApiMethodHelper.invokeMethod(apiProxy, apiMethod, properties);
    }

    @Override
    protected void doStop() throws Exception {
        if (apiProxy != null) {
            String requestUri = (String) properties.get(REQUEST_URI_PROPERTY);
            apiProxy.stopListening(requestUri);
        }

        super.doStop();
    }

    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context)
            throws HttpException {
        Exception exception;
        try {
            if (request instanceof HttpEntityEnclosingRequest) {
                EntityParser.parseAS2MessageEntity(request);
                apiProxy.handleMDNResponse(context, getEndpoint().getSubject(),
                        ofNullable(getEndpoint().getFrom()).orElse(getEndpoint().getConfiguration().getServer()));
            }
            ApplicationEntity ediEntity
                    = HttpMessageUtils.extractEdiPayload(request,
                            new HttpMessageUtils.DecrpytingAndSigningInfo(
                                    as2ServerConnection.getValidateSigningCertificateChain(),
                                    as2ServerConnection.getDecryptingPrivateKey()));

            // Set AS2 Interchange property and EDI message into body of input message.
            Exchange exchange = createExchange(false);

            try {
                HttpCoreContext coreContext = HttpCoreContext.adapt(context);
                exchange.setProperty(AS2Constants.AS2_INTERCHANGE, coreContext);
                exchange.getIn().setBody(ediEntity.getEdiMessage());
                // send message to next processor in the route
                getProcessor().process(exchange);
            } finally {
                // check if an exception occurred and was not handled
                exception = exchange.getException();
                releaseExchange(exchange, false);
            }
        } catch (Exception e) {
            LOG.warn("Failed to process AS2 message", e);
            exception = e;
        }

        if (exception != null) {
            throw new HttpException("Failed to process AS2 message: " + exception.getMessage(), exception);
        }
    }

}
