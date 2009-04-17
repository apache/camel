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

import org.apache.camel.Processor;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a proxy to an error handler builder which is resolved by named reference
 *
 * @version $Revision$
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
            handler = lookupErrorHandlerBuilder(routeContext);
        }
        return handler.createErrorHandler(routeContext, processor);
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
    public boolean isErrorHandlerBuilderConfigued() {
        return !DEFAULT_ERROR_HANDLER_BUILDER.equals(getRef());
    }

    public boolean supportTransacted() {
        return supportTransacted;
    }

    public ErrorHandlerBuilder lookupErrorHandlerBuilder(RouteContext routeContext) {
        if (handler == null) {
            // if the ref is the default then the we do not have any explicit error handler configured
            // if that is the case then use error handlers configured on the route, as for instance
            // the transacted error handler could have been configured on the route so we should use that one
            if (!isErrorHandlerBuilderConfigued()) {
                // see if there has been configured a route builder on the route
                handler = routeContext.getRoute().getErrorHandlerBuilder();
                if (handler == null) {
                    handler = routeContext.lookup(routeContext.getRoute().getErrorHandlerRef(), ErrorHandlerBuilder.class);
                }
                if (handler == null) {
                    // fallback to the default error handler if none configured on the route
                    handler = new DefaultErrorHandlerBuilder();
                }
                // check if its also a ref with no error handler configuration like me
                if (handler instanceof ErrorHandlerBuilderRef) {
                    ErrorHandlerBuilderRef other = (ErrorHandlerBuilderRef) handler;
                    if (!other.isErrorHandlerBuilderConfigued()) {
                        // the other has also no explict error handler configured then fallback to the default error handler
                        // otherwise we could recursive loop forever (triggered by createErrorHandler method)
                        handler = new DefaultErrorHandlerBuilder();
                    }
                }
            } else {
                // use specific configured error handler
                handler = routeContext.lookup(ref, ErrorHandlerBuilder.class);
            }

            ObjectHelper.notNull(handler, "error handler '" + ref + "'");

            // configure if the handler support transacted
            supportTransacted = handler.supportTransacted();

            List<OnExceptionDefinition> list = getErrorHandlers();
            for (OnExceptionDefinition exceptionType : list) {
                handler.addErrorHandlers(exceptionType);
            }
        }
        return handler;
    }

    public String getRef() {
        return ref;
    }

    @Override
    public String toString() {
        return "ErrorHandlerBuilderRef[" + ref + "]";
    }
}
