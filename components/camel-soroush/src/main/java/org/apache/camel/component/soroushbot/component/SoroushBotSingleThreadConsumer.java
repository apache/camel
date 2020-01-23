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
package org.apache.camel.component.soroushbot.component;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * The SoroushBot consumer. if concurrentConsumer set to 1, this Consumer will be Instantiated
 * every message will be processed in order of their arrival time
 * this consumer support both Sync and Async processors.
 */

//CHECKSTYLE:OFF
public class SoroushBotSingleThreadConsumer extends SoroushBotAbstractConsumer {

    public SoroushBotSingleThreadConsumer(SoroushBotEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void sendExchange(Exchange exchange) {
        try {
            if (endpoint.isSynchronous()) {
                getProcessor().process(exchange);
            } else {
                getAsyncProcessor().process(exchange, doneSync -> {});
            }
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange",
                    exchange, exchange.getException());
        }
    }
}
