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

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.FatalFallbackErrorHandler;
import org.apache.camel.spi.ClassResolver;

public class OnExceptionReifier extends ProcessorReifier<OnExceptionDefinition> {

    public OnExceptionReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (OnExceptionDefinition) definition);
    }

    @Override
    public void addRoutes() throws Exception {
        // must validate configuration before creating processor
        definition.validateConfiguration();

        if (parseBoolean(definition.getUseOriginalMessage(), false)) {
            // ensure allow original is turned on
            route.setAllowUseOriginalMessage(true);
        }

        // lets attach this on exception to the route error handler
        Processor child = createOutputsProcessor();
        if (child != null) {
            // wrap in our special safe fallback error handler if OnException
            // have child output
            Processor errorHandler = new FatalFallbackErrorHandler(child, false);
            String id = getId(definition);
            route.setOnException(id, errorHandler);
        }
        // lookup the error handler builder
        ErrorHandlerFactory builder = route.getErrorHandlerFactory();
        // and add this as error handlers
        route.addErrorHandler(builder, definition);
    }

    @Override
    public CatchProcessor createProcessor() throws Exception {
        // load exception classes
        List<Class<? extends Throwable>> classes = null;
        if (definition.getExceptions() != null && !definition.getExceptions().isEmpty()) {
            classes = createExceptionClasses(camelContext.getClassResolver());
        }

        if (parseBoolean(definition.getUseOriginalMessage(), false)) {
            // ensure allow original is turned on
            route.setAllowUseOriginalMessage(true);
        }

        // must validate configuration before creating processor
        definition.validateConfiguration();

        Processor childProcessor = this.createChildProcessor(false);

        Predicate when = null;
        if (definition.getOnWhen() != null) {
            when = createPredicate(definition.getOnWhen().getExpression());
        }

        return new CatchProcessor(getCamelContext(), classes, childProcessor, when);
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

}
