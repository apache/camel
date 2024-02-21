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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default error handler
 */
public class DefaultErrorHandler extends RedeliveryErrorHandler {

    private static final CamelLogger DEFAULT_LOGGER
            = new CamelLogger(LoggerFactory.getLogger(DefaultErrorHandler.class), LoggingLevel.ERROR);

    /**
     * Creates the default error handler.
     *
     * @param camelContext                 the camel context
     * @param output                       outer processor that should use this default error handler
     * @param logger                       logger to use for logging failures and redelivery attempts
     * @param redeliveryProcessor          an optional processor to run before redelivery attempt
     * @param redeliveryPolicy             policy for redelivery
     * @param retryWhile                   retry while
     * @param executorService              the {@link java.util.concurrent.ScheduledExecutorService} to be used for
     *                                     redelivery thread pool. Can be <tt>null</tt>.
     * @param onPrepareProcessor           a custom {@link org.apache.camel.Processor} to prepare the
     *                                     {@link org.apache.camel.Exchange} before handled by the failure processor /
     *                                     dead letter channel.
     * @param onExceptionOccurredProcessor a custom {@link org.apache.camel.Processor} to process the
     *                                     {@link org.apache.camel.Exchange} just after an exception was thrown.
     */
    public DefaultErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger, Processor redeliveryProcessor,
                               RedeliveryPolicy redeliveryPolicy, Predicate retryWhile,
                               ScheduledExecutorService executorService, Processor onPrepareProcessor,
                               Processor onExceptionOccurredProcessor) {

        super(camelContext, output, logger != null ? logger : DEFAULT_LOGGER, redeliveryProcessor, redeliveryPolicy, null, null,
              true, false, false, retryWhile,
              executorService, onPrepareProcessor, onExceptionOccurredProcessor);
    }

    private DefaultErrorHandler(Logger log) {
        // used for eager loading
        super(log);
        SimpleTask dummy = new SimpleTask();
        log.trace("Loaded {}", dummy.getClass().getName());
        RedeliveryTask dummy2 = new RedeliveryTask();
        log.trace("Loaded {}", dummy2.getClass().getName());
    }

    @Override
    public ErrorHandler clone(Processor output) {
        DefaultErrorHandler answer = new DefaultErrorHandler(
                camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, retryWhilePolicy, executorService,
                onPrepareProcessor, onExceptionProcessor);
        // shallow clone is okay as we do not mutate these
        if (exceptionPolicies != null) {
            answer.exceptionPolicies = exceptionPolicies;
        }
        return answer;
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
