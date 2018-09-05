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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.CamelLogger;

/**
 * Implements a <a
 * href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
 * Channel</a> after attempting to redeliver the message using the
 * {@link RedeliveryPolicy}
 *
 * @version 
 */
public class DeadLetterChannel extends RedeliveryErrorHandler {

    /**
     * Creates the dead letter channel.
     *
     * @param camelContext                  the camel context
     * @param output                        outer processor that should use this dead letter channel
     * @param logger                        logger to use for logging failures and redelivery attempts
     * @param redeliveryProcessor           an optional processor to run before redelivery attempt
     * @param redeliveryPolicy              policy for redelivery
     * @param exceptionPolicyStrategy       strategy for onException handling
     * @param deadLetter                    the failure processor to send failed exchanges to
     * @param deadLetterUri                 an optional uri for logging purpose
     * @param deadLetterHandleException     whether dead letter channel should handle (and ignore) exceptions which may be thrown during sending the message to the dead letter endpoint
     * @param useOriginalBodyPolicy         should the original IN body be moved to the dead letter queue or the current exchange IN body?
     * @param retryWhile                    retry while
     * @param executorService               the {@link java.util.concurrent.ScheduledExecutorService} to be used for redelivery thread pool. Can be <tt>null</tt>.
     * @param onPrepareProcessor            a custom {@link org.apache.camel.Processor} to prepare the {@link org.apache.camel.Exchange} before
     *                                      handled by the failure processor / dead letter channel.
     * @param onExceptionOccurredProcessor  a custom {@link org.apache.camel.Processor} to process the {@link org.apache.camel.Exchange} just after an exception was thrown.
     */
    public DeadLetterChannel(CamelContext camelContext, Processor output, CamelLogger logger, Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy,
            ExceptionPolicyStrategy exceptionPolicyStrategy, Processor deadLetter, String deadLetterUri, boolean deadLetterHandleException,
            boolean useOriginalBodyPolicy, Predicate retryWhile, ScheduledExecutorService executorService, Processor onPrepareProcessor, Processor onExceptionOccurredProcessor) {

        super(camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, deadLetter, deadLetterUri, deadLetterHandleException,
                useOriginalBodyPolicy, retryWhile, executorService, onPrepareProcessor, onExceptionOccurredProcessor);
        setExceptionPolicy(exceptionPolicyStrategy);
    }

    @Override
    public String toString() {
        if (output == null) {
            // if no output then don't do any description
            return "";
        }
        return "DeadLetterChannel[" + output + ", " + (deadLetterUri != null ? deadLetterUri : deadLetter) + "]";
    }

    @Override
    protected boolean isRunAllowedOnPreparingShutdown() {
        // allow to run as we want to move the message eto DLC, instead of rejecting the message
        return true;
    }

    @Override
    public boolean isDeadLetterChannel() {
        return true;
    }
}
