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

import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NamedNode;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.processor.errorhandler.ExceptionPolicy;
import org.apache.camel.processor.errorhandler.ExceptionPolicy.RedeliveryOption;
import org.apache.camel.processor.errorhandler.ExceptionPolicyKey;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.ErrorHandler;
import org.apache.camel.spi.Language;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;

public abstract class ErrorHandlerReifier<T extends ErrorHandlerFactory> extends AbstractReifier {

    private static final Map<Class<?>, BiFunction<Route, ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>>> ERROR_HANDLERS
            = new HashMap<>(0);

    protected final T definition;

    /**
     * Utility classes should not have a public constructor.
     */
    protected ErrorHandlerReifier(Route route, T definition) {
        super(route);
        this.definition = definition;
    }

    public static void registerReifier(
            Class<?> errorHandlerClass,
            BiFunction<Route, ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>> creator) {
        ERROR_HANDLERS.put(errorHandlerClass, creator);
    }

    public static ErrorHandlerReifier<? extends ErrorHandlerFactory> reifier(Route route, ErrorHandlerFactory definition) {
        ErrorHandlerReifier<? extends ErrorHandlerFactory> answer = null;
        if (!ERROR_HANDLERS.isEmpty()) {
            // custom take precedence
            BiFunction<Route, ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>> reifier
                    = ERROR_HANDLERS.get(definition.getClass());
            if (reifier != null) {
                answer = reifier.apply(route, definition);
            }
        }
        if (answer == null) {
            answer = coreReifier(route, definition);
        }
        if (answer == null) {
            throw new IllegalStateException("Unsupported definition: " + definition);
        }
        return answer;
    }

    private static ErrorHandlerReifier<? extends ErrorHandlerFactory> coreReifier(Route route, ErrorHandlerFactory definition) {
        if (definition instanceof DeadLetterChannelDefinition) {
            return new DeadLetterChannelReifier(route, (DeadLetterChannelDefinition) definition);
        } else if (definition instanceof DefaultErrorHandlerDefinition) {
            return new DefaultErrorHandlerReifier(route, (DefaultErrorHandlerDefinition) definition);
        } else if (definition instanceof NoErrorHandlerDefinition) {
            return new NoErrorHandlerReifier(route, definition);
        } else if (definition instanceof RefErrorHandlerDefinition) {
            return new ErrorHandlerRefReifier(route, definition);
        }

        return null;
    }

    public ExceptionPolicy createExceptionPolicy(OnExceptionDefinition def) {
        Predicate handled = def.getHandledPolicy();
        if (handled == null && def.getHandled() != null) {
            handled = createPredicate(def.getHandled());
        }
        Predicate continued = def.getContinuedPolicy();
        if (continued == null && def.getContinued() != null) {
            continued = createPredicate(def.getContinued());
        }
        Predicate retryWhile = def.getRetryWhilePolicy();
        if (retryWhile == null && def.getRetryWhile() != null) {
            retryWhile = createPredicate(def.getRetryWhile());
        }
        Processor onRedelivery = getProcessor(def.getOnRedelivery(), def.getOnRedeliveryRef());
        Processor onExceptionOccurred = getProcessor(def.getOnExceptionOccurred(), def.getOnExceptionOccurredRef());
        return new ExceptionPolicy(
                def.getId(), CamelContextHelper.getRouteId(def),
                parseBoolean(def.getUseOriginalMessage(), false),
                parseBoolean(def.getUseOriginalBody(), false),
                ObjectHelper.isNotEmpty(def.getOutputs()), handled,
                continued, retryWhile, onRedelivery,
                onExceptionOccurred, def.getRedeliveryPolicyRef(),
                createRedeliveryPolicyOptions(def.getRedeliveryPolicyType()), def.getExceptions());
    }

    public static RedeliveryPolicy createRedeliveryPolicy(RedeliveryPolicyDefinition definition, CamelContext context) {
        Map<RedeliveryOption, String> options = createRedeliveryPolicyOptions(definition);
        return createRedeliveryPolicy(options, context, null);
    }

