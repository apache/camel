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
package org.apache.camel.model.errorhandler;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Route;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.support.CamelContextHelper;

public final class ErrorHandlerHelper {

    public static final String DEFAULT_ERROR_HANDLER_BUILDER = "CamelDefaultErrorHandlerBuilder";

    private ErrorHandlerHelper() {
    }

    /**
     * Lookup the error handler by the given ref
     *
     * @param  route     the route
     * @param  ref       reference id for the error handler
     * @param  mandatory whether the error handler must exists, if not a {@link org.apache.camel.NoSuchBeanException} is
     *                   thrown
     * @return           the error handler
     */
    public static ErrorHandlerFactory lookupErrorHandlerFactory(Route route, String ref, boolean mandatory) {
        ErrorHandlerFactory source;
        ErrorHandlerFactory answer = null;
        CamelContext camelContext = route.getCamelContext();

        // if the ref is the default then we do not have any explicit error
        // handler configured
        // if that is the case then use error handlers configured on the route,
        // as for instance
        // the transacted error handler could have been configured on the route
        // so we should use that one
        if (!isErrorHandlerFactoryConfigured(ref)) {
            // see if there has been configured a error handler builder on the route
            source = route.getErrorHandlerFactory();
            // check if its also a ref with no error handler configuration like me
            if (source instanceof RefErrorHandlerDefinition) {
                RefErrorHandlerDefinition other = (RefErrorHandlerDefinition) source;
                String otherRef = other.getRef();
                if (!isErrorHandlerFactoryConfigured(otherRef)) {
                    // the other has also no explicit error handler configured
                    // then fallback to the handler
                    // configured on the parent camel context
                    answer = lookupErrorHandlerFactory(camelContext);
                }
                if (answer == null) {
                    // the other has also no explicit error handler configured
                    // then fallback to the default error handler
                    // otherwise we could recursive loop forever (triggered by
                    // createErrorHandler method)
                    answer = ((ModelCamelContext) camelContext).getModelReifierFactory().createDefaultErrorHandler();
                }
                // inherit the error handlers from the other as they are to be
                // shared
                // this is needed by camel-spring when none error handler has
                // been explicit configured
                route.addErrorHandlerFactoryReference(source, answer);
            }
        } else {
            // use specific configured error handler
            if (mandatory) {
                answer = CamelContextHelper.mandatoryLookup(camelContext, ref, ErrorHandlerFactory.class);
            } else {
                answer = CamelContextHelper.lookup(camelContext, ref, ErrorHandlerFactory.class);
            }
        }

        return answer;
    }

    protected static ErrorHandlerFactory lookupErrorHandlerFactory(CamelContext camelContext) {
        ErrorHandlerFactory answer = camelContext.getCamelContextExtension().getErrorHandlerFactory();
        if (answer instanceof RefErrorHandlerDefinition) {
            RefErrorHandlerDefinition other = (RefErrorHandlerDefinition) answer;
            String otherRef = other.getRef();
            if (isErrorHandlerFactoryConfigured(otherRef)) {
                answer = CamelContextHelper.lookup(camelContext, otherRef, ErrorHandlerFactory.class);
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
     * Can be used to test if none has been configured and then install a custom error handler builder replacing the
     * default error handler (that would have been used as fallback otherwise). <br/>
     * This is for instance used by the transacted policy to setup a TransactedErrorHandlerBuilder in camel-spring.
     */
    public static boolean isErrorHandlerFactoryConfigured(String ref) {
        return !RefErrorHandlerDefinition.DEFAULT_ERROR_HANDLER_BUILDER.equals(ref);
    }

}
