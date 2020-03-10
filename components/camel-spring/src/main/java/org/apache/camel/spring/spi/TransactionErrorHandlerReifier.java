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

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.reifier.errorhandler.DefaultErrorHandlerReifier;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.model.TransactedDefinition.PROPAGATION_REQUIRED;

public class TransactionErrorHandlerReifier extends DefaultErrorHandlerReifier<TransactionErrorHandlerBuilder> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionErrorHandlerReifier.class);

    public TransactionErrorHandlerReifier(Route route, ErrorHandlerFactory definition) {
        super(route, definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        TransactionTemplate transactionTemplate = definition.getTransactionTemplate();
        if (transactionTemplate == null) {
            // lookup in context if no transaction template has been configured
            LOG.debug("No TransactionTemplate configured on TransactionErrorHandlerBuilder. Will try find it in the registry.");

            Map<String, TransactedPolicy> mapPolicy = findByTypeWithName(TransactedPolicy.class);
            if (mapPolicy != null && mapPolicy.size() == 1) {
                TransactedPolicy policy = mapPolicy.values().iterator().next();
                if (policy instanceof SpringTransactionPolicy) {
                    transactionTemplate = ((SpringTransactionPolicy) policy).getTransactionTemplate();
                }
            }

            if (transactionTemplate == null) {
                TransactedPolicy policy = lookup(PROPAGATION_REQUIRED, TransactedPolicy.class);
                if (policy instanceof SpringTransactionPolicy) {
                    transactionTemplate = ((SpringTransactionPolicy) policy).getTransactionTemplate();
                }
            }

            if (transactionTemplate == null) {
                Map<String, TransactionTemplate> mapTemplate = findByTypeWithName(TransactionTemplate.class);
                if (mapTemplate == null || mapTemplate.isEmpty()) {
                    LOG.trace("No TransactionTemplate found in registry.");
                } else if (mapTemplate.size() == 1) {
                    transactionTemplate = mapTemplate.values().iterator().next();
                } else {
                    LOG.debug("Found {} TransactionTemplate in registry. Cannot determine which one to use. "
                            + "Please configure a TransactionTemplate on the TransactionErrorHandlerBuilder", mapTemplate.size());
                }
            }

            if (transactionTemplate == null) {
                Map<String, PlatformTransactionManager> mapManager = findByTypeWithName(PlatformTransactionManager.class);
                if (mapManager == null || mapManager.isEmpty()) {
                    LOG.trace("No PlatformTransactionManager found in registry.");
                } else if (mapManager.size() == 1) {
                    transactionTemplate = new TransactionTemplate(mapManager.values().iterator().next());
                } else {
                    LOG.debug("Found {} PlatformTransactionManager in registry. Cannot determine which one to use for TransactionTemplate. "
                            + "Please configure a TransactionTemplate on the TransactionErrorHandlerBuilder", mapManager.size());
                }
            }

            if (transactionTemplate != null) {
                LOG.debug("Found TransactionTemplate in registry to use: {}", transactionTemplate);
            }
        }

        ObjectHelper.notNull(transactionTemplate, "transactionTemplate", this);

        TransactionErrorHandler answer = new TransactionErrorHandler(camelContext, processor,
                definition.getLogger(), definition.getOnRedelivery(),
                definition.getRedeliveryPolicy(), definition.getExceptionPolicyStrategy(), transactionTemplate,
                definition.getRetryWhilePolicy(camelContext),
                getExecutorService(camelContext),
                definition.getRollbackLoggingLevel(), definition.getOnExceptionOccurred());
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

}
