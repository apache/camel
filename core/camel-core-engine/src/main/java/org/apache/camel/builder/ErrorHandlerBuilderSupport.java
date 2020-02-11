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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.NamedNode;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.processor.errorhandler.ExceptionPolicy;
import org.apache.camel.processor.errorhandler.ExceptionPolicyKey;
import org.apache.camel.processor.errorhandler.ExceptionPolicyStrategy;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class for builders of error handling.
 */
public abstract class ErrorHandlerBuilderSupport implements ErrorHandlerBuilder {
    private ExceptionPolicyStrategy exceptionPolicyStrategy;

    protected void cloneBuilder(ErrorHandlerBuilderSupport other) {
        other.exceptionPolicyStrategy = exceptionPolicyStrategy;
    }

    /**
     * Configures the other error handler based on this error handler.
     *
     * @param routeContext the route context
     * @param handler the other error handler
     */
    public void configure(RouteContext routeContext, ErrorHandler handler) {
        if (handler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport handlerSupport = (ErrorHandlerSupport)handler;

            Set<NamedNode> list = routeContext.getErrorHandlers(this);
            for (NamedNode exception : list) {
                addExceptionPolicy(handlerSupport, routeContext, (OnExceptionDefinition)exception);
            }
        }
        if (handler instanceof RedeliveryErrorHandler) {
            RedeliveryErrorHandler reh = (RedeliveryErrorHandler)handler;
            boolean original = reh.isUseOriginalMessagePolicy() || reh.isUseOriginalBodyPolicy();
            if (original) {
                if (reh.isUseOriginalMessagePolicy() && reh.isUseOriginalBodyPolicy()) {
                    throw new IllegalArgumentException("Cannot set both useOriginalMessage and useOriginalBody on the error handler");
                }
                // ensure allow original is turned on
                routeContext.setAllowUseOriginalMessage(true);
            }
        }
    }

    public static void addExceptionPolicy(ErrorHandlerSupport handlerSupport, RouteContext routeContext, OnExceptionDefinition exceptionType) {
        if (routeContext != null) {
            // add error handler as child service so they get lifecycle handled
            Processor errorHandler = routeContext.getOnException(exceptionType.getId());
            handlerSupport.addErrorHandler(errorHandler);

            // load exception classes
            List<Class<? extends Throwable>> list;
            if (ObjectHelper.isNotEmpty(exceptionType.getExceptions())) {
                list = createExceptionClasses(exceptionType, routeContext.getCamelContext().getClassResolver());
                for (Class<? extends Throwable> clazz : list) {
                    String routeId = null;
                    // only get the route id, if the exception type is route
                    // scoped
                    if (exceptionType.isRouteScoped()) {
                        RouteDefinition route = ProcessorDefinitionHelper.getRoute(exceptionType);
                        if (route != null) {
                            routeId = route.getId();
                        }
                    }
                    Predicate when = exceptionType.getOnWhen() != null ? exceptionType.getOnWhen().getExpression() : null;
                    ExceptionPolicyKey key = new ExceptionPolicyKey(routeId, clazz, when);
                    ExceptionPolicy policy = toExceptionPolicy(exceptionType, routeContext);
                    handlerSupport.addExceptionPolicy(key, policy);
                }
            }
        }
    }

    protected static ExceptionPolicy toExceptionPolicy(OnExceptionDefinition exceptionType, RouteContext routeContext) {
        return ErrorHandlerReifier.createExceptionPolicy(exceptionType, routeContext.getCamelContext());
    }

    protected static List<Class<? extends Throwable>> createExceptionClasses(OnExceptionDefinition exceptionType, ClassResolver resolver) {
        List<String> list = exceptionType.getExceptions();
        List<Class<? extends Throwable>> answer = new ArrayList<>(list.size());
        for (String name : list) {
            try {
                Class<? extends Throwable> type = resolver.resolveMandatoryClass(name, Throwable.class);
                answer.add(type);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        return answer;
    }

    /**
     * Sets the exception policy to use
     */
    public ErrorHandlerBuilderSupport exceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        setExceptionPolicyStrategy(exceptionPolicyStrategy);
        return this;
    }

    /**
     * Gets the exception policy strategy
     */
    public ExceptionPolicyStrategy getExceptionPolicyStrategy() {
        return exceptionPolicyStrategy;
    }

    /**
     * Sets the exception policy strategy to use for resolving the
     * {@link org.apache.camel.model.OnExceptionDefinition} to use for a given
     * thrown exception
     *
     * @param exceptionPolicyStrategy the exception policy strategy
     */
    public void setExceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        ObjectHelper.notNull(exceptionPolicyStrategy, "ExceptionPolicyStrategy");
        this.exceptionPolicyStrategy = exceptionPolicyStrategy;
    }

}
