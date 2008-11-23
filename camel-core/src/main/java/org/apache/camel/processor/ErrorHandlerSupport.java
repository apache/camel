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
package org.apache.camel.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.ExceptionType;
import org.apache.camel.processor.exceptionpolicy.DefaultExceptionPolicyStrategy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyKey;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;

/**
 * Support class for {@link ErrorHandler} implementations.
 *
 * @version $Revision$
 */
public abstract class ErrorHandlerSupport extends ServiceSupport implements ErrorHandler {
    private Map<ExceptionPolicyKey, ExceptionType> exceptionPolicies = new LinkedHashMap<ExceptionPolicyKey, ExceptionType>();
    private ExceptionPolicyStrategy exceptionPolicy = createDefaultExceptionPolicyStrategy();

    public void addExceptionPolicy(ExceptionType exceptionType) {
        Processor processor = exceptionType.getErrorHandler();
        addChildService(processor);

        List<Class> list = exceptionType.getExceptionClasses();

        for (Class clazz : list) {
            ExceptionPolicyKey key = new ExceptionPolicyKey(clazz, exceptionType.getOnWhen());
            exceptionPolicies.put(key, exceptionType);
        }
    }

    /**
     * Attempts to invoke the handler for this particular exception if one is available
     */
    protected boolean customProcessorForException(Exchange exchange, Throwable exception) throws Exception {
        ExceptionType policy = getExceptionPolicy(exchange, exception);
        if (policy != null) {
            Processor processor = policy.getErrorHandler();
            if (processor != null) {
                processor.process(exchange);
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to find the best suited {@link ExceptionType} to be used for handling the given thrown exception.
     *
     * @param exchange  the exchange
     * @param exception the exception that was thrown
     * @return the best exception type to handle this exception, <tt>null</tt> if none found.
     */
    protected ExceptionType getExceptionPolicy(Exchange exchange, Throwable exception) {
        if (exceptionPolicy == null) {
            throw new IllegalStateException("The exception policy has not been set");
        }

        return exceptionPolicy.getExceptionPolicy(exceptionPolicies, exchange, exception);
    }

    /**
     * Sets the strategy to use for resolving the {@link ExceptionType} to use
     * for handling thrown exceptions.
     */
    public void setExceptionPolicy(ExceptionPolicyStrategy exceptionPolicy) {
        this.exceptionPolicy = exceptionPolicy;
    }

    /**
     * Creates the default exception policy strategy to use.
     */
    public static ExceptionPolicyStrategy createDefaultExceptionPolicyStrategy() {
        return new DefaultExceptionPolicyStrategy();
    }

}
