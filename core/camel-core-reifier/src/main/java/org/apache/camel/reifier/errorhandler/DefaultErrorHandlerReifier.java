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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.processor.FatalFallbackErrorHandler;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.errorhandler.DefaultErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ThreadPoolProfile;
import org.slf4j.LoggerFactory;

public class DefaultErrorHandlerReifier extends ErrorHandlerReifier<DefaultErrorHandlerDefinition> {

    public DefaultErrorHandlerReifier(Route route, DefaultErrorHandlerDefinition definition) {
        super(route, definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        // optimize to use shared default instance if using out of the box settings

        RedeliveryPolicy redeliveryPolicy = resolveRedeliveryPolicy(definition, camelContext);
        CamelLogger logger = resolveLogger(definition);

        DefaultErrorHandler answer = new DefaultErrorHandler(
                camelContext, processor, logger,
                getProcessor(definition.getOnRedeliveryProcessor(), definition.getOnRedeliveryRef()),
                redeliveryPolicy,
                getPredicate(definition.getRetryWhilePredicate(), definition.getRetryWhileRef()),
                getExecutorService(definition.getExecutorServiceBean(), definition.getExecutorServiceRef()),
                getProcessor(definition.getOnPrepareFailureProcessor(), definition.getOnPrepareFailureRef()),
                getProcessor(definition.getOnExceptionOccurredProcessor(), definition.getOnExceptionOccurredRef()));
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    private Predicate resolveRetryWhilePolicy(DeadLetterChannelDefinition definition, CamelContext camelContext) {
        Predicate answer = definition.getRetryWhilePredicate();

        if (answer == null && definition.getRetryWhileRef() != null) {
            // it is a bean expression
            Language bean = camelContext.resolveLanguage("bean");
            answer = bean.createPredicate(definition.getRetryWhileRef());
            answer.initPredicate(camelContext);
        }

        return answer;
    }

    private CamelLogger resolveLogger(DefaultErrorHandlerDefinition definition) {
        CamelLogger answer = definition.getLoggerBean();
        if (answer == null && definition.getLoggerRef() != null) {
            answer = mandatoryLookup(definition.getLoggerRef(), CamelLogger.class);
        }
        if (answer == null) {
            answer = new CamelLogger(LoggerFactory.getLogger(DefaultErrorHandler.class), LoggingLevel.ERROR);
        }
        if (definition.getLevel() != null) {
            answer.setLevel(parse(LoggingLevel.class, definition.getLevel()));
        }
        return answer;
    }

    private Processor createDeadLetterChannelProcessor(String uri) {
        // wrap in our special safe fallback error handler if sending to
        // dead letter channel fails
        Processor child = new SendProcessor(camelContext.getEndpoint(uri), ExchangePattern.InOnly);
        // force MEP to be InOnly so when sending to DLQ we would not expect
        // a reply if the MEP was InOut
        return new FatalFallbackErrorHandler(child, true);
    }

    private RedeliveryPolicy resolveRedeliveryPolicy(DefaultErrorHandlerDefinition definition, CamelContext camelContext) {
        if (definition.hasRedeliveryPolicy() && definition.getRedeliveryPolicyRef() != null) {
            throw new IllegalArgumentException(
                    "Cannot have both redeliveryPolicy and redeliveryPolicyRef set at the same time.");
        }

        RedeliveryPolicy answer = null;
        RedeliveryPolicyDefinition def = definition.hasRedeliveryPolicy() ? definition.getRedeliveryPolicy() : null;
        if (def != null) {
            answer = ErrorHandlerReifier.createRedeliveryPolicy(def, camelContext, null);
        }
        if (def == null && definition.getRedeliveryPolicyRef() != null) {
            answer = mandatoryLookup(definition.getRedeliveryPolicyRef(), RedeliveryPolicy.class);
        }
        if (answer == null) {
            answer = RedeliveryPolicy.DEFAULT_POLICY;
        }
        return answer;
    }

    protected synchronized ScheduledExecutorService getExecutorService(
            ScheduledExecutorService executorService, String executorServiceRef) {
        if (executorService == null || executorService.isShutdown()) {
            // camel context will shutdown the executor when it shutdown so no
            // need to shut it down when stopping
            if (executorServiceRef != null) {
                executorService = lookupByNameAndType(executorServiceRef, ScheduledExecutorService.class);
                if (executorService == null) {
                    ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
                    ThreadPoolProfile profile = manager.getThreadPoolProfile(executorServiceRef);
                    executorService = manager.newScheduledThreadPool(this, executorServiceRef, profile);
                }
                if (executorService == null) {
                    throw new IllegalArgumentException("ExecutorService " + executorServiceRef + " not found in registry.");
                }
            } else {
                // no explicit configured thread pool, so leave it up to the
                // error handler to decide if it need a default thread pool from
                // CamelContext#getErrorHandlerExecutorService
                executorService = null;
            }
        }
        return executorService;
    }

}
