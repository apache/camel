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
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * A builder of a <a href="http://camel.apache.org/error-handler.html">Error Handler</a>
 *
 * @version 
 */
public interface ErrorHandlerBuilder {

    /**
     * Creates the error handler interceptor
     *
     * @param routeContext the route context
     * @param processor the outer processor
     * @return the error handler
     * @throws Exception is thrown if the error handler could not be created
     */
    Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception;

    /**
     * Adds error handler for the given exception type
     *
     * @param exception  the exception to handle
     */
    void addErrorHandlers(OnExceptionDefinition exception);

    /**
     * Adds the error handlers for the given list of exception types
     *
     * @param exceptions  the list of exceptions to handle
     */
    void setErrorHandlers(List<OnExceptionDefinition> exceptions);

    /**
     * Gets the error handlers
     */
    List<OnExceptionDefinition> getErrorHandlers();

    /**
     * Gets the exception policy strategy
     */
    ExceptionPolicyStrategy getExceptionPolicyStrategy();

    /**
     * Sets the exception policy strategy to use for resolving the {@link org.apache.camel.model.OnExceptionDefinition}
     * to use for a given thrown exception
     *
     * @param exceptionPolicyStrategy  the exception policy strategy
     */
    void setExceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy);

    /**
     * Whether this error handler supports transacted exchanges.
     */
    boolean supportTransacted();

    /**
     * Configures the other error handler based on this error handler.
     *
     * @param handler the other error handler
     */
    void configure(ErrorHandler handler);
}
