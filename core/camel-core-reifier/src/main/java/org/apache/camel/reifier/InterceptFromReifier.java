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
package org.apache.camel.reifier;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

public class InterceptFromReifier extends InterceptReifier<InterceptFromDefinition> {

    public InterceptFromReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        final Processor child = this.createChildProcessor(true);

        return new DelegateAsyncProcessor(child) {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                if (exchange.getFromEndpoint() != null) {
                    exchange.setProperty(ExchangePropertyKey.INTERCEPTED_ENDPOINT, exchange.getFromEndpoint().getEndpointUri());
                }
                return super.process(exchange, callback);
            }
        };
    }

}
