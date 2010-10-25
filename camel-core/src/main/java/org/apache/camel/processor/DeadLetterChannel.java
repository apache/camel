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
 * Implements a <a
 * href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
 * Channel</a> after attempting to redeliver the message using the
 * {@link RedeliveryPolicy}
 *
 * @version $Revision$
 */
public class DeadLetterChannel extends RedeliveryErrorHandler {

    /**
     * Creates the dead letter channel.
     *
     * @param camelContext              the camel context
     * @param output                    outer processor that should use this dead letter channel
     * @param logger                    logger to use for logging failures and redelivery attempts
     * @param redeliveryProcessor       an optional processor to run before redelivery attempt
     * @param redeliveryPolicy          policy for redelivery
     * @param handledPolicy             policy for handling failed exception that are moved to the dead letter queue
     * @param exceptionPolicyStrategy   strategy for onException handling
     * @param deadLetter                the failure processor to send failed exchanges to
     * @param deadLetterUri             an optional uri for logging purpose
     * @param useOriginalBodyPolicy     should the original IN body be moved to the dead letter queue or the current exchange IN body?
     * @param retryWhile                retry while
     */
    public DeadLetterChannel(CamelContext camelContext, Processor output, Logger logger, Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy,
                             Predicate handledPolicy, ExceptionPolicyStrategy exceptionPolicyStrategy, Processor deadLetter,
                             String deadLetterUri, boolean useOriginalBodyPolicy, Predicate retryWhile) {
        super(camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, handledPolicy, deadLetter, deadLetterUri, useOriginalBodyPolicy, retryWhile);
        setExceptionPolicy(exceptionPolicyStrategy);
    }

    public void process(Exchange exchange) throws Exception {
        // just to let the stack trace reveal that this is a dead letter channel
        super.process(exchange);
    }

    @Override
    public String toString() {
        if (output == null) {
            // if no output then don't do any description
            return "";
        }
        return "DeadLetterChannel[" + output + ", " + (deadLetterUri != null ? deadLetterUri : deadLetter) + "]";
    }

}
