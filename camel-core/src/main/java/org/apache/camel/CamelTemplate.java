/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel;

import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ProducerCache;

import java.util.HashMap;
import java.util.Map;

/**
 * A client helper object (named like Spring's TransactionTemplate & JmsTemplate et al)
 * for working with Camel and sending {@link Message} instances in an {@link Exchange}
 * to an {@link Endpoint}.
 *
 * @version $Revision$
 */
public class CamelTemplate<E extends Exchange> extends ServiceSupport {
    private CamelContext context;
    private ProducerCache<E> producerCache = new ProducerCache<E>();
    private boolean useEndpointCache = true;
    private Map<String, Endpoint<E>> endpointCache = new HashMap<String, Endpoint<E>>();
    private Endpoint<E> defaultEndpoint;


    public CamelTemplate(CamelContext context) {
        this.context = context;
    }

    public CamelTemplate(CamelContext context, Endpoint defaultEndpoint) {
        this(context);
        this.defaultEndpoint = defaultEndpoint;
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param exchange    the exchange to send
     */
    public E send(String endpointUri, E exchange) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        send(endpoint, exchange);
        return exchange;
    }

    /**
     * Sends an exchange to an endpoint using a supplied @{link Processor} to populate the exchange
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor   the transformer used to populate the new exchange
     */
    public E send(String endpointUri, Processor processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, processor);
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     */
    public E send(Endpoint<E> endpoint, E exchange) {
        E convertedExchange = endpoint.toExchangeType(exchange);
        producerCache.send(endpoint, convertedExchange);
        return exchange;
    }

    /**
     * Sends an exchange to an endpoint using a supplied @{link Processor} to populate the exchange
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     */
    public E send(Endpoint<E> endpoint, Processor processor) {
        return producerCache.send(endpoint, processor);
    }

    /**
     * Send the body to an endpoint
     *
     * @param endpoint
     * @param body     = the payload
     * @return the result
     */
    public Object sendBody(Endpoint<E> endpoint, final Object body) {
        E result = send(endpoint, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
            }
        });
        return extractResultBody(result);
    }

    /**
     * Send the body to an endpoint
     *
     * @param endpointUri
     * @param body        = the payload
     * @return the result
     */
    public Object sendBody(String endpointUri, final Object body) {
        E result = send(endpointUri, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
            }
        });
        return extractResultBody(result);
    }

    /**
     * Sends the body to an endpoint with a specified header and header value
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload send
     * @param header      the header name
     * @param headerValue the header value
     * @return the result
     */
    public Object sendBody(String endpointUri, final Object body, final String header, final Object headerValue) {
        E result = send(endpointUri, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setHeader(header, headerValue);
                in.setBody(body);
            }
        });
        return extractResultBody(result);
    }
    
    /**
     * Sends the body to an endpoint with the specified headers and header values
     *
     * @param endpointUri the endpoint URI to send to
     * @param body        the payload send
     * @return the result
     */
    public Object sendBody(String endpointUri, final Object body, final Map<String, Object> headers) {
        E result = send(endpointUri, new Processor() {
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

    // Methods using the default endpoint
    //-----------------------------------------------------------------------

    /**
     * Sends the body to the default endpoint and returns the result content
     *
     * @param body the body to send
     * @return the returned message body
     */
    public Object sendBody(Object body) {
        return sendBody(getMandatoryDefaultEndpoint(), body);
    }

    /**
     * Sends the exchange to the default endpoint
     *
     * @param exchange the exchange to send
     */
    public E send(E exchange) {
        return send(getMandatoryDefaultEndpoint(), exchange);
    }

    /**
     * Sends an exchange to the default endpoint
     * using a supplied @{link Processor} to populate the exchange
     *
     * @param processor the transformer used to populate the new exchange
     */
    public E send(Processor processor) {
        return send(getMandatoryDefaultEndpoint(), processor);
    }


    // Properties
    //-----------------------------------------------------------------------
    public Producer<E> getProducer(Endpoint<E> endpoint) {
        return producerCache.getProducer(endpoint);
    }

    public CamelContext getContext() {
        return context;
    }

    public Endpoint<E> getDefaultEndpoint() {
        return defaultEndpoint;
    }

    public void setDefaultEndpoint(Endpoint<E> defaultEndpoint) {
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

    // Implementation methods
    //-----------------------------------------------------------------------

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
        }
        else {
            endpoint = context.getEndpoint(endpointUri);
        }
        if (endpoint == null) {
            throw new NoSuchEndpointException(endpointUri);
        }
        return endpoint;
    }

    protected Endpoint<E> getMandatoryDefaultEndpoint() {
        Endpoint<E> answer = getDefaultEndpoint();
        ObjectHelper.notNull(answer, "defaultEndpoint");
        return answer;
    }

    protected void doStart() throws Exception {
        producerCache.start();
    }

    protected void doStop() throws Exception {
        producerCache.stop();
    }

    protected Object extractResultBody(E result) {
        return result != null ? result.getOut().getBody() : null;
    }
}
