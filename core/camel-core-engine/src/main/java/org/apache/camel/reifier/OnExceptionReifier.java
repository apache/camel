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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.FatalFallbackErrorHandler;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class OnExceptionReifier extends ProcessorReifier<OnExceptionDefinition> {

    public OnExceptionReifier(RouteContext routeContext, ProcessorDefinition<?> definition) {
        super(routeContext, (OnExceptionDefinition)definition);
    }

    @Override
    public void addRoutes() throws Exception {
        // assign whether this was a route scoped onException or not
        // we need to know this later when setting the parent, as only route
        // scoped should have parent
        // Note: this logic can possible be removed when the Camel routing
        // engine decides at runtime
        // to apply onException in a more dynamic fashion than current code base
        // and therefore is in a better position to decide among context/route
        // scoped OnException at runtime
        if (definition.getRouteScoped() == null) {
            definition.setRouteScoped(definition.getParent() != null);
        }

        setHandledFromExpressionType();
        setContinuedFromExpressionType();
        setRetryWhileFromExpressionType();
        setOnRedeliveryFromRedeliveryRef();
        setOnExceptionOccurredFromOnExceptionOccurredRef();

        // must validate configuration before creating processor
        definition.validateConfiguration();

        if (definition.getUseOriginalMessage() != null && parseBoolean(definition.getUseOriginalMessage())) {
            // ensure allow original is turned on
            routeContext.setAllowUseOriginalMessage(true);
        }

        // lets attach this on exception to the route error handler
        Processor child = createOutputsProcessor();
        if (child != null) {
            // wrap in our special safe fallback error handler if OnException
            // have child output
            Processor errorHandler = new FatalFallbackErrorHandler(child);
            String id = getId(definition, routeContext);
            routeContext.setOnException(id, errorHandler);
        }
        // lookup the error handler builder
        ErrorHandlerBuilder builder = (ErrorHandlerBuilder)routeContext.getErrorHandlerFactory();
        // and add this as error handlers
        routeContext.addErrorHandler(builder, definition);
    }

    @Override
    public CatchProcessor createProcessor() throws Exception {
        // load exception classes
        List<Class<? extends Throwable>> classes = null;
        if (definition.getExceptions() != null && !definition.getExceptions().isEmpty()) {
            classes = createExceptionClasses(camelContext.getClassResolver());
        }

        if (definition.getUseOriginalMessage() != null && parseBoolean(definition.getUseOriginalMessage())) {
            // ensure allow original is turned on
            routeContext.setAllowUseOriginalMessage(true);
        }

        // must validate configuration before creating processor
        definition.validateConfiguration();

        Processor childProcessor = this.createChildProcessor(false);

        Predicate when = null;
        if (definition.getOnWhen() != null) {
            when = createPredicate(definition.getOnWhen().getExpression());
        }

        Predicate handle = null;
        if (definition.getHandled() != null) {
            handle = createPredicate(definition.getHandled());
        }

        return new CatchProcessor(classes, childProcessor, when, handle);
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

    private void setHandledFromExpressionType() {
        // TODO: should not modify
        if (definition.getHandled() != null && definition.getHandledPolicy() == null && camelContext != null) {
            definition.handled(createPredicate(definition.getHandled()));
        }
    }

    private void setContinuedFromExpressionType() {
        // TODO: should not modify
        if (definition.getContinued() != null && definition.getContinuedPolicy() == null && camelContext != null) {
            definition.continued(createPredicate(definition.getContinued()));
        }
    }

    private void setRetryWhileFromExpressionType() {
        // TODO: should not modify
        if (definition.getRetryWhile() != null && definition.getRetryWhilePolicy() == null && camelContext != null) {
            definition.retryWhile(createPredicate(definition.getRetryWhile()));
        }
    }

    private void setOnRedeliveryFromRedeliveryRef() {
        // lookup onRedelivery if ref is provided
        if (ObjectHelper.isNotEmpty(definition.getOnRedeliveryRef())) {
            // if ref is provided then use mandatory lookup to fail if not found
            Processor onRedelivery = CamelContextHelper.mandatoryLookup(camelContext, definition.getOnRedeliveryRef(), Processor.class);
            definition.setOnRedelivery(onRedelivery);
        }
    }

    private void setOnExceptionOccurredFromOnExceptionOccurredRef() {
        // lookup onRedelivery if ref is provided
        if (ObjectHelper.isNotEmpty(definition.getOnExceptionOccurredRef())) {
            // if ref is provided then use mandatory lookup to fail if not found
            Processor onExceptionOccurred = CamelContextHelper.mandatoryLookup(camelContext, definition.getOnExceptionOccurredRef(), Processor.class);
            definition.setOnExceptionOccurred(onExceptionOccurred);
        }
    }

}
