/**
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.FatalFallbackErrorHandler;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

class OnExceptionReifier extends ProcessorReifier<OnExceptionDefinition> {

    OnExceptionReifier(ProcessorDefinition<?> definition) {
        super((OnExceptionDefinition) definition);
    }

    @Override
    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        // assign whether this was a route scoped onException or not
        // we need to know this later when setting the parent, as only route scoped should have parent
        // Note: this logic can possible be removed when the Camel routing engine decides at runtime
        // to apply onException in a more dynamic fashion than current code base
        // and therefore is in a better position to decide among context/route scoped OnException at runtime
        if (definition.getRouteScoped() == null) {
            definition.setRouteScoped(definition.getParent() != null);
        }

        setHandledFromExpressionType(routeContext);
        setContinuedFromExpressionType(routeContext);
        setRetryWhileFromExpressionType(routeContext);
        setOnRedeliveryFromRedeliveryRef(routeContext);
        setOnExceptionOccurredFromOnExceptionOccurredRef(routeContext);

        // load exception classes
        if (definition.getExceptions() != null && !definition.getExceptions().isEmpty()) {
            definition.setExceptionClasses(createExceptionClasses(routeContext.getCamelContext().getClassResolver()));
        }

        // must validate configuration before creating processor
        definition.validateConfiguration();

        if (definition.getUseOriginalMessagePolicy() != null && definition.getUseOriginalMessagePolicy()) {
            // ensure allow original is turned on
            routeContext.setAllowUseOriginalMessage(true);
        }

        // lets attach this on exception to the route error handler
        Processor child = createOutputsProcessor(routeContext);
        if (child != null) {
            // wrap in our special safe fallback error handler if OnException have child output
            Processor errorHandler = new FatalFallbackErrorHandler(child);
            String id = routeContext.getRoute().getId();
            definition.setErrorHandler(id, errorHandler);
        }
        // lookup the error handler builder
        ErrorHandlerBuilder builder = (ErrorHandlerBuilder) ((RouteDefinition) routeContext.getRoute()).getErrorHandlerBuilder();
        // and add this as error handlers
        builder.addErrorHandlers(routeContext, definition);
    }

    @Override
    public CatchProcessor createProcessor(RouteContext routeContext) throws Exception {
        // load exception classes
        if (definition.getExceptions() != null && !definition.getExceptions().isEmpty()) {
            definition.setExceptionClasses(createExceptionClasses(routeContext.getCamelContext().getClassResolver()));
        }

        if (definition.getUseOriginalMessagePolicy() != null && definition.getUseOriginalMessagePolicy()) {
            // ensure allow original is turned on
            routeContext.setAllowUseOriginalMessage(true);
        }

        // must validate configuration before creating processor
        definition.validateConfiguration();

        Processor childProcessor = this.createChildProcessor(routeContext, false);

        Predicate when = null;
        if (definition.getOnWhen() != null) {
            when = definition.getOnWhen().getExpression().createPredicate(routeContext);
        }

        Predicate handle = null;
        if (definition.getHandled() != null) {
            handle = definition.getHandled().createPredicate(routeContext);
        }

        return new CatchProcessor(definition.getExceptionClasses(), childProcessor, when, handle);
    }

    protected List<Class<? extends Throwable>> createExceptionClasses(ClassResolver resolver) throws ClassNotFoundException {
        List<String> list = definition.getExceptions();
        List<Class<? extends Throwable>> answer = new ArrayList<>(list.size());
        for (String name : list) {
            Class<? extends Throwable> type = resolver.resolveMandatoryClass(name, Throwable.class);
            answer.add(type);
        }
        return answer;
    }

    private void setHandledFromExpressionType(RouteContext routeContext) {
        if (definition.getHandled() != null && definition.getHandledPolicy() == null && routeContext != null) {
            definition.handled(definition.getHandled().createPredicate(routeContext));
        }
    }

    private void setContinuedFromExpressionType(RouteContext routeContext) {
        if (definition.getContinued() != null && definition.getContinuedPolicy() == null && routeContext != null) {
            definition.continued(definition.getContinued().createPredicate(routeContext));
        }
    }

    private void setRetryWhileFromExpressionType(RouteContext routeContext) {
        if (definition.getRetryWhile() != null && definition.getRetryWhilePolicy() == null && routeContext != null) {
            definition.retryWhile(definition.getRetryWhile().createPredicate(routeContext));
        }
    }

    private void setOnRedeliveryFromRedeliveryRef(RouteContext routeContext) {
        // lookup onRedelivery if ref is provided
        if (ObjectHelper.isNotEmpty(definition.getOnRedeliveryRef())) {
            // if ref is provided then use mandatory lookup to fail if not found
            Processor onRedelivery = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), definition.getOnRedeliveryRef(), Processor.class);
            definition.setOnRedelivery(onRedelivery);
        }
    }

    private void setOnExceptionOccurredFromOnExceptionOccurredRef(RouteContext routeContext) {
        // lookup onRedelivery if ref is provided
        if (ObjectHelper.isNotEmpty(definition.getOnExceptionOccurredRef())) {
            // if ref is provided then use mandatory lookup to fail if not found
            Processor onExceptionOccurred = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), definition.getOnExceptionOccurredRef(), Processor.class);
            definition.setOnExceptionOccurred(onExceptionOccurred);
        }
    }


}
