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

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.WhenSkipSendToEndpointDefinition;
import org.apache.camel.processor.FilterProcessor;

public class WhenSkipSendToEndpointReifier extends ExpressionReifier<WhenSkipSendToEndpointDefinition> {

    public WhenSkipSendToEndpointReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (WhenSkipSendToEndpointDefinition) definition);
    }

    @Override
    public FilterProcessor createProcessor() throws Exception {
        return createFilterProcessor();
    }

    @Override
    protected Predicate createPredicate() {
        // we need to keep track whether the when matches or not, so delegate
        // the predicate and add the matches result as a property on the
        // exchange
        final Predicate delegate = super.createPredicate();
        return new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                boolean matches = delegate.matches(exchange);
                exchange.setProperty(Exchange.INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED, matches);
                return matches;
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }
}
