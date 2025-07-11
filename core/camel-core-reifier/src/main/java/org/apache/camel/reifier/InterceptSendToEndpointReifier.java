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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.processor.InterceptSendToEndpointCallback;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.support.PluginHelper;

public class InterceptSendToEndpointReifier extends ProcessorReifier<InterceptSendToEndpointDefinition> {

    public InterceptSendToEndpointReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (InterceptSendToEndpointDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // create the before
        final Processor before = this.createChildProcessor(true);
        // create the after
        Processor afterProcessor = null;
        String afterUri = parseString(definition.getAfterUri());
        if (afterUri != null) {
            ToDefinition to = new ToDefinition(afterUri);
            // at first use custom factory
            afterProcessor = PluginHelper.getProcessorFactory(camelContext).createProcessor(route, to);
            // fallback to default implementation if factory did not create the processor
            if (afterProcessor == null) {
                afterProcessor = createProcessor(to);
            }
        }
        final Processor after = afterProcessor;
        final String matchURI = parseString(definition.getUri());
        final boolean skip = parseBoolean(definition.getSkipSendToOriginalEndpoint(), false);

        Predicate when = null;
        if (definition.getOnWhen() != null) {
            definition.getOnWhen().preCreateProcessor();
            when = new OnWhenPredicate(createPredicate(definition.getOnWhen().getExpression()));
        }

        Processor p = exchange -> {
            exchange.setProperty(ExchangePropertyKey.INTERCEPTED_ROUTE_ID, route.getId());
            exchange.setProperty(ExchangePropertyKey.INTERCEPTED_NODE_ID, definition.getId());
            exchange.setProperty(ExchangePropertyKey.INTERCEPTED_ROUTE_ENDPOINT_URI, route.getEndpoint().getEndpointUri());
        };

        // register endpoint callback so we can proxy the endpoint
        camelContext.getCamelContextExtension()
                .registerEndpointCallback(
                        new InterceptSendToEndpointCallback(
                                camelContext,
                                Pipeline.newInstance(camelContext, p, before),
                                after,
                                matchURI, skip, when));

        // remove the original intercepted route from the outputs as we do not
        // intercept as the regular interceptor
        // instead we use the proxy endpoints producer do the triggering. That
        // is we trigger when someone sends
        // an exchange to the endpoint, see InterceptSendToEndpoint for details.
        RouteDefinition route = (RouteDefinition) this.route.getRoute();
        List<ProcessorDefinition<?>> outputs = route.getOutputs();
        outputs.remove(definition);

        // and return no processor to invoke next from me
        return null;
    }

    /**
     * Wrap in predicate to set filter marker we need to keep track whether the when matches or not, so delegate the
     * predicate and add the matches result as a property on the exchange
     */
    private static class OnWhenPredicate implements Predicate {

        private final Predicate delegate;

        public OnWhenPredicate(Predicate delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean matches(Exchange exchange) {
            boolean matches = delegate.matches(exchange);
            exchange.setProperty(ExchangePropertyKey.INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED, matches);
            return matches;
        }

        @Override
        public void init(CamelContext context) {
            delegate.init(context);
        }

        @Override
        public void initPredicate(CamelContext context) {
            delegate.initPredicate(context);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

}
