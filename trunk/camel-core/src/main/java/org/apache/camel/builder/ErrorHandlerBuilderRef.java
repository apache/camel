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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a proxy to an error handler builder which is resolved by named reference
 *
 * @version 
 */
public class ErrorHandlerBuilderRef extends ErrorHandlerBuilderSupport {
    public static final String DEFAULT_ERROR_HANDLER_BUILDER = "CamelDefaultErrorHandlerBuilder";
    private final String ref;
    private ErrorHandlerBuilder handler;
    private boolean supportTransacted;

    public ErrorHandlerBuilderRef(String ref) {
        this.ref = ref;
    }

    @Override
    public void addErrorHandlers(OnExceptionDefinition exception) {
        if (handler != null) {
            handler.addErrorHandlers(exception);
        }
        super.addErrorHandlers(exception);
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        if (handler == null) {
            handler = createErrorHandler(routeContext);
        }
        return handler.createErrorHandler(routeContext, processor);
    }

    public boolean supportTransacted() {
        return supportTransacted;
    }

    /**
     * Lookup the error handler by the given ref
     *
     * @param routeContext the route context
     * @param ref          reference id for the error handler
     * @return the error handler
     */
    public static ErrorHandlerBuilder lookupErrorHandlerBuilder(RouteContext routeContext, String ref) {
        ErrorHandlerBuilder answer;

        // if the ref is the default then we do not have any explicit error handler configured
        // if that is the case then use error handlers configured on the route, as for instance
        // the transacted error handler could have been configured on the route so we should use that one
        if (!isErrorHandlerBuilderConfigured(ref)) {
            // see if there has been configured a route builder on the route
            answer = routeContext.getRoute().getErrorHandlerBuilder();
            if (answer == null && routeContext.getRoute().getErrorHandlerRef() != null) {
                answer = routeContext.lookup(routeContext.getRoute().getErrorHandlerRef(), ErrorHandlerBuilder.class);
            }
            if (answer == null) {
                // fallback to the default error handler if none configured on the route
                answer = new DefaultErrorHandlerBuilder();
            }
            // check if its also a ref with no error handler configuration like me
            if (answer instanceof ErrorHandlerBuilderRef) {
                ErrorHandlerBuilderRef other = (ErrorHandlerBuilderRef) answer;
                String otherRef = other.getRef();
                if (!isErrorHandlerBuilderConfigured(otherRef)) {
                    // the other has also no explicit error handler configured then fallback to the handler
                    // configured on the parent camel context
                    answer = lookupErrorHandlerBuilder(routeContext.getCamelContext());
                }
                if (answer == null) {
                    // the other has also no explicit error handler configured then fallback to the default error handler
                    // otherwise we could recursive loop forever (triggered by createErrorHandler method)
                    answer = new DefaultErrorHandlerBuilder();
                }
                // inherit the error handlers from the other as they are to be shared
                // this is needed by camel-spring when none error handler has been explicit configured
                answer.setErrorHandlers(other.getErrorHandlers());
            }
        } else {
            // use specific configured error handler
            answer = routeContext.lookup(ref, ErrorHandlerBuilder.class);
            if (answer == null) {
                throw new IllegalArgumentException("ErrorHandlerBuilder with id " + ref + " not found in registry.");
            }
        }

        return answer;
    }

    protected static ErrorHandlerBuilder lookupErrorHandlerBuilder(CamelContext camelContext) {
        ErrorHandlerBuilder answer = camelContext.getErrorHandlerBuilder();
        if (answer instanceof ErrorHandlerBuilderRef) {
            ErrorHandlerBuilderRef other = (ErrorHandlerBuilderRef) answer;
            String otherRef = other.getRef();
            if (isErrorHandlerBuilderConfigured(otherRef)) {
                answer = camelContext.getRegistry().lookup(otherRef, ErrorHandlerBuilder.class);
                if (answer == null) {
                    throw new IllegalArgumentException("ErrorHandlerBuilder with id " + otherRef + " not found in registry.");
                }
            }
        }

        return answer;
    }



    /**
     * Returns whether a specific error handler builder has been configured or not.
     * <p/>
     * Can be used to test if none has been configured and then install a custom error handler builder
     * replacing the default error handler (that would have been used as fallback otherwise).
     * <br/>
     * This is for instance used by the transacted policy to setup a TransactedErrorHandlerBuilder
     * in camel-spring.
     */
    public static boolean isErrorHandlerBuilderConfigured(String ref) {
        return !DEFAULT_ERROR_HANDLER_BUILDER.equals(ref);
    }

    public String getRef() {
        return ref;
    }

    public ErrorHandlerBuilder getHandler() {
        return handler;
    }

    private ErrorHandlerBuilder createErrorHandler(RouteContext routeContext) {
        handler = lookupErrorHandlerBuilder(routeContext, getRef());
        ObjectHelper.notNull(handler, "error handler '" + ref + "'");

        // configure if the handler support transacted
        supportTransacted = handler.supportTransacted();

        List<OnExceptionDefinition> list = getErrorHandlers();
        for (OnExceptionDefinition exceptionType : list) {
            handler.addErrorHandlers(exceptionType);
        }
        return handler;
    }

    @Override
    public String toString() {
        return "ErrorHandlerBuilderRef[" + ref + "]";
    }
}
