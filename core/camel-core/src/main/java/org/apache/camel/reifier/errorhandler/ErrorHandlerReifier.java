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
package org.apache.camel.reifier.errorhandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.ErrorHandlerBuilderSupport;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.errorhandler.ExceptionPolicy;
import org.apache.camel.processor.errorhandler.ExceptionPolicy.RedeliveryOption;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public abstract class ErrorHandlerReifier<T extends ErrorHandlerBuilderSupport> {

    private static final Map<Class<?>, Function<ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>>> ERROR_HANDLERS;
    static {
        Map<Class<?>, Function<ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>>> map = new HashMap<>();
        map.put(DeadLetterChannelBuilder.class, DeadLetterChannelReifier::new);
        map.put(DefaultErrorHandlerBuilder.class, DefaultErrorHandlerReifier::new);
        map.put(ErrorHandlerBuilderRef.class, ErrorHandlerRefReifier::new);
        map.put(NoErrorHandlerBuilder.class, NoErrorHandlerReifier::new);
        ERROR_HANDLERS = map;
    }

    public static void registerReifier(Class<?> errorHandlerClass, Function<ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>> creator) {
        ERROR_HANDLERS.put(errorHandlerClass, creator);
    }

    protected T definition;

    /**
     * Utility classes should not have a public constructor.
     */
    ErrorHandlerReifier(T definition) {
        this.definition = definition;
    }

    public static ErrorHandlerReifier<? extends ErrorHandlerFactory> reifier(ErrorHandlerFactory definition) {
        Function<ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>> reifier = ERROR_HANDLERS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(definition);
        } else if (definition instanceof ErrorHandlerBuilderSupport) {
            return new ErrorHandlerReifier<ErrorHandlerBuilderSupport>((ErrorHandlerBuilderSupport) definition) {
                @Override
                public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
                    return definition.createErrorHandler(routeContext, processor);
                }
            };
        } else {
            throw new IllegalStateException("Unsupported definition: " + definition);
        }
    }

    public static ExceptionPolicy createExceptionPolicy(OnExceptionDefinition def, RouteContext routeContext) {
        return new ExceptionPolicy(
                def.getId(),
                CamelContextHelper.getRouteId(def),
                def.getUseOriginalMessagePolicy() != null && def.getUseOriginalMessagePolicy(),
                ObjectHelper.isNotEmpty(def.getOutputs()),
                def.getHandledPolicy(),
                def.getContinuedPolicy(),
                def.getRetryWhilePolicy(),
                def.getOnRedelivery(),
                def.getOnExceptionOccurred(),
                def.getRedeliveryPolicyRef(),
                getRedeliveryPolicy(def.getRedeliveryPolicyType()),
                def.getExceptions());
    }

    private static Map<RedeliveryOption, String> getRedeliveryPolicy(RedeliveryPolicyDefinition definition) {
        if (definition == null) {
            return null;
        }
        Map<RedeliveryOption, String> policy = new HashMap<>();
        setoption(policy, RedeliveryOption.maximumRedeliveries, definition.getMaximumRedeliveries());
        setoption(policy, RedeliveryOption.redeliveryDelay, definition.getRedeliveryDelay());
        setoption(policy, RedeliveryOption.asyncDelayedRedelivery, definition.getAsyncDelayedRedelivery());
        setoption(policy, RedeliveryOption.backOffMultiplier, definition.getBackOffMultiplier());
        setoption(policy, RedeliveryOption.useExponentialBackOff, definition.getUseExponentialBackOff());
        setoption(policy, RedeliveryOption.collisionAvoidanceFactor, definition.getCollisionAvoidanceFactor());
        setoption(policy, RedeliveryOption.useCollisionAvoidance, definition.getUseCollisionAvoidance());
        setoption(policy, RedeliveryOption.maximumRedeliveryDelay, definition.getMaximumRedeliveryDelay());
        setoption(policy, RedeliveryOption.retriesExhaustedLogLevel, definition.getRetriesExhaustedLogLevel());
        setoption(policy, RedeliveryOption.retryAttemptedLogLevel, definition.getRetryAttemptedLogLevel());
        setoption(policy, RedeliveryOption.retryAttemptedLogInterval, definition.getRetryAttemptedLogInterval());
        setoption(policy, RedeliveryOption.logRetryAttempted, definition.getLogRetryAttempted());
        setoption(policy, RedeliveryOption.logStackTrace, definition.getLogStackTrace());
        setoption(policy, RedeliveryOption.logRetryStackTrace, definition.getLogRetryStackTrace());
        setoption(policy, RedeliveryOption.logHandled, definition.getLogHandled());
        setoption(policy, RedeliveryOption.logNewException, definition.getLogNewException());
        setoption(policy, RedeliveryOption.logContinued, definition.getLogContinued());
        setoption(policy, RedeliveryOption.logExhausted, definition.getLogExhausted());
        setoption(policy, RedeliveryOption.logExhaustedMessageHistory, definition.getLogExhaustedMessageHistory());
        setoption(policy, RedeliveryOption.logExhaustedMessageBody, definition.getLogExhaustedMessageBody());
        setoption(policy, RedeliveryOption.disableRedelivery, definition.getDisableRedelivery());
        setoption(policy, RedeliveryOption.delayPattern, definition.getDelayPattern());
        setoption(policy, RedeliveryOption.allowRedeliveryWhileStopping, definition.getAllowRedeliveryWhileStopping());
        setoption(policy, RedeliveryOption.exchangeFormatterRef, definition.getExchangeFormatterRef());
        return policy;
    }

    private static void setoption(Map<RedeliveryOption, String> policy, RedeliveryOption option, Object value) {
        if (value != null) {
            policy.put(option, value.toString());
        }
    }

    /**
     * Creates the error handler
     *
     * @param routeContext the route context
     * @param processor the outer processor
     * @return the error handler
     * @throws Exception is thrown if the error handler could not be created
     */
    public abstract Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception;

    public void configure(RouteContext routeContext, ErrorHandler handler) {
        if (handler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport handlerSupport = (ErrorHandlerSupport) handler;

            List<OnExceptionDefinition> list = definition.getOnExceptions().get(routeContext);
            if (list != null) {
                for (OnExceptionDefinition exception : list) {
                    ErrorHandlerBuilderSupport.addExceptionPolicy(handlerSupport, routeContext, exception);
                }
            }
        }
        if (handler instanceof RedeliveryErrorHandler) {
            boolean original = ((RedeliveryErrorHandler) handler).isUseOriginalMessagePolicy();
            if (original) {
                // ensure allow original is turned on
                routeContext.setAllowUseOriginalMessage(true);
            }
        }
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

}
