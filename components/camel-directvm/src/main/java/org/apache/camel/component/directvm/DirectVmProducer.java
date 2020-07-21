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
package org.apache.camel.component.directvm;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Direct-VM producer.
 */
public class DirectVmProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DirectVmProducer.class);

    private DirectVmEndpoint endpoint;

    public DirectVmProducer(DirectVmEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // send to consumer
        DirectVmConsumer consumer = endpoint.getComponent().getConsumer(endpoint);
        
        if (consumer == null) {
            if (endpoint.isFailIfNoConsumers()) {
                exchange.setException(new DirectVmConsumerNotAvailableException("No consumers available on endpoint: " + endpoint, exchange));
            } else {
                LOG.debug("message ignored, no consumers available on endpoint: {}", endpoint);
            }
            callback.done(true);
            return true;
        }
        
        final HeaderFilterStrategy headerFilterStrategy = endpoint.getHeaderFilterStrategy();

        // Only clone the Exchange if we actually need to filter out properties or headers.
        final Exchange submitted = (!endpoint.isPropagateProperties() || headerFilterStrategy != null) ? exchange.copy() : exchange;

        // Clear properties in the copy if we are not propagating them.
        if (!endpoint.isPropagateProperties()) {
            submitted.getProperties().clear();
        }
        
        // Filter headers by Header Filter Strategy if there is one set.
        if (headerFilterStrategy != null) {
            submitted.getIn().getHeaders().entrySet().removeIf(e -> headerFilterStrategy.applyFilterToCamelHeaders(e.getKey(), e.getValue(), submitted));
        }
        
        return consumer.getAsyncProcessor().process(submitted, done -> {
            Message msg = submitted.getMessage();

            if (headerFilterStrategy != null) {
                msg.getHeaders().entrySet().removeIf(e -> headerFilterStrategy.applyFilterToExternalHeaders(e.getKey(), e.getValue(), submitted));
            }

            if (exchange != submitted) {
                // only need to copy back if they are different
                exchange.setException(submitted.getException());
                exchange.getOut().copyFrom(msg);
            }

            if (endpoint.isPropagateProperties()) {
                exchange.getProperties().putAll(submitted.getProperties());
            }

            callback.done(done);
        });
    }
    
}