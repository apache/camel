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
package org.apache.camel.component.jcr;

import java.util.LinkedList;
import java.util.List;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

/**
 * A JCR {@link EventListener} which can be used to delegate processing to a
 * Camel endpoint.
 */
public class EndpointEventListener implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointEventListener.class);

    private final JcrEndpoint endpoint;
    private final Processor processor;

    public EndpointEventListener(JcrEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
    }

    @Override
    public void onEvent(EventIterator events) {
        LOG.trace("onEvent START");
        LOG.debug("{} consumer received JCR events: {}", endpoint, events);
        RuntimeCamelException rce = null;

        try {
            final Exchange exchange = createExchange(events);

            try {
                LOG.debug("Processor, {}, is processing exchange, {}", processor, exchange);
                processor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            rce = exchange.getException(RuntimeCamelException.class);
        } catch (Exception e) {
            rce = wrapRuntimeCamelException(e);
        }

        if (rce != null) {
            LOG.trace("onEvent END throwing exception: {}", rce.toString());
            throw rce;
        }

        LOG.trace("onEvent END");
    }

    private Exchange createExchange(EventIterator events) {
        Exchange exchange = endpoint.createExchange();

        List<Event> eventList = new LinkedList<>();
        if (events != null) {
            while (events.hasNext()) {
                eventList.add(events.nextEvent());
            }
        }
        exchange.getIn().setBody(eventList);

        return exchange;
    }

}
