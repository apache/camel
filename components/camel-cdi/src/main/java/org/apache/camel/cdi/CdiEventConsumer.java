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
package org.apache.camel.cdi;

import javax.enterprise.inject.Vetoed;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Vetoed
final class CdiEventConsumer<T> extends DefaultConsumer {

    private final Logger logger = LoggerFactory.getLogger(CdiEventConsumer.class);

    private final CdiEventEndpoint<T> endpoint;

    CdiEventConsumer(CdiEventEndpoint<T> endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.addConsumer(this);
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.removeConsumer(this);
        super.doStop();
    }

    void notify(T event) {
        logger.debug("Consuming CDI event [{}] with {}", event, this);

        Exchange exchange = getEndpoint().createExchange();
        // TODO: would that be possible to propagate the event metadata?
        exchange.getIn().setBody(event);

        // Avoid infinite loop of exchange events
        if (event instanceof ExchangeEvent) {
            exchange.adapt(ExtendedExchange.class).setNotifyEvent(true);
        }
        try {
            getProcessor().process(exchange);
        } catch (Exception cause) {
            throw new RuntimeExchangeException("Error while processing CDI event", exchange, cause);
        } finally {
            if (event instanceof ExchangeEvent) {
                exchange.adapt(ExtendedExchange.class).setNotifyEvent(false);
            }
        }
    }
}
