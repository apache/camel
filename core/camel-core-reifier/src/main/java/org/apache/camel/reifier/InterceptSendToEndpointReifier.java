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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.InterceptSendToEndpointManager;
import org.apache.camel.processor.InterceptSendToEndpointService;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.support.DefaultInterceptSendToEndpoint.Interceptor;
import org.apache.camel.support.ExchangeHelper;
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

        final Route registeringRoute = route;
        Processor p = exchange -> {
            // other routes may use this interceptor (such as when they have none), so use the route that is sending
            Route current = ExchangeHelper.getRoute(exchange);
            if (current == null) {
                current = registeringRoute;
            }
            exchange.setProperty(ExchangePropertyKey.INTERCEPTED_ROUTE_ID, current.getId());
            exchange.setProperty(ExchangePropertyKey.INTERCEPTED_NODE_ID, definition.getId());
            exchange.setProperty(ExchangePropertyKey.INTERCEPTED_ROUTE_ENDPOINT_URI, current.getEndpoint().getEndpointUri());
        };

        // the interceptor of this route, which it registers when it starts and unregisters when it stops, so the
        // endpoints are intercepted by the routes that are running (see InterceptSendToEndpointManager)
        Predicate predicate = when != null ? when : exchange -> true;
        Processor pipeline = new FilterProcessor(camelContext, predicate, Pipeline.newInstance(camelContext, p, before));
        Interceptor interceptor = new Interceptor(route.getRouteId(), pipeline, after, skip);
        // the matching endpoints must be wrapped now (before the route resolves its endpoints)
        InterceptSendToEndpointManager.getOrCreate(camelContext).addPattern(matchURI);
        route.addService(new InterceptSendToEndpointService(camelContext, matchURI, interceptor));

        // the interceptor is not a processor in the route (the definition is abstract, and is kept in the route, so the
        // interceptor is created again when the route is created again, such as when CamelContext is restarted)
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
