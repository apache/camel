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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.wrapRuntimeCamelException;

/**
 * A client helper object (named like Spring's TransactionTemplate & JmsTemplate
 * et al) for working with Camel and sending {@link org.apache.camel.Message} instances in an
 * {@link org.apache.camel.Exchange} to an {@link org.apache.camel.Endpoint}.
 *
 * @version $Revision$
 */
public class DefaultProducerTemplate extends ServiceSupport implements ProducerTemplate {
    private CamelContext context;
    private final ProducerCache producerCache = new ProducerCache();
    private boolean useEndpointCache = true;
    private final Map<String, Endpoint> endpointCache = new HashMap<String, Endpoint>();
    private Endpoint defaultEndpoint;
    
    public DefaultProducerTemplate(CamelContext context) {
        this.context = context;
    }

    public DefaultProducerTemplate(CamelContext context, Endpoint defaultEndpoint) {
        this(context);
        this.defaultEndpoint = defaultEndpoint;
    }

    public static DefaultProducerTemplate newInstance(CamelContext camelContext, String defaultEndpointUri) {
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, defaultEndpointUri);
        return new DefaultProducerTemplate(camelContext, endpoint);
    }   

    public Exchange send(String endpointUri, Exchange exchange) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, exchange);
    }

    public Exchange send(String endpointUri, Processor processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, processor);
    }

    public Exchange send(String endpointUri, Processor processor, AsyncCallback callback) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, processor, callback);
    }

    public Exchange send(String endpointUri, ExchangePattern pattern, Processor processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, pattern, processor);
    }

    public Exchange send(Endpoint endpoint, Exchange exchange) {
        Exchange convertedExchange = exchange;
        producerCache.send(endpoint, convertedExchange);
        return convertedExchange;
    }

    public Exchange send(Endpoint endpoint, Processor processor) {
        return producerCache.send(endpoint, processor);
    }

    public Exchange send(Endpoint endpoint, Processor processor, AsyncCallback callback) {
        return producerCache.send(endpoint, processor, callback);
    }

    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor) {
        return producerCache.send(endpoint, pattern, processor);
    }

    public Object sendBody(Endpoint endpoint, ExchangePattern pattern, Object body) {
        Exchange result = send(endpoint, pattern, createSetBodyProcessor(body));
        return extractResultBody(result, pattern);
    }

    public Object sendBody(Endpoint endpoint, Object body) {
        Exchange result = send(endpoint, createSetBodyProcessor(body));
        return extractResultBody(result);
    }

    public Object sendBody(String endpointUri, Object body) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return sendBody(endpoint, body);
    }

    public Object sendBody(String endpointUri, ExchangePattern pattern, Object body) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return sendBody(endpoint, pattern, body);
    }

    public Object sendBodyAndHeader(String endpointUri, final Object body, final String header,
            final Object headerValue) {
        return sendBodyAndHeader(resolveMandatoryEndpoint(endpointUri), body, header, headerValue);
    }

    public Object sendBodyAndHeader(Endpoint endpoint, final Object body, final String header,
            final Object headerValue) {
        Exchange result = send(endpoint, createBodyAndHeaderProcessor(body, header, headerValue));
        return extractResultBody(result);
    }

    public Object sendBodyAndHeader(Endpoint endpoint, ExchangePattern pattern, final Object body, final String header,
            final Object headerValue) {
        Exchange result = send(endpoint, pattern, createBodyAndHeaderProcessor(body, header, headerValue));
        return extractResultBody(result, pattern);
    }

    public Object sendBodyAndHeader(String endpoint, ExchangePattern pattern, final Object body, final String header,
            final Object headerValue) {
        Exchange result = send(endpoint, pattern, createBodyAndHeaderProcessor(body, header, headerValue));
        return extractResultBody(result, pattern);
    }

    public Object sendBodyAndHeaders(String endpointUri, final Object body, final Map<String, Object> headers) {
        return sendBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers);
    }

    public Object sendBodyAndHeaders(Endpoint endpoint, final Object body, final Map<String, Object> headers) {
        Exchange result = send(endpoint, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                for (Map.Entry<String, Object> header : headers.entrySet()) {
                    in.setHeader(header.getKey(), header.getValue());
                }
                in.setBody(body);
            }
        });
        return extractResultBody(result);
    }

    public Object sendBodyAndHeaders(String endpointUri, ExchangePattern pattern, Object body, Map<String, Object> headers) {
        return sendBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), pattern, body, headers);
    }

    public Object sendBodyAndHeaders(Endpoint endpoint, ExchangePattern pattern, final Object body, final Map<String, Object> headers) {
        Exchange result = send(endpoint,  pattern, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                for (Map.Entry<String, Object> header : headers.entrySet()) {
                    in.setHeader(header.getKey(), header.getValue());
                }
                in.setBody(body);
            }
        });
        return extractResultBody(result);
    }

    // Methods using an InOut ExchangePattern
    // -----------------------------------------------------------------------

    public Exchange request(Endpoint endpoint, Processor processor) {
        return send(endpoint, ExchangePattern.InOut, processor);
    }

    public Object requestBody(Endpoint endpoint, Object body) {
        return sendBody(endpoint, ExchangePattern.InOut, body);
    }

    public Object requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue) {
        return sendBodyAndHeader(endpoint, ExchangePattern.InOut, body, header, headerValue);
    }

    public Exchange request(String endpoint, Processor processor) {
        return send(endpoint, ExchangePattern.InOut, processor);
    }

    public Object requestBody(String endpoint, Object body) {
        return sendBody(endpoint, ExchangePattern.InOut, body);
    }

    public Object requestBodyAndHeader(String endpoint, Object body, String header, Object headerValue) {
        return sendBodyAndHeader(endpoint, ExchangePattern.InOut, body, header, headerValue);
    }

    public Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) {
        return requestBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers);
    }

    public Object requestBodyAndHeaders(Endpoint endpoint, final Object body, final Map<String, Object> headers) {
        return sendBodyAndHeaders(endpoint, ExchangePattern.InOut, body, headers);
    }

    // Methods using the default endpoint
    // -----------------------------------------------------------------------

    public Object sendBody(Object body) {
        return sendBody(getMandatoryDefaultEndpoint(), body);
    }

    public Exchange send(Exchange exchange) {
        return send(getMandatoryDefaultEndpoint(), exchange);
    }

    public Exchange send(Processor processor) {
        return send(getMandatoryDefaultEndpoint(), processor);
    }

    public Object sendBodyAndHeader(Object body, String header, Object headerValue) {
        return sendBodyAndHeader(getMandatoryDefaultEndpoint(), body, header, headerValue);
    }

    public Object sendBodyAndHeaders(Object body, Map<String, Object> headers) {
        return sendBodyAndHeaders(getMandatoryDefaultEndpoint(), body, headers);
    }

    // Properties
    // -----------------------------------------------------------------------
    public Producer getProducer(Endpoint endpoint) {
        return producerCache.getProducer(endpoint);
    }

    public CamelContext getContext() {
        return context;
    }

    public Endpoint getDefaultEndpoint() {
        return defaultEndpoint;
    }

    public void setDefaultEndpoint(Endpoint defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }

    /**
     * Sets the default endpoint to use if none is specified
     */
    public void setDefaultEndpointUri(String endpointUri) {
        setDefaultEndpoint(getContext().getEndpoint(endpointUri));
    }

    public boolean isUseEndpointCache() {
        return useEndpointCache;
    }

    public void setUseEndpointCache(boolean useEndpointCache) {
        this.useEndpointCache = useEndpointCache;
    }

    public <T extends Endpoint> T getResolvedEndpoint(String endpointUri, Class<T> expectedClass) {
        Endpoint e = null;
        synchronized (endpointCache) {
            e = endpointCache.get(endpointUri);
        }
        if (e != null && expectedClass.isAssignableFrom(e.getClass())) {
            return expectedClass.asSubclass(expectedClass).cast(e);
        }
        return null;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected Processor createBodyAndHeaderProcessor(final Object body, final String header, final Object headerValue) {
        return new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setHeader(header, headerValue);
                in.setBody(body);
            }
        };
    }

    protected Processor createSetBodyProcessor(final Object body) {
        return new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
            }
        };
    }

    protected Endpoint resolveMandatoryEndpoint(String endpointUri) {
        Endpoint endpoint = null;

        if (isUseEndpointCache()) {
            synchronized (endpointCache) {
                endpoint = endpointCache.get(endpointUri);
                if (endpoint == null) {
                    endpoint = context.getEndpoint(endpointUri);
                    if (endpoint != null) {
                        endpointCache.put(endpointUri, endpoint);
                    }
                }
            }
        } else {
            endpoint = context.getEndpoint(endpointUri);
        }
        if (endpoint == null) {
            throw new NoSuchEndpointException(endpointUri);
        }
        return endpoint;
    }

    protected Endpoint getMandatoryDefaultEndpoint() {
        Endpoint answer = getDefaultEndpoint();
        ObjectHelper.notNull(answer, "defaultEndpoint");
        return answer;
    }

    protected void doStart() throws Exception {
        producerCache.start();
    }

    protected void doStop() throws Exception {
        producerCache.stop();
        endpointCache.clear();
    }

    /**
     * Extracts the body from the given result.
     *
     * @param result   the result
     * @return  the result, can be <tt>null</tt>.
     */
    protected Object extractResultBody(Exchange result) {
        return extractResultBody(result, null);
    }

    /**
     * Extracts the body from the given result.
     * <p/>
     * If the exchange pattern is provided it will try to honor it and retrive the body
     * from either IN or OUT according to the pattern.
     *
     * @param result   the result
     * @param pattern  exchange pattern if given, can be <tt>null</tt>
     * @return  the result, can be <tt>null</tt>.
     */
    protected Object extractResultBody(Exchange result, ExchangePattern pattern) {
        Object answer = null;
        if (result != null) {
            // rethrow if there was an exception
            if (result.getException() != null) {
                throw wrapRuntimeCamelException(result.getException());
            }

            // result could have a fault message
            if (hasFaultMessage(result)) {
                return result.getFault().getBody();
            }

            // okay no fault then return the response according to the pattern
            // try to honor pattern if provided
            boolean notOut = pattern != null && !pattern.isOutCapable();
            boolean hasOut = result.getOut(false) != null;
            if (hasOut && !notOut) {
                answer = result.getOut().getBody();
            } else {
                answer = result.getIn().getBody();
            }
        }
        return answer;
    }

    protected boolean hasFaultMessage(Exchange result) {
        Message faultMessage = result.getFault(false);
        if (faultMessage != null) {
            Object faultBody = faultMessage.getBody();
            if (faultBody != null) {
                return true;
            }
        }
        return false;
    }
}