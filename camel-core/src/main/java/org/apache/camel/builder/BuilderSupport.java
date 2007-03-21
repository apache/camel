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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.processor.LoggingLevel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for implementation inheritance
 *
 * @version $Revision: $
 */
public abstract class BuilderSupport<E extends Exchange> {
    private ErrorHandlerBuilder<E> errorHandlerBuilder;

    protected BuilderSupport() {
    }

    protected BuilderSupport(BuilderSupport<E> parent) {
        if (parent.errorHandlerBuilder != null) {
            this.errorHandlerBuilder = parent.errorHandlerBuilder.copy();
        }
    }

    // Builder methods
    //-------------------------------------------------------------------------

    /**
     * Returns a predicate and value builder for headers on an exchange
     */
    public ValueBuilder<E> header(String name) {
        Expression<E> expression = ExpressionBuilder.headerExpression(name);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    public ValueBuilder<E> body() {
        Expression<E> expression = ExpressionBuilder.bodyExpression();
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a specific type
     */
    public <T> ValueBuilder<E> bodyAs(Class<T> type) {
        Expression<E> expression = ExpressionBuilder.bodyExpression(type);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an exchange
     */
    public ValueBuilder<E> outBody() {
        Expression<E> expression = ExpressionBuilder.bodyExpression();
        return new ValueBuilder<E>(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a specific type
     */
    public <T> ValueBuilder<E> outBody(Class<T> type) {
        Expression<E> expression = ExpressionBuilder.bodyExpression(type);
        return new ValueBuilder<E>(expression);
    }

    /**
     * Creates a disabled error handler for removing the default error handler
     */
    public NoErrorHandlerBuilder<E> noErrorHandler() {
        return new NoErrorHandlerBuilder<E>();
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler() {
        return new LoggingErrorHandlerBuilder<E>();
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler(String log) {
        return loggingErrorHandler(LogFactory.getLog(log));
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler(Log log) {
        return new LoggingErrorHandlerBuilder<E>(log);
    }

    /**
     * Creates an error handler which just logs errors
     */
    public LoggingErrorHandlerBuilder<E> loggingErrorHandler(Log log, LoggingLevel level) {
        return new LoggingErrorHandlerBuilder<E>(log, level);
    }

    /*
    public DeadLetterChannelBuilder<E> deadLetterChannel() {
        return new DeadLetterChannelBuilder<E>();
    }
    */

    // Properties
    //-------------------------------------------------------------------------

    public ErrorHandlerBuilder<E> getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = new DeadLetterChannelBuilder<E>();
        }
        return errorHandlerBuilder;
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder<E> errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }
}
