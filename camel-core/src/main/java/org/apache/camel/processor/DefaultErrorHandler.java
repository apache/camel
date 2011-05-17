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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;

/**
 * Default error handler
 *
 * @version 
 */
public class DefaultErrorHandler extends RedeliveryErrorHandler {

    /**
     * Creates the default error handler.
     *
     * @param camelContext              the camel context
     * @param output                    outer processor that should use this default error handler
     * @param logger                    logger to use for logging failures and redelivery attempts
     * @param redeliveryProcessor       an optional processor to run before redelivery attempt
     * @param redeliveryPolicy          policy for redelivery
     * @param exceptionPolicyStrategy   strategy for onException handling
     * @param retryWhile                retry while
     * @param executorServiceRef        reference to a {@link java.util.concurrent.ScheduledExecutorService} to be used for redelivery thread pool. Can be <tt>null</tt>.
     */
    public DefaultErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger, Processor redeliveryProcessor,
            RedeliveryPolicy redeliveryPolicy, ExceptionPolicyStrategy exceptionPolicyStrategy, Predicate retryWhile, String executorServiceRef) {

        super(camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, null, null, false, retryWhile, executorServiceRef);
        setExceptionPolicy(exceptionPolicyStrategy);
    }

    public void process(Exchange exchange) throws Exception {
        // just to let the stacktrace reveal that this is a dead letter channel
        super.process(exchange);
    }

    @Override
    public String toString() {
        if (output == null) {
            // if no output then dont do any description
            return "";
        }
        return "DefaultErrorHandler[" + output + "]";
    }

}
