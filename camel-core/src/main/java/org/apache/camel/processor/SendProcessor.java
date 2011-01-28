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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducerCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.ProducerCallback;
import org.apache.camel.impl.InterceptSendToEndpoint;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Processor for forwarding exchanges to an endpoint destination.
 *
 * @version $Revision$
 */
public class SendProcessor extends ServiceSupport implements AsyncProcessor, Traceable {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected final CamelContext camelContext;
    protected ProducerCache producerCache;
    protected Endpoint destination;
    protected ExchangePattern pattern;

    public SendProcessor(Endpoint destination) {
        ObjectHelper.notNull(destination, "destination");
        this.destination = destination;
        this.camelContext = destination.getCamelContext();
        ObjectHelper.notNull(this.camelContext, "camelContext");
    }

    public SendProcessor(Endpoint destination, ExchangePattern pattern) {
        this(destination);
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        return "sendTo(" + destination + (pattern != null ? " " + pattern : "") + ")";
    }

    public void setDestination(Endpoint destination) {
        this.destination = destination;
    }

    public String getTraceLabel() {
        return destination.getEndpointUri();
    }

    public void process(final Exchange exchange) throws Exception {
        if (!isStarted()) {
            throw new IllegalStateException("SendProcessor has not been started: " + this);
        }

        // send the exchange to the destination using a producer
        producerCache.doInProducer(destination, exchange, pattern, new ProducerCallback<Exchange>() {
            public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) throws Exception {
                exchange = configureExchange(exchange, pattern);
                if (log.isDebugEnabled()) {
                    log.debug(">>>> " + destination + " " + exchange);
                }
                producer.process(exchange);
                return exchange;
            }
        });
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (!isStarted()) {
            throw new IllegalStateException("SendProcessor has not been started: " + this);
        }

        // send the exchange to the destination using a producer
        return producerCache.doInAsyncProducer(destination, exchange, pattern, callback, new AsyncProducerCallback() {
            public boolean doInAsyncProducer(Producer producer, AsyncProcessor asyncProducer, Exchange exchange, ExchangePattern pattern, AsyncCallback callback) {
                exchange = configureExchange(exchange, pattern);
                if (log.isDebugEnabled()) {
                    log.debug(">>>> " + destination + " " + exchange);
                }
                return AsyncProcessorHelper.process(asyncProducer, exchange, callback);
            }
        });
    }

    public Endpoint getDestination() {
        return destination;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern) {
        if (pattern != null) {
            exchange.setPattern(pattern);
        }
        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, destination.getEndpointUri());
        return exchange;
    }

    protected void doStart() throws Exception {
        if (producerCache == null) {
            producerCache = new ProducerCache(this, camelContext);
            // add it as a service so we can manage it
            camelContext.addService(producerCache);
        }
        ServiceHelper.startService(producerCache);

        // the destination could since have been intercepted by a interceptSendToEndpoint so we got to
        // lookup this before we can use the destination
        Endpoint lookup = camelContext.hasEndpoint(destination.getEndpointKey());
        if (lookup instanceof InterceptSendToEndpoint) {
            if (log.isDebugEnabled()) {
                log.debug("Intercepted sending to " + destination.getEndpointUri() + " -> " + lookup.getEndpointUri());
            }
            destination = lookup;
        }
        // warm up the producer by starting it so we can fail fast if there was a problem
        // however must start endpoint first
        ServiceHelper.startService(destination);
        producerCache.startProducer(destination);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
    }

}
