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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.ErrorHandlerSupport;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class for builders of error handling.
 *
 * @version 
 */
public abstract class ErrorHandlerBuilderSupport implements ErrorHandlerBuilder {
    private Map<RouteContext, List<OnExceptionDefinition>> onExceptions = new HashMap<RouteContext, List<OnExceptionDefinition>>();
    private ExceptionPolicyStrategy exceptionPolicyStrategy;

    public void addErrorHandlers(RouteContext routeContext, OnExceptionDefinition exception) {
        // only add if we not already have it
        List<OnExceptionDefinition> list = onExceptions.get(routeContext);
        if (list == null) {
            list = new ArrayList<OnExceptionDefinition>();
            onExceptions.put(routeContext, list);
        }
        if (!list.contains(exception)) {
            list.add(exception);
        }
    }

    protected void cloneBuilder(ErrorHandlerBuilderSupport other) {
        if (!onExceptions.isEmpty()) {
            Map<RouteContext, List<OnExceptionDefinition>> copy = new HashMap<RouteContext, List<OnExceptionDefinition>>(onExceptions);
            other.onExceptions = copy;
        }
        other.exceptionPolicyStrategy = exceptionPolicyStrategy;
    }

    public void configure(RouteContext routeContext, ErrorHandler handler) {
        if (handler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport handlerSupport = (ErrorHandlerSupport) handler;

            List<OnExceptionDefinition> list = onExceptions.get(routeContext);
            if (list != null) {
                for (OnExceptionDefinition exception : list) {
                    handlerSupport.addExceptionPolicy(routeContext, exception);
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
            if (getRouteId(routeContext).equals(id)) {
                onExceptions.remove(routeContext);
                return true;
            }
        }
        return false;
    }
    
    protected String getRouteId(RouteContext routeContext) {
        CamelContext context = routeContext.getCamelContext();
        if (context != null) {
            return routeContext.getRoute().idOrCreate(context.getNodeIdFactory());
        } else {
            return routeContext.getRoute().getId();
        }
    }
}
