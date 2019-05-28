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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyKey;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class for builders of error handling.
 */
public abstract class ErrorHandlerBuilderSupport implements ErrorHandlerBuilder {
    private Map<RouteContext, List<OnExceptionDefinition>> onExceptions = new HashMap<>();
    private ExceptionPolicyStrategy exceptionPolicyStrategy;

    public void addErrorHandlers(RouteContext routeContext, OnExceptionDefinition exception) {
        // only add if we not already have it
        List<OnExceptionDefinition> list = onExceptions.computeIfAbsent(routeContext, rc -> new ArrayList<>());
        if (!list.contains(exception)) {
            list.add(exception);
        }
    }

    protected void cloneBuilder(ErrorHandlerBuilderSupport other) {
        if (!onExceptions.isEmpty()) {
            other.onExceptions.putAll(onExceptions);
        }
        other.exceptionPolicyStrategy = exceptionPolicyStrategy;
    }

    public void configure(RouteContext routeContext, ErrorHandler handler) {
        if (handler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport handlerSupport = (ErrorHandlerSupport) handler;

            List<OnExceptionDefinition> list = onExceptions.get(routeContext);
            if (list != null) {
                for (OnExceptionDefinition exception : list) {
                    addExceptionPolicy(handlerSupport, routeContext, exception);
                }
            }
        }
        if (handler instanceof RedeliveryErrorHandler) {
            boolean original = ((RedeliveryErrorHandler) handler).isUseOriginalMessagePolicy();
            if (original) {
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
            if (exceptionType.getExceptions() != null && !exceptionType.getExceptions().isEmpty()) {
                list = createExceptionClasses(exceptionType, routeContext.getCamelContext().getClassResolver());
                for (Class<? extends Throwable> clazz : list) {
                    String routeId = null;
                    // only get the route id, if the exception type is route scoped
                    if (exceptionType.isRouteScoped()) {
                        RouteDefinition route = ProcessorDefinitionHelper.getRoute(exceptionType);
                        if (route != null) {
                            routeId = route.getId();
                        }
                    }
                    Predicate when = exceptionType.getOnWhen() != null ? exceptionType.getOnWhen().getExpression() : null;
                    ExceptionPolicyKey key = new ExceptionPolicyKey(routeId, clazz, when);
                    ExceptionPolicy policy = toExceptionPolicy(exceptionType);
                    handlerSupport.addExceptionPolicy(key, policy);
                }
            }
        }
    }

    protected static ExceptionPolicy toExceptionPolicy(OnExceptionDefinition exceptionType) {
        return new ExceptionPolicy(exceptionType);
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

    public List<OnExceptionDefinition> getErrorHandlers(RouteContext routeContext) {
        return onExceptions.get(routeContext);
    }

    public void setErrorHandlers(RouteContext routeContext, List<OnExceptionDefinition> exceptions) {
        this.onExceptions.put(routeContext, exceptions);
    }

    /**
     * Sets the exception policy to use
     */
    public ErrorHandlerBuilderSupport exceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        setExceptionPolicyStrategy(exceptionPolicyStrategy);
        return this;
    }

    public ExceptionPolicyStrategy getExceptionPolicyStrategy() {
        return exceptionPolicyStrategy;
    }

    public void setExceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        ObjectHelper.notNull(exceptionPolicyStrategy, "ExceptionPolicyStrategy");
        this.exceptionPolicyStrategy = exceptionPolicyStrategy;
    }
    
    /**
     * Remove the OnExceptionList by look up the route id from the ErrorHandlerBuilder internal map
     * @param id the route id
     * @return true if the route context is found and removed
     */
    public boolean removeOnExceptionList(String id) {
        for (RouteContext routeContext : onExceptions.keySet()) {
            if (routeContext.getRouteId().equals(id)) {
                onExceptions.remove(routeContext);
                return true;
            }
        }
        return false;
    }

    public Map<RouteContext, List<OnExceptionDefinition>> getOnExceptions() {
        return onExceptions;
    }
}
