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

import org.apache.camel.Processor;
import org.apache.camel.processor.DefaultErrorHandler;
import org.apache.camel.processor.ErrorHandlerSupport;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * The default error handler builder.
 *
 * @version $Revision$
 */
public class DefaultErrorHandlerBuilder extends ErrorHandlerBuilderSupport {

    private ExceptionPolicyStrategy exceptionPolicyStrategy = ErrorHandlerSupport.createDefaultExceptionPolicyStrategy();

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) {
        DefaultErrorHandler answer = new DefaultErrorHandler(processor, exceptionPolicyStrategy);
        configure(answer);
        return answer;
    }

    /**
     * Sets the exception policy to use
     */
    public DefaultErrorHandlerBuilder exceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        setExceptionPolicyStrategy(exceptionPolicyStrategy);
        return this;
    }

    /**
     * Sets the exception policy strategy to use for resolving the {@link org.apache.camel.model.OnExceptionDefinition}
     * to use for a given thrown exception
     */
    public ExceptionPolicyStrategy getExceptionPolicyStrategy() {
        return exceptionPolicyStrategy;
    }

    public void setExceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        this.exceptionPolicyStrategy = exceptionPolicyStrategy;
    }

}