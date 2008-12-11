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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Processor for forwarding exchanges to an endpoint destination.
 *
 * @version $Revision$
 */
public class SendProcessor extends ServiceSupport implements AsyncProcessor, Service {
    private static final transient Log LOG = LogFactory.getLog(SendProcessor.class);
    private Endpoint destination;
    private Producer producer;
    private AsyncProcessor processor;
    private ExchangePattern pattern;

    public SendProcessor(Endpoint destination) {
        if (destination == null) {
            throw new IllegalArgumentException("Endpoint cannot be null!");
        }
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

    public void process(Exchange exchange) throws Exception {
        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
            } else {
                throw new IllegalStateException("No producer, this processor has not been started!");
            }
        } else {
            configureExchange(exchange);
            producer.process(exchange);
        }
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
            } else {
                exchange.setException(new IllegalStateException("No producer, this processor has not been started!"));
            }
            callback.done(true);
            return true;
        } else {
            configureExchange(exchange);
            return processor.process(exchange, callback);
        }
    }

    public Endpoint getDestination() {
        return destination;
    }

    protected void doStart() throws Exception {
        this.producer = destination.createProducer();
        this.producer.start();
        this.processor = AsyncProcessorTypeConverter.convert(producer);
    }

    protected void doStop() throws Exception {
        if (producer != null) {
            try {
                producer.stop();
            } finally {
                producer = null;
                processor = null;
            }
        }
    }

    protected void configureExchange(Exchange exchange) {
        if (pattern != null) {
            exchange.setPattern(pattern);
        }
    }
}
