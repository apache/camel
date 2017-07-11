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
package org.apache.camel.component.olingo2;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.olingo2.api.Olingo2ResponseHandler;
import org.apache.camel.component.olingo2.internal.Olingo2ApiName;
import org.apache.camel.component.olingo2.internal.Olingo2Constants;
import org.apache.camel.component.olingo2.internal.Olingo2PropertiesHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.AbstractApiProducer;
import org.apache.camel.util.component.ApiMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Olingo2 producer.
 */
public class Olingo2Producer extends AbstractApiProducer<Olingo2ApiName, Olingo2Configuration> {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo2Producer.class);

    private static final String RESPONSE_HTTP_HEADERS = "responseHttpHeaders";

    public Olingo2Producer(Olingo2Endpoint endpoint) {
        super(endpoint, Olingo2PropertiesHelper.getHelper());
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // properties for method arguments
        final Map<String, Object> properties = new HashMap<String, Object>();
        properties.putAll(endpoint.getEndpointProperties());
        propertiesHelper.getExchangeProperties(exchange, properties);

        // let the endpoint and the Producer intercept properties
        endpoint.interceptProperties(properties);
        interceptProperties(properties);

        // create response handler
        properties.put(Olingo2Endpoint.RESPONSE_HANDLER_PROPERTY, new Olingo2ResponseHandler<Object>() {
            @Override
            public void onResponse(Object response, Map<String, String> responseHeaders) {
                // producer returns a single response, even for methods with List return types
                exchange.getOut().setBody(response);
                // copy headers
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                
                // Add http response headers
                exchange.getOut().setHeader(Olingo2Constants.PROPERTY_PREFIX + RESPONSE_HTTP_HEADERS, responseHeaders);

                interceptResult(response, exchange);

                callback.done(false);
            }

            @Override
            public void onException(Exception ex) {
                exchange.setException(ex);
                callback.done(false);
            }

            @Override
            public void onCanceled() {
                exchange.setException(new RuntimeCamelException("OData HTTP Request cancelled!"));
                callback.done(false);
            }
        });

        // decide which method to invoke
        final ApiMethod method = findMethod(exchange, properties);
        if (method == null) {
            // synchronous failure
            callback.done(true);
            return true;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Invoking operation {} with {}", method.getName(), properties.keySet());
        }

        try {
            doInvokeMethod(method, properties);
        } catch (Throwable t) {
            exchange.setException(ObjectHelper.wrapRuntimeCamelException(t));
            callback.done(true);
            return true;
        }

        return false;
    }
}
