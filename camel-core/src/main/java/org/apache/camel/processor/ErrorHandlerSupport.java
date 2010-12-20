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
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.exceptionpolicy.DefaultExceptionPolicyStrategy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyKey;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Support class for {@link ErrorHandler} implementations.
 *
 * @version $Revision$
 */
public abstract class ErrorHandlerSupport extends ServiceSupport implements ErrorHandler {

    protected final transient Log log = LogFactory.getLog(getClass());

    private final Map<ExceptionPolicyKey, OnExceptionDefinition> exceptionPolicies = new LinkedHashMap<ExceptionPolicyKey, OnExceptionDefinition>();
    private ExceptionPolicyStrategy exceptionPolicy = createDefaultExceptionPolicyStrategy();

    public void addExceptionPolicy(OnExceptionDefinition exceptionType) {
        Processor processor = exceptionType.getErrorHandler();
        addChildService(processor);

        List<Class> list = exceptionType.getExceptionClasses();

        for (Class clazz : list) {
            RouteDefinition route = ProcessorDefinitionHelper.getRoute(exceptionType);
            String routeId = route != null ? route.getId() : null;
            ExceptionPolicyKey key = new ExceptionPolicyKey(routeId, clazz, exceptionType.getOnWhen());
            exceptionPolicies.put(key, exceptionType);
        }
    }

    /**
     * Attempts to invoke the handler for this particular exception if one is available
     */
    protected boolean customProcessorForException(Exchange exchange, Throwable exception) throws Exception {
        OnExceptionDefinition policy = getExceptionPolicy(exchange, exception);
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
     * Attempts to find the best suited {@link OnExceptionDefinition} to be used for handling the given thrown exception.
     *
     * @param exchange  the exchange
     * @param exception the exception that was thrown
     * @return the best exception type to handle this exception, <tt>null</tt> if none found.
     */
    protected OnExceptionDefinition getExceptionPolicy(Exchange exchange, Throwable exception) {
        if (exceptionPolicy == null) {
            throw new IllegalStateException("The exception policy has not been set");
        }

        return exceptionPolicy.getExceptionPolicy(exceptionPolicies, exchange, exception);
    }

    /**
     * Sets the strategy to use for resolving the {@link OnExceptionDefinition} to use
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

    /**
     * Whether this error handler supports transacted exchanges or not.
     */
    public abstract boolean supportTransacted();

    /**
     * Gets the output
     */
    public abstract Processor getOutput();

}
