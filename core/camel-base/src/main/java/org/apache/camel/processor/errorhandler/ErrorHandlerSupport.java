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
package org.apache.camel.processor.errorhandler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.support.ChildServiceSupport;

/**
 * Support class for {@link ErrorHandler} implementations.
 */
public abstract class ErrorHandlerSupport extends ChildServiceSupport implements ErrorHandler {

    protected final Map<ExceptionPolicyKey, ExceptionPolicy> exceptionPolicies = new LinkedHashMap<>();
    protected ExceptionPolicyStrategy exceptionPolicy = createDefaultExceptionPolicyStrategy();

    public void addErrorHandler(Processor errorHandler) {
        addChildService(errorHandler);
    }

    public void addExceptionPolicy(ExceptionPolicyKey key, ExceptionPolicy policy) {
        exceptionPolicies.put(key, policy);
    }

    /**CamelContextHelper
     * Attempts to find the best suited {@link ExceptionPolicy} to be used for handling the given thrown exception.
     *
     * @param exchange  the exchange
     * @param exception the exception that was thrown
     * @return the best exception type to handle this exception, <tt>null</tt> if none found.
     */
    protected ExceptionPolicy getExceptionPolicy(Exchange exchange, Throwable exception) {
        if (exceptionPolicy == null) {
            throw new IllegalStateException("The exception policy has not been set");
        }

        ExceptionPolicyKey key = exceptionPolicy.getExceptionPolicy(exceptionPolicies.keySet(), exchange, exception);
        return key != null ? exceptionPolicies.get(key) : null;
    }

    /**
     * Sets the strategy to use for resolving the {@link ExceptionPolicy} to use
     * for handling thrown exceptions.
     */
    public void setExceptionPolicy(ExceptionPolicyStrategy exceptionPolicy) {
        if (exceptionPolicy != null) {
            this.exceptionPolicy = exceptionPolicy;
        }
    }

    /**
     * Creates the default exception policy strategy to use.
     */
    public static ExceptionPolicyStrategy createDefaultExceptionPolicyStrategy() {
        return new DefaultExceptionPolicyStrategy();
    }

    /**
     * Whether this error handler supports transacted exchanges or not.
     */
    public abstract boolean supportTransacted();

    /**
     * Whether this error handler handles exhausted errors by moving the exchange to a dead letter channel.
     */
    public boolean isDeadLetterChannel() {
        return false;
    }

    /**
     * Gets the output
     */
    public abstract Processor getOutput();

}
