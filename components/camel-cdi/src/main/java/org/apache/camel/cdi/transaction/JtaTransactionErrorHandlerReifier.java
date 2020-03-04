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
package org.apache.camel.cdi.transaction;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.reifier.TransactedReifier;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JtaTransactionErrorHandlerReifier extends ErrorHandlerReifier<JtaTransactionErrorHandlerBuilder> {

    public static final String ROLLBACK_LOGGING_LEVEL_PROPERTY =
            JtaTransactionErrorHandlerBuilder.class.getName() + "#rollbackLoggingLevel";

    private static final String PROPAGATION_REQUIRED = "PROPAGATION_REQUIRED";

    private static final Logger LOG = LoggerFactory.getLogger(JtaTransactionErrorHandlerReifier.class);

    public JtaTransactionErrorHandlerReifier(Route route, ErrorHandlerFactory definition) {
        super(route, (JtaTransactionErrorHandlerBuilder) definition);
    }

    @Override
    public Processor createErrorHandler(final Processor processor) throws Exception {
        JtaTransactionPolicy transactionPolicy = definition.getTransactionPolicy();

        // resolve policy reference, if given
        if (transactionPolicy == null) {
            if (definition.getPolicyRef() != null) {
                final TransactedDefinition transactedDefinition = new TransactedDefinition();
                transactedDefinition.setRef(definition.getPolicyRef());
                final Policy policy = new TransactedReifier(camelContext, transactedDefinition).resolvePolicy();
                if (policy != null) {
                    if (!(policy instanceof JtaTransactionPolicy)) {
                        throw new RuntimeCamelException("The configured policy '" + definition.getPolicyRef()
                                + "' is of type '" + policy.getClass().getName() + "' but an instance of '"
                                + JtaTransactionPolicy.class.getName() + "' is required!");
                    }
                    transactionPolicy = (JtaTransactionPolicy) policy;
                }
            }
        }

        // try to lookup default policy
        if (transactionPolicy == null) {
            LOG.debug("No transaction policy configured on TransactionErrorHandlerBuilder. "
                    + "Will try find it in the registry.");

            Map<String, TransactedPolicy> mapPolicy = findByTypeWithName(TransactedPolicy.class);
            if (mapPolicy != null && mapPolicy.size() == 1) {
                TransactedPolicy policy = mapPolicy.values().iterator().next();
                if (policy instanceof JtaTransactionPolicy) {
                    transactionPolicy = (JtaTransactionPolicy) policy;
                }
            }

            if (transactionPolicy == null) {
                TransactedPolicy policy = lookupByNameAndType(PROPAGATION_REQUIRED, TransactedPolicy.class);
                if (policy instanceof JtaTransactionPolicy) {
                    transactionPolicy = (JtaTransactionPolicy) policy;
                }
            }

            if (transactionPolicy != null) {
                LOG.debug("Found TransactionPolicy in registry to use: {}", transactionPolicy);
            }
        }

        ObjectHelper.notNull(transactionPolicy, "transactionPolicy", this);

        final Map<String, String> properties = camelContext.getGlobalOptions();
        LoggingLevel rollbackLoggingLevel = definition.getRollbackLoggingLevel();
        if ((properties != null) && properties.containsKey(ROLLBACK_LOGGING_LEVEL_PROPERTY)) {
            rollbackLoggingLevel = LoggingLevel.valueOf(properties.get(ROLLBACK_LOGGING_LEVEL_PROPERTY));
        }

        JtaTransactionErrorHandler answer = new JtaTransactionErrorHandler(camelContext,
                processor,
                definition.getLogger(),
                definition.getOnRedelivery(),
                definition.getRedeliveryPolicy(),
                definition.getExceptionPolicyStrategy(),
                transactionPolicy,
                definition.getRetryWhilePolicy(camelContext),
                getExecutorService(),
                rollbackLoggingLevel,
                definition.getOnExceptionOccurred());

        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    protected synchronized ScheduledExecutorService getExecutorService() {
        ScheduledExecutorService executorService = definition.getExecutorService();
        if (executorService == null || executorService.isShutdown()) {
            // camel context will shutdown the executor when it shutdown so no
            // need to shut it down when stopping
            if (definition.getExecutorServiceRef() != null) {
                executorService = lookupByNameAndType(definition.getExecutorServiceRef(), ScheduledExecutorService.class);
                if (executorService == null) {
                    ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
                    ThreadPoolProfile profile = manager.getThreadPoolProfile(definition.getExecutorServiceRef());
                    executorService = manager.newScheduledThreadPool(this, definition.getExecutorServiceRef(), profile);
                }
                if (executorService == null) {
                    throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() + " not found in registry.");
                }
            } else {
                // no explicit configured thread pool, so leave it up to the
                // error handler to decide if it need
                // a default thread pool from
                // CamelContext#getErrorHandlerExecutorService
                executorService = null;
            }
        }
        return executorService;
    }

}