    private static RedeliveryPolicy createRedeliveryPolicy(
            Map<RedeliveryOption, String> definition, CamelContext context, RedeliveryPolicy parentPolicy) {
        RedeliveryPolicy answer;
        if (parentPolicy != null) {
            answer = parentPolicy.copy();
        } else {
            answer = new RedeliveryPolicy();
        }
        try {
            if (definition.get(RedeliveryOption.maximumRedeliveries) != null) {
                answer.setMaximumRedeliveries(
                        CamelContextHelper.parseInteger(context, definition.get(RedeliveryOption.maximumRedeliveries)));
            }
            if (definition.get(RedeliveryOption.redeliveryDelay) != null) {
                answer.setRedeliveryDelay(
                        CamelContextHelper.parseLong(context, definition.get(RedeliveryOption.redeliveryDelay)));
            }
            if (definition.get(RedeliveryOption.asyncDelayedRedelivery) != null) {
                answer.setAsyncDelayedRedelivery(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.asyncDelayedRedelivery)));
            }
            if (definition.get(RedeliveryOption.retriesExhaustedLogLevel) != null) {
                answer.setRetriesExhaustedLogLevel(CamelContextHelper.parse(context, LoggingLevel.class,
                        definition.get(RedeliveryOption.retriesExhaustedLogLevel)));
            }
            if (definition.get(RedeliveryOption.retryAttemptedLogLevel) != null) {
                answer.setRetryAttemptedLogLevel(CamelContextHelper.parse(context, LoggingLevel.class,
                        definition.get(RedeliveryOption.retryAttemptedLogLevel)));
            }
            if (definition.get(RedeliveryOption.retryAttemptedLogInterval) != null) {
                answer.setRetryAttemptedLogInterval(
                        CamelContextHelper.parseInteger(context, definition.get(RedeliveryOption.retryAttemptedLogInterval)));
            }
            if (definition.get(RedeliveryOption.backOffMultiplier) != null) {
                answer.setBackOffMultiplier(
                        CamelContextHelper.parseDouble(context, definition.get(RedeliveryOption.backOffMultiplier)));
            }
            if (definition.get(RedeliveryOption.useExponentialBackOff) != null) {
                answer.setUseExponentialBackOff(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.useExponentialBackOff)));
            }
            if (definition.get(RedeliveryOption.collisionAvoidanceFactor) != null) {
                answer.setCollisionAvoidanceFactor(
                        CamelContextHelper.parseDouble(context, definition.get(RedeliveryOption.collisionAvoidanceFactor)));
            }
            if (definition.get(RedeliveryOption.useCollisionAvoidance) != null) {
                answer.setUseCollisionAvoidance(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.useCollisionAvoidance)));
            }
            if (definition.get(RedeliveryOption.maximumRedeliveryDelay) != null) {
                answer.setMaximumRedeliveryDelay(
                        CamelContextHelper.parseLong(context, definition.get(RedeliveryOption.maximumRedeliveryDelay)));
            }
            if (definition.get(RedeliveryOption.logStackTrace) != null) {
                answer.setLogStackTrace(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logStackTrace)));
            }
            if (definition.get(RedeliveryOption.logRetryStackTrace) != null) {
                answer.setLogRetryStackTrace(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logRetryStackTrace)));
            }
            if (definition.get(RedeliveryOption.logHandled) != null) {
                answer.setLogHandled(CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logHandled)));
            }
            if (definition.get(RedeliveryOption.logNewException) != null) {
                answer.setLogNewException(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logNewException)));
            }
            if (definition.get(RedeliveryOption.logContinued) != null) {
                answer.setLogContinued(CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logContinued)));
            }
            if (definition.get(RedeliveryOption.logRetryAttempted) != null) {
                answer.setLogRetryAttempted(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logRetryAttempted)));
            }
            if (definition.get(RedeliveryOption.logExhausted) != null) {
                answer.setLogExhausted(CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logExhausted)));
            }
            if (definition.get(RedeliveryOption.logExhaustedMessageHistory) != null) {
                answer.setLogExhaustedMessageHistory(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logExhaustedMessageHistory)));
            }
            if (definition.get(RedeliveryOption.logExhaustedMessageBody) != null) {
                answer.setLogExhaustedMessageBody(
                        CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.logExhaustedMessageBody)));
            }
            if (definition.get(RedeliveryOption.disableRedelivery) != null) {
                if (CamelContextHelper.parseBoolean(context, definition.get(RedeliveryOption.disableRedelivery))) {
                    answer.setMaximumRedeliveries(0);
                }
            }
            if (definition.get(RedeliveryOption.delayPattern) != null) {
                answer.setDelayPattern(CamelContextHelper.parseText(context, definition.get(RedeliveryOption.delayPattern)));
            }
            if (definition.get(RedeliveryOption.allowRedeliveryWhileStopping) != null) {
                answer.setAllowRedeliveryWhileStopping(CamelContextHelper.parseBoolean(context,
                        definition.get(RedeliveryOption.allowRedeliveryWhileStopping)));
            }
            if (definition.get(RedeliveryOption.exchangeFormatterRef) != null) {
                answer.setExchangeFormatterRef(
                        CamelContextHelper.parseText(context, definition.get(RedeliveryOption.exchangeFormatterRef)));
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        return answer;
    }

    private static Map<RedeliveryOption, String> createRedeliveryPolicyOptions(RedeliveryPolicyDefinition definition) {
        if (definition == null) {
            return null;
        }
        Map<RedeliveryOption, String> policy = new EnumMap<>(RedeliveryOption.class);
        setOption(policy, RedeliveryOption.maximumRedeliveries, definition.getMaximumRedeliveries());
        setOption(policy, RedeliveryOption.redeliveryDelay, definition.getRedeliveryDelay(), "1000");
        setOption(policy, RedeliveryOption.asyncDelayedRedelivery, definition.getAsyncDelayedRedelivery());
        setOption(policy, RedeliveryOption.backOffMultiplier, definition.getBackOffMultiplier(), "2");
        setOption(policy, RedeliveryOption.useExponentialBackOff, definition.getUseExponentialBackOff());
        setOption(policy, RedeliveryOption.collisionAvoidanceFactor, definition.getCollisionAvoidanceFactor(), "0.15");
        setOption(policy, RedeliveryOption.useCollisionAvoidance, definition.getUseCollisionAvoidance());
        setOption(policy, RedeliveryOption.maximumRedeliveryDelay, definition.getMaximumRedeliveryDelay(), "60000");
        setOption(policy, RedeliveryOption.retriesExhaustedLogLevel, definition.getRetriesExhaustedLogLevel(), "ERROR");
        setOption(policy, RedeliveryOption.retryAttemptedLogLevel, definition.getRetryAttemptedLogLevel(), "DEBUG");
        setOption(policy, RedeliveryOption.retryAttemptedLogInterval, definition.getRetryAttemptedLogInterval(), "1");
        setOption(policy, RedeliveryOption.logRetryAttempted, definition.getLogRetryAttempted(), "true");
        setOption(policy, RedeliveryOption.logStackTrace, definition.getLogStackTrace(), "true");
        setOption(policy, RedeliveryOption.logRetryStackTrace, definition.getLogRetryStackTrace());
        setOption(policy, RedeliveryOption.logHandled, definition.getLogHandled());
        setOption(policy, RedeliveryOption.logNewException, definition.getLogNewException(), "true");
        setOption(policy, RedeliveryOption.logContinued, definition.getLogContinued());
        setOption(policy, RedeliveryOption.logExhausted, definition.getLogExhausted(), "true");
        setOption(policy, RedeliveryOption.logExhaustedMessageHistory, definition.getLogExhaustedMessageHistory());
        setOption(policy, RedeliveryOption.logExhaustedMessageBody, definition.getLogExhaustedMessageBody());
        setOption(policy, RedeliveryOption.disableRedelivery, definition.getDisableRedelivery());
        setOption(policy, RedeliveryOption.delayPattern, definition.getDelayPattern());
        setOption(policy, RedeliveryOption.allowRedeliveryWhileStopping, definition.getAllowRedeliveryWhileStopping(), "true");
        setOption(policy, RedeliveryOption.exchangeFormatterRef, definition.getExchangeFormatterRef());
        return policy;
    }

    private static void setOption(Map<RedeliveryOption, String> policy, RedeliveryOption option, Object value) {
        setOption(policy, option, value, null);
    }

    private static void setOption(
            Map<RedeliveryOption, String> policy, RedeliveryOption option, Object value, Object defaultValue) {
        if (value != null) {
            policy.put(option, value.toString());
        } else if (defaultValue != null) {
            policy.put(option, defaultValue.toString());
        }
    }

    public void addExceptionPolicy(ErrorHandlerSupport handlerSupport, OnExceptionDefinition exceptionType) {
        // add error handler as child service so they get lifecycle handled
        Processor errorHandler = route.getOnException(exceptionType.getId());
        handlerSupport.addErrorHandler(errorHandler);

        // load exception classes
        List<Class<? extends Throwable>> list;
        if (ObjectHelper.isNotEmpty(exceptionType.getExceptions())) {
            list = createExceptionClasses(exceptionType);
            for (Class<? extends Throwable> clazz : list) {
                String routeId = null;
                // only get the route id, if the exception type is route scoped
                if (exceptionType.isRouteScoped()) {
                    routeId = route.getRouteId();
                }
                Predicate when = exceptionType.getOnWhen() != null ? exceptionType.getOnWhen().getExpression() : null;
                ExceptionPolicyKey key = new ExceptionPolicyKey(routeId, clazz, when);
                ExceptionPolicy policy = createExceptionPolicy(exceptionType);
                handlerSupport.addExceptionPolicy(key, policy);
            }
        }
    }

    protected List<Class<? extends Throwable>> createExceptionClasses(OnExceptionDefinition exceptionType) {
        List<String> list = exceptionType.getExceptions();
        List<Class<? extends Throwable>> answer = new ArrayList<>(list.size());
        for (String name : list) {
            try {
                Class<? extends Throwable> type = camelContext.getClassResolver().resolveMandatoryClass(name, Throwable.class);
                answer.add(type);
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
        return answer;
    }

    /**
     * Creates the error handler
     *
     * @param  processor the outer processor
     * @return           the error handler
     * @throws Exception is thrown if the error handler could not be created
     */
    public abstract Processor createErrorHandler(Processor processor) throws Exception;

    public void configure(ErrorHandler handler) {
        if (handler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport handlerSupport = (ErrorHandlerSupport) handler;

            for (NamedNode exception : route.getErrorHandlers(definition)) {
                addExceptionPolicy(handlerSupport, (OnExceptionDefinition) exception);
            }
        }
        if (handler instanceof RedeliveryErrorHandler) {
            boolean original = ((RedeliveryErrorHandler) handler).isUseOriginalMessagePolicy()
                    || ((RedeliveryErrorHandler) handler).isUseOriginalBodyPolicy();
            if (original) {
                // ensure allow original is turned on
                route.setAllowUseOriginalMessage(true);
            }
        }
    }

    /**
     * Note: Not for end users - this method is used internally by camel-blueprint
     */
    public static RedeliveryPolicy createRedeliveryPolicy(
            RedeliveryPolicyDefinition definition, CamelContext context, RedeliveryPolicy parentPolicy) {
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
                Duration duration = CamelContextHelper.parseDuration(context, definition.getRedeliveryDelay());
                answer.setRedeliveryDelay(duration.toMillis());
            }
            if (definition.getAsyncDelayedRedelivery() != null) {
                answer.setAsyncDelayedRedelivery(
                        CamelContextHelper.parseBoolean(context, definition.getAsyncDelayedRedelivery()));
            }
            if (definition.getRetriesExhaustedLogLevel() != null) {
                answer.setRetriesExhaustedLogLevel(
                        CamelContextHelper.parse(context, LoggingLevel.class, definition.getRetriesExhaustedLogLevel()));
            }
            if (definition.getRetryAttemptedLogLevel() != null) {
                answer.setRetryAttemptedLogLevel(
                        CamelContextHelper.parse(context, LoggingLevel.class, definition.getRetryAttemptedLogLevel()));
            }
            if (definition.getRetryAttemptedLogInterval() != null) {
                answer.setRetryAttemptedLogInterval(
                        CamelContextHelper.parseInteger(context, definition.getRetryAttemptedLogInterval()));
            }
            if (definition.getBackOffMultiplier() != null) {
                answer.setBackOffMultiplier(CamelContextHelper.parseDouble(context, definition.getBackOffMultiplier()));
            }
            if (definition.getUseExponentialBackOff() != null) {
                answer.setUseExponentialBackOff(
                        CamelContextHelper.parseBoolean(context, definition.getUseExponentialBackOff()));
            }
            if (definition.getCollisionAvoidanceFactor() != null) {
                answer.setCollisionAvoidanceFactor(
                        CamelContextHelper.parseDouble(context, definition.getCollisionAvoidanceFactor()));
            }
            if (definition.getUseCollisionAvoidance() != null) {
                answer.setUseCollisionAvoidance(
                        CamelContextHelper.parseBoolean(context, definition.getUseCollisionAvoidance()));
            }
            if (definition.getMaximumRedeliveryDelay() != null) {
                Duration duration = CamelContextHelper.parseDuration(context, definition.getMaximumRedeliveryDelay());
                answer.setMaximumRedeliveryDelay(duration.toMillis());
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
                answer.setLogExhaustedMessageHistory(
                        CamelContextHelper.parseBoolean(context, definition.getLogExhaustedMessageHistory()));
            }
            if (definition.getLogExhaustedMessageBody() != null) {
                answer.setLogExhaustedMessageBody(
                        CamelContextHelper.parseBoolean(context, definition.getLogExhaustedMessageBody()));
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
                answer.setAllowRedeliveryWhileStopping(
                        CamelContextHelper.parseBoolean(context, definition.getAllowRedeliveryWhileStopping()));
            }
            if (definition.getExchangeFormatterRef() != null) {
                answer.setExchangeFormatterRef(CamelContextHelper.parseText(context, definition.getExchangeFormatterRef()));
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        return answer;
    }

    protected Predicate getPredicate(Predicate pred, String ref) {
        if (pred == null && ref != null) {
            // its a bean expression
            Language bean = camelContext.resolveLanguage("bean");
            pred = bean.createPredicate(ref);
        }
        return pred;
    }

    protected <T> T getBean(Class<T> clazz, T bean, String ref) {
        if (bean == null && ref != null) {
            bean = lookupByNameAndType(ref, clazz);
        }
        return bean;
    }

    protected Processor getProcessor(Processor processor, String ref) {
        if (processor == null) {
            processor = getBean(Processor.class, null, ref);
        }
        if (processor != null) {
            // must wrap the processor in an UoW
            processor = PluginHelper.getInternalProcessorFactory(camelContext)
                    .addUnitOfWorkProcessorAdvice(camelContext, processor, route);
        }
        return processor;
    }

}
