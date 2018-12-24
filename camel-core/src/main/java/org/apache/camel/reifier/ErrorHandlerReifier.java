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
package org.apache.camel.reifier;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.support.CamelContextHelper;

public final class ErrorHandlerReifier {
    
    /**
     * Utility classes should not have a public constructor.
     */
    private ErrorHandlerReifier() {
    }

    public static RedeliveryPolicy createRedeliveryPolicy(RedeliveryPolicyDefinition definition, CamelContext context, RedeliveryPolicy parentPolicy) {

        RedeliveryPolicy answer;
        if (parentPolicy != null) {
            answer = parentPolicy.copy();
        } else {
            answer = new RedeliveryPolicy();
        }

        try {

            // copy across the properties - if they are set
            if (definition.getMaximumRedeliveries() != null) {
                answer.setMaximumRedeliveries(CamelContextHelper.parseInteger(context, definition.getMaximumRedeliveries()));
            }
            if (definition.getRedeliveryDelay() != null) {
                answer.setRedeliveryDelay(CamelContextHelper.parseLong(context, definition.getRedeliveryDelay()));
            }
            if (definition.getAsyncDelayedRedelivery() != null) {
                answer.setAsyncDelayedRedelivery(CamelContextHelper.parseBoolean(context, definition.getAsyncDelayedRedelivery()));
            }
            if (definition.getRetriesExhaustedLogLevel() != null) {
                answer.setRetriesExhaustedLogLevel(definition.getRetriesExhaustedLogLevel());
            }
            if (definition.getRetryAttemptedLogLevel() != null) {
                answer.setRetryAttemptedLogLevel(definition.getRetryAttemptedLogLevel());
            }
            if (definition.getRetryAttemptedLogInterval() != null) {
                answer.setRetryAttemptedLogInterval(CamelContextHelper.parseInteger(context, definition.getRetryAttemptedLogInterval()));
            }
            if (definition.getBackOffMultiplier() != null) {
                answer.setBackOffMultiplier(CamelContextHelper.parseDouble(context, definition.getBackOffMultiplier()));
            }
            if (definition.getUseExponentialBackOff() != null) {
                answer.setUseExponentialBackOff(CamelContextHelper.parseBoolean(context, definition.getUseExponentialBackOff()));
            }
            if (definition.getCollisionAvoidanceFactor() != null) {
                answer.setCollisionAvoidanceFactor(CamelContextHelper.parseDouble(context, definition.getCollisionAvoidanceFactor()));
            }
            if (definition.getUseCollisionAvoidance() != null) {
                answer.setUseCollisionAvoidance(CamelContextHelper.parseBoolean(context, definition.getUseCollisionAvoidance()));
            }
            if (definition.getMaximumRedeliveryDelay() != null) {
                answer.setMaximumRedeliveryDelay(CamelContextHelper.parseLong(context, definition.getMaximumRedeliveryDelay()));
            }
            if (definition.getLogStackTrace() != null) {
                answer.setLogStackTrace(CamelContextHelper.parseBoolean(context, definition.getLogStackTrace()));
            }
            if (definition.getLogRetryStackTrace() != null) {
                answer.setLogRetryStackTrace(CamelContextHelper.parseBoolean(context, definition.getLogRetryStackTrace()));
            }
            if (definition.getLogHandled() != null) {
                answer.setLogHandled(CamelContextHelper.parseBoolean(context, definition.getLogHandled()));
            }
            if (definition.getLogNewException() != null) {
                answer.setLogNewException(CamelContextHelper.parseBoolean(context, definition.getLogNewException()));
            }
            if (definition.getLogContinued() != null) {
                answer.setLogContinued(CamelContextHelper.parseBoolean(context, definition.getLogContinued()));
            }
            if (definition.getLogRetryAttempted() != null) {
                answer.setLogRetryAttempted(CamelContextHelper.parseBoolean(context, definition.getLogRetryAttempted()));
            }
            if (definition.getLogExhausted() != null) {
                answer.setLogExhausted(CamelContextHelper.parseBoolean(context, definition.getLogExhausted()));
            }
            if (definition.getLogExhaustedMessageHistory() != null) {
                answer.setLogExhaustedMessageHistory(CamelContextHelper.parseBoolean(context, definition.getLogExhaustedMessageHistory()));
            }
            if (definition.getLogExhaustedMessageBody() != null) {
                answer.setLogExhaustedMessageBody(CamelContextHelper.parseBoolean(context, definition.getLogExhaustedMessageBody()));
            }
            if (definition.getDisableRedelivery() != null) {
                if (CamelContextHelper.parseBoolean(context, definition.getDisableRedelivery())) {
                    answer.setMaximumRedeliveries(0);
                }
            }
            if (definition.getDelayPattern() != null) {
                answer.setDelayPattern(CamelContextHelper.parseText(context, definition.getDelayPattern()));
            }
            if (definition.getAllowRedeliveryWhileStopping() != null) {
                answer.setAllowRedeliveryWhileStopping(CamelContextHelper.parseBoolean(context, definition.getAllowRedeliveryWhileStopping()));
            }
            if (definition.getExchangeFormatterRef() != null) {
                answer.setExchangeFormatterRef(CamelContextHelper.parseText(context, definition.getExchangeFormatterRef()));
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        return answer;
    }

    /**
     * Allows an exception handler to create a new redelivery policy for this exception type
     *
     * @param definition
     * @param context      the camel context
     * @param parentPolicy the current redelivery policy, is newer <tt>null</tt>
     * @return a newly created redelivery policy, or return the original policy if no customization is required
     *         for this exception handler.
     */
    public static RedeliveryPolicy createRedeliveryPolicy(OnExceptionDefinition definition, CamelContext context, RedeliveryPolicy parentPolicy) {
        if (definition.getRedeliveryPolicyRef() != null) {
            return CamelContextHelper.mandatoryLookup(context, definition.getRedeliveryPolicyRef(), RedeliveryPolicy.class);
        } else if (definition.getRedeliveryPolicyType() != null) {
            return createRedeliveryPolicy(definition.getRedeliveryPolicyType(), context, parentPolicy);
        } else if (!definition.getOutputs().isEmpty() && parentPolicy.getMaximumRedeliveries() != 0) {
            // if we have outputs, then do not inherit parent maximumRedeliveries
            // as you would have to explicit configure maximumRedeliveries on this onException to use it
            // this is the behavior Camel has always had
            RedeliveryPolicy answer = parentPolicy.copy();
            answer.setMaximumRedeliveries(0);
            return answer;
        } else {
            return parentPolicy;
        }
    }
}
