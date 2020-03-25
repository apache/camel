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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.CamelContext;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NamedNode;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.ErrorHandlerBuilderSupport;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.processor.errorhandler.ExceptionPolicy;
import org.apache.camel.processor.errorhandler.ExceptionPolicy.RedeliveryOption;
import org.apache.camel.processor.errorhandler.ExceptionPolicyKey;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public abstract class ErrorHandlerReifier<T extends ErrorHandlerBuilderSupport> extends AbstractReifier {

    private static final Map<Class<?>, BiFunction<Route, ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>>> ERROR_HANDLERS;
    static {
        Map<Class<?>, BiFunction<Route, ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>>> map = new HashMap<>();
        map.put(DeadLetterChannelBuilder.class, DeadLetterChannelReifier::new);
        map.put(DefaultErrorHandlerBuilder.class, DefaultErrorHandlerReifier::new);
        map.put(ErrorHandlerBuilderRef.class, ErrorHandlerRefReifier::new);
        map.put(NoErrorHandlerBuilder.class, NoErrorHandlerReifier::new);
        ERROR_HANDLERS = map;
    }

    protected T definition;

    /**
     * Utility classes should not have a public constructor.
     */
    protected ErrorHandlerReifier(Route route, T definition) {
        super(route);
        this.definition = definition;
    }

    public static void registerReifier(Class<?> errorHandlerClass, BiFunction<Route, ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>> creator) {
        ERROR_HANDLERS.put(errorHandlerClass, creator);
    }

    public static ErrorHandlerReifier<? extends ErrorHandlerFactory> reifier(Route route, ErrorHandlerFactory definition) {
        BiFunction<Route, ErrorHandlerFactory, ErrorHandlerReifier<? extends ErrorHandlerFactory>> reifier = ERROR_HANDLERS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(route, definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
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
        Processor onRedelivery = def.getOnRedelivery();
        if (onRedelivery == null && def.getOnRedeliveryRef() != null) {
            onRedelivery = mandatoryLookup(parseString(def.getOnRedeliveryRef()), Processor.class);
        }
        Processor onExceptionOccurred = def.getOnExceptionOccurred();
        if (onExceptionOccurred == null && def.getOnExceptionOccurredRef() != null) {
            onExceptionOccurred = mandatoryLookup(parseString(def.getOnExceptionOccurredRef()), Processor.class);
        }
        return new ExceptionPolicy(def.getId(), CamelContextHelper.getRouteId(def),
                                   parseBoolean(def.getUseOriginalMessage(), false),
                                   parseBoolean(def.getUseOriginalBody(), false),
                                   ObjectHelper.isNotEmpty(def.getOutputs()), handled,
                                   continued, retryWhile, onRedelivery,
                                   onExceptionOccurred, def.getRedeliveryPolicyRef(),
                                   getRedeliveryPolicy(def.getRedeliveryPolicyType()), def.getExceptions());
    }

    private static Map<RedeliveryOption, String> getRedeliveryPolicy(RedeliveryPolicyDefinition definition) {
        if (definition == null) {
            return null;
        }
        Map<RedeliveryOption, String> policy = new HashMap<>();
        setOption(policy, RedeliveryOption.maximumRedeliveries, definition.getMaximumRedeliveries());
        setOption(policy, RedeliveryOption.redeliveryDelay, definition.getRedeliveryDelay());
        setOption(policy, RedeliveryOption.asyncDelayedRedelivery, definition.getAsyncDelayedRedelivery());
        setOption(policy, RedeliveryOption.backOffMultiplier, definition.getBackOffMultiplier());
        setOption(policy, RedeliveryOption.useExponentialBackOff, definition.getUseExponentialBackOff());
        setOption(policy, RedeliveryOption.collisionAvoidanceFactor, definition.getCollisionAvoidanceFactor());
        setOption(policy, RedeliveryOption.useCollisionAvoidance, definition.getUseCollisionAvoidance());
        setOption(policy, RedeliveryOption.maximumRedeliveryDelay, definition.getMaximumRedeliveryDelay());
        setOption(policy, RedeliveryOption.retriesExhaustedLogLevel, definition.getRetriesExhaustedLogLevel());
        setOption(policy, RedeliveryOption.retryAttemptedLogLevel, definition.getRetryAttemptedLogLevel());
        setOption(policy, RedeliveryOption.retryAttemptedLogInterval, definition.getRetryAttemptedLogInterval());
        setOption(policy, RedeliveryOption.logRetryAttempted, definition.getLogRetryAttempted());
        setOption(policy, RedeliveryOption.logStackTrace, definition.getLogStackTrace());
        setOption(policy, RedeliveryOption.logRetryStackTrace, definition.getLogRetryStackTrace());
        setOption(policy, RedeliveryOption.logHandled, definition.getLogHandled());
        setOption(policy, RedeliveryOption.logNewException, definition.getLogNewException());
        setOption(policy, RedeliveryOption.logContinued, definition.getLogContinued());
        setOption(policy, RedeliveryOption.logExhausted, definition.getLogExhausted());
        setOption(policy, RedeliveryOption.logExhaustedMessageHistory, definition.getLogExhaustedMessageHistory());
        setOption(policy, RedeliveryOption.logExhaustedMessageBody, definition.getLogExhaustedMessageBody());
        setOption(policy, RedeliveryOption.disableRedelivery, definition.getDisableRedelivery());
        setOption(policy, RedeliveryOption.delayPattern, definition.getDelayPattern());
        setOption(policy, RedeliveryOption.allowRedeliveryWhileStopping, definition.getAllowRedeliveryWhileStopping());
        setOption(policy, RedeliveryOption.exchangeFormatterRef, definition.getExchangeFormatterRef());
        return policy;
    }

    private static void setOption(Map<RedeliveryOption, String> policy, RedeliveryOption option, Object value) {
        if (value != null) {
            policy.put(option, value.toString());
        }
    }

    /**
     * Lookup the error handler by the given ref
     *
     * @param route the route context
     * @param ref reference id for the error handler
     * @return the error handler
     */
    public static ErrorHandlerFactory lookupErrorHandlerFactory(Route route, String ref) {
        return lookupErrorHandlerFactory(route, ref, true);
    }

    /**
     * Lookup the error handler by the given ref
     *
     * @param route the route
     * @param ref reference id for the error handler
     * @param mandatory whether the error handler must exists, if not a
     *            {@link org.apache.camel.NoSuchBeanException} is thrown
     * @return the error handler
     */
    public static ErrorHandlerFactory lookupErrorHandlerFactory(Route route, String ref, boolean mandatory) {
        ErrorHandlerFactory answer;
        CamelContext camelContext = route.getCamelContext();

        // if the ref is the default then we do not have any explicit error
        // handler configured
        // if that is the case then use error handlers configured on the route,
        // as for instance
        // the transacted error handler could have been configured on the route
        // so we should use that one
        if (!isErrorHandlerFactoryConfigured(ref)) {
            // see if there has been configured a error handler builder on the route
            answer = route.getErrorHandlerFactory();
            // check if its also a ref with no error handler configuration like me
            if (answer instanceof ErrorHandlerBuilderRef) {
                ErrorHandlerBuilderRef other = (ErrorHandlerBuilderRef)answer;
                String otherRef = other.getRef();
                if (!isErrorHandlerFactoryConfigured(otherRef)) {
                    // the other has also no explicit error handler configured
                    // then fallback to the handler
                    // configured on the parent camel context
                    answer = lookupErrorHandlerFactory(camelContext);
                }
                if (answer == null) {
                    // the other has also no explicit error handler configured
                    // then fallback to the default error handler
                    // otherwise we could recursive loop forever (triggered by
                    // createErrorHandler method)
                    answer = new DefaultErrorHandlerBuilder();
                }
                // inherit the error handlers from the other as they are to be
                // shared
                // this is needed by camel-spring when none error handler has
                // been explicit configured
                route.addErrorHandlerFactoryReference(other, answer);
            }
        } else {
            // use specific configured error handler
            if (mandatory) {
                answer = CamelContextHelper.mandatoryLookup(camelContext, ref, ErrorHandlerBuilder.class);
            } else {
                answer = CamelContextHelper.lookup(camelContext, ref, ErrorHandlerBuilder.class);
            }
        }

        return answer;
    }

    protected static ErrorHandlerFactory lookupErrorHandlerFactory(CamelContext camelContext) {
        ErrorHandlerFactory answer = camelContext.adapt(ExtendedCamelContext.class).getErrorHandlerFactory();
        if (answer instanceof ErrorHandlerBuilderRef) {
            ErrorHandlerBuilderRef other = (ErrorHandlerBuilderRef)answer;
            String otherRef = other.getRef();
            if (isErrorHandlerFactoryConfigured(otherRef)) {
                answer = CamelContextHelper.lookup(camelContext, otherRef, ErrorHandlerBuilder.class);
                if (answer == null) {
                    throw new IllegalArgumentException("ErrorHandlerBuilder with id " + otherRef + " not found in registry.");
                }
            }
        }

        return answer;
    }

    /**
     * Returns whether a specific error handler builder has been configured or
     * not.
     * <p/>
     * Can be used to test if none has been configured and then install a custom
     * error handler builder replacing the default error handler (that would
     * have been used as fallback otherwise). <br/>
     * This is for instance used by the transacted policy to setup a
     * TransactedErrorHandlerBuilder in camel-spring.
     */
    public static boolean isErrorHandlerFactoryConfigured(String ref) {
        return !ErrorHandlerBuilderRef.DEFAULT_ERROR_HANDLER_BUILDER.equals(ref);
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
     * @param processor the outer processor
     * @return the error handler
     * @throws Exception is thrown if the error handler could not be created
     */
    public abstract Processor createErrorHandler(Processor processor) throws Exception;

    public void configure(ErrorHandler handler) {
        if (handler instanceof ErrorHandlerSupport) {
            ErrorHandlerSupport handlerSupport = (ErrorHandlerSupport)handler;

            for (NamedNode exception : route.getErrorHandlers(definition)) {
                addExceptionPolicy(handlerSupport, (OnExceptionDefinition) exception);
            }
        }
        if (handler instanceof RedeliveryErrorHandler) {
            boolean original = ((RedeliveryErrorHandler)handler).isUseOriginalMessagePolicy() || ((RedeliveryErrorHandler)handler).isUseOriginalMessagePolicy();
            if (original) {
                // ensure allow original is turned on
                route.setAllowUseOriginalMessage(true);
            }
        }
    }

    /**
     * Note: Not for end users - this method is used internally by
     * camel-blueprint
     */
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
                answer.setRetriesExhaustedLogLevel(LoggingLevel.valueOf(definition.getRetriesExhaustedLogLevel()));
            }
            if (definition.getRetryAttemptedLogLevel() != null) {
                answer.setRetryAttemptedLogLevel(LoggingLevel.valueOf(definition.getRetryAttemptedLogLevel()));
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
