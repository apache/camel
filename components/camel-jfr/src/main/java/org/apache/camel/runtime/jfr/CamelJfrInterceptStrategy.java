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
package org.apache.camel.runtime.jfr;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

public class CamelJfrInterceptStrategy implements InterceptStrategy {

    @Override
    public Processor wrapProcessorInInterceptors(
            CamelContext camelContext, NamedNode definition, Processor target, Processor nextTarget) {
        return new JfrProcessor(target, definition.getId(), definition.getShortName());
    }

    private static final class JfrProcessor extends DelegateAsyncProcessor {
        private final String processorId;
        private final String processorType;

        JfrProcessor(Processor target, String processorId, String processorType) {
            super(target);
            this.processorId = processorId;
            this.processorType = processorType;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            CamelProcessorEvent event = new CamelProcessorEvent();
            final boolean enabled = event.isEnabled();
            if (enabled) {
                event.exchangeId = exchange.getExchangeId();
                event.routeId = exchange.getFromRouteId();
                event.processorId = processorId;
                event.processorType = processorType;
                event.begin();
            }
            return processor.process(exchange, doneSync -> {
                try {
                    if (enabled) {
                        event.failed = exchange.isFailed();
                        event.end();
                        event.commit();
                    }
                } finally {
                    callback.done(doneSync);
                }
            });
        }
    }
}
