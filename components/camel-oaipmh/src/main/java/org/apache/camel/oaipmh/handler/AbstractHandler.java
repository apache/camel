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
package org.apache.camel.oaipmh.handler;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.oaipmh.component.OAIPMHConsumer;
import org.apache.camel.oaipmh.model.OAIPMHResponse;
import org.apache.camel.spi.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHandler.class);

    protected final Consumer consumer;
    protected final Endpoint endpoint;
    protected final Processor processor;
    protected final ExceptionHandler exceptionHandler;

    protected AbstractHandler(OAIPMHConsumer consumer) {
        this.consumer = consumer;
        this.endpoint = consumer.getEndpoint();
        this.processor = consumer.getAsyncProcessor();
        this.exceptionHandler = consumer.getExceptionHandler();
    }

    protected void send(OAIPMHResponse message) {
        Exchange exchange = consumer.createExchange(false);
        String xml = message.getRawResponse();
        exchange.getIn().setBody(xml);
        try {
            // send message to next processor in the route
            LOG.trace("sending exchange: {}", exchange);
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                exceptionHandler.handleException("Error processing exchange", exchange, exchange.getException());
            }
            consumer.releaseExchange(exchange, false);
        }
    }

}
