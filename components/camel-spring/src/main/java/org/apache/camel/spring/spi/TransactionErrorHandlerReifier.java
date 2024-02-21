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
package org.apache.camel.spring.spi;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.model.errorhandler.SpringTransactionErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.TransactionErrorHandlerDefinition;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.model.TransactedDefinition.PROPAGATION_REQUIRED;

public class TransactionErrorHandlerReifier extends ErrorHandlerReifier<SpringTransactionErrorHandlerDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionErrorHandlerReifier.class);

    public TransactionErrorHandlerReifier(Route route, SpringTransactionErrorHandlerDefinition definition) {
        super(route, definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        // must have a transaction template
        TransactionTemplate transactionTemplate = resolveTransactionTemplate(definition);
        ObjectHelper.notNull(transactionTemplate, "transactionTemplate", this);

        // optimize to use shared default instance if using out of the box settings
        RedeliveryPolicy redeliveryPolicy = resolveRedeliveryPolicy(definition, camelContext);
        CamelLogger logger = resolveLogger(definition);
        LoggingLevel rollbackLoggingLevel = resolveRollbackLoggingLevel(definition);

        TransactionErrorHandler answer = new TransactionErrorHandler(
                camelContext, processor, logger,
                getProcessor(definition.getOnRedeliveryProcessor(), definition.getOnRedeliveryRef()),
                redeliveryPolicy,
                transactionTemplate,
                resolveRetryWhilePolicy(definition, camelContext),
                getExecutorService(definition.getExecutorServiceBean(), definition.getExecutorServiceRef()),
                rollbackLoggingLevel,
                getProcessor(definition.getOnExceptionOccurredProcessor(), definition.getOnExceptionOccurredRef()));
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    private TransactionTemplate resolveTransactionTemplate(
            TransactionErrorHandlerDefinition definition) {

        TransactionTemplate answer = null;

        SpringTransactionPolicy policy = (SpringTransactionPolicy) definition.getTransactedPolicy();
        if (policy == null && definition.getTransactedPolicyRef() != null) {
            policy = mandatoryLookup(definition.getTransactedPolicyRef(), SpringTransactionPolicy.class);
        }
        if (policy != null) {
            answer = policy.getTransactionTemplate();
        }

        if (answer == null) {
            Map<String, SpringTransactionPolicy> mapPolicy = findByTypeWithName(SpringTransactionPolicy.class);
            if (mapPolicy != null && mapPolicy.size() == 1) {
                policy = mapPolicy.values().iterator().next();
                if (policy != null) {
                    answer = policy.getTransactionTemplate();
                }
            }
        }

        if (answer == null) {
            policy = lookupByNameAndType(PROPAGATION_REQUIRED, SpringTransactionPolicy.class);
            if (policy != null) {
                answer = policy.getTransactionTemplate();
            }
        }

        if (answer == null) {
            Map<String, TransactionTemplate> mapTemplate = findByTypeWithName(TransactionTemplate.class);
            if (mapTemplate == null || mapTemplate.isEmpty()) {
                LOG.trace("No TransactionTemplate found in registry.");
            } else if (mapTemplate.size() == 1) {
                answer = mapTemplate.values().iterator().next();
            } else {
                LOG.debug("Found {} TransactionTemplate in registry. Cannot determine which one to use. "
                          + "Please configure a TransactionTemplate on the TransactionErrorHandlerBuilder",
                        mapTemplate.size());
            }
        }

        if (answer == null) {
            Map<String, PlatformTransactionManager> mapManager = findByTypeWithName(PlatformTransactionManager.class);
            if (mapManager == null || mapManager.isEmpty()) {
                LOG.trace("No PlatformTransactionManager found in registry.");
            } else if (mapManager.size() == 1) {
                answer = new TransactionTemplate(mapManager.values().iterator().next());
            } else {
                LOG.debug(
                        "Found {} PlatformTransactionManager in registry. Cannot determine which one to use for TransactionTemplate. "
                          + "Please configure a TransactionTemplate on the TransactionErrorHandlerBuilder",
                        mapManager.size());
            }
        }

        if (answer != null) {
            LOG.debug("Found TransactionTemplate in registry to use: {}", answer);
        }
        return answer;
    }

    private CamelLogger resolveLogger(TransactionErrorHandlerDefinition definition) {
        CamelLogger answer = definition.getLoggerBean();
        if (answer == null && definition.getLoggerRef() != null) {
            answer = mandatoryLookup(definition.getLoggerRef(), CamelLogger.class);
        }
        if (answer == null) {
            answer = new CamelLogger(LoggerFactory.getLogger(TransactionErrorHandler.class), LoggingLevel.ERROR);
        }
        if (definition.getLevel() != null) {
            answer.setLevel(parse(LoggingLevel.class, definition.getLevel()));
        }
        return answer;
    }

    private LoggingLevel resolveRollbackLoggingLevel(TransactionErrorHandlerDefinition definition) {
        LoggingLevel answer = LoggingLevel.WARN;
        if (definition.getRollbackLoggingLevel() != null) {
            answer = parse(LoggingLevel.class, definition.getRollbackLoggingLevel());
        }
        return answer;
    }

    private RedeliveryPolicy resolveRedeliveryPolicy(TransactionErrorHandlerDefinition definition, CamelContext camelContext) {
        RedeliveryPolicy answer = null;
        RedeliveryPolicyDefinition def = definition.getRedeliveryPolicy();
        if (def == null && definition.getRedeliveryPolicyRef() != null) {
            // ref may point to a definition
            def = lookupByNameAndType(definition.getRedeliveryPolicyRef(), RedeliveryPolicyDefinition.class);
        }
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

    private Predicate resolveRetryWhilePolicy(TransactionErrorHandlerDefinition definition, CamelContext camelContext) {
        Predicate answer = definition.getRetryWhilePredicate();

        if (answer == null && definition.getRetryWhileRef() != null) {
            // it is a bean expression
            Language bean = camelContext.resolveLanguage("bean");
            answer = bean.createPredicate(definition.getRetryWhileRef());
            answer.initPredicate(camelContext);
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
