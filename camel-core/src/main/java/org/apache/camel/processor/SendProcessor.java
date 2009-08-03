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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerCallback;
import org.apache.camel.impl.InterceptSendToEndpoint;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Processor for forwarding exchanges to an endpoint destination.
 *
 * @version $Revision$
 */
public class SendProcessor extends ServiceSupport implements Processor, Traceable {
    protected static final transient Log LOG = LogFactory.getLog(SendProcessor.class);
    protected ProducerCache producerCache;
    protected Endpoint destination;
    protected ExchangePattern pattern;
    private boolean init;

    public SendProcessor(Endpoint destination) {
        ObjectHelper.notNull(destination, "destination");
        this.destination = destination;
    }

    public SendProcessor(Endpoint destination, ExchangePattern pattern) {
        this(destination);
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        return "sendTo(" + destination + (pattern != null ? " " + pattern : "") + ")";
    }

    public String getTraceLabel() {
        return destination.getEndpointUri();
    }

    public void process(final Exchange exchange) throws Exception {
        // the destination could since have been intercepted by a interceptSendToEndpoint so we got to
        // init this before we can use the destination
        if (!init) {
            init = true;
            Endpoint lookup = exchange.getContext().hasEndpoint(destination.getEndpointKey());
            if (lookup instanceof InterceptSendToEndpoint) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("SendTo is intercepted using a interceptSendToEndpoint: " + lookup.getEndpointUri());
                }
                destination = lookup;
            }
        }

        // send the exchange to the destination using a producer
        getProducerCache(exchange).doInProducer(destination, exchange, pattern, new ProducerCallback<Exchange>() {
            public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) throws Exception {
                exchange = configureExchange(exchange, pattern);
                producer.process(exchange);
                return exchange;
            }
        });
    }

    protected ProducerCache getProducerCache(Exchange exchange) throws Exception {
        // setup producer cache as we need to use the pluggable service pool defined on camel context
        if (producerCache == null) {
            this.producerCache = new ProducerCache(exchange.getContext().getProducerServicePool());
            this.producerCache.start();
        }
        return this.producerCache;
    }

    public Endpoint getDestination() {
        return destination;
    }

    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern) {
        if (pattern != null) {
            exchange.setPattern(pattern);
        }
        return exchange;
    }

    protected void doStart() throws Exception {
        if (producerCache != null) {
            producerCache.start();
        }
    }

    protected void doStop() throws Exception {
        if (producerCache != null) {
            producerCache.stop();
        }
    }

}
