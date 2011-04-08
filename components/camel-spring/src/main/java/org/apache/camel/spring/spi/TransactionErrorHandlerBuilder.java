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
package org.apache.camel.spring.spi;

import java.util.Map;

import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.processor.CamelLogger;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A transactional error handler that supports leveraging Spring TransactionManager.
 *
 * @version 
 */
public class TransactionErrorHandlerBuilder extends DefaultErrorHandlerBuilder {

    private static final transient Logger LOG = LoggerFactory.getLogger(TransactionErrorHandlerBuilder.class);
    private static final String PROPAGATION_REQUIRED = "PROPAGATION_REQUIRED";
    private TransactionTemplate transactionTemplate;

    public TransactionErrorHandlerBuilder() {
        // no-arg constructor used by Spring DSL
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    public boolean supportTransacted() {
        return true;
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        if (transactionTemplate == null) {
            // lookup in context if no transaction template has been configured
            LOG.debug("No TransactionTemplate configured on TransactionErrorHandlerBuilder. Will try find it in the registry.");

            if (transactionTemplate == null) {
                Map<String, TransactedPolicy> map = routeContext.lookupByType(TransactedPolicy.class);
                if (map != null && map.size() == 1) {
                    TransactedPolicy policy = map.values().iterator().next();
                    if (policy != null && policy instanceof SpringTransactionPolicy) {
                        transactionTemplate = ((SpringTransactionPolicy) policy).getTransactionTemplate();
                    }
                }
            }

            if (transactionTemplate == null) {
                TransactedPolicy policy = routeContext.lookup(PROPAGATION_REQUIRED, TransactedPolicy.class);
                if (policy != null && policy instanceof SpringTransactionPolicy) {
                    transactionTemplate = ((SpringTransactionPolicy) policy).getTransactionTemplate();
                }
            }

            if (transactionTemplate == null) {
                Map<String, TransactionTemplate> map = routeContext.lookupByType(TransactionTemplate.class);
                if (map != null && map.size() == 1) {
                    transactionTemplate = map.values().iterator().next();
                } else if (LOG.isDebugEnabled()) {
                    if (map == null || map.isEmpty()) {
                        LOG.trace("No TransactionTemplate found in registry.");
                    } else {
                        LOG.debug("Found " + map.size() + " TransactionTemplate in registry. "
                                + "Cannot determine which one to use. Please configure a TransactionTemplate on the TransactionErrorHandlerBuilder");
                    }
                }
            }

            if (transactionTemplate == null) {
                Map<String, PlatformTransactionManager> map = routeContext.lookupByType(PlatformTransactionManager.class);
                if (map != null && map.size() == 1) {
                    transactionTemplate = new TransactionTemplate(map.values().iterator().next());
                } else if (LOG.isDebugEnabled()) {
                    if (map == null || map.isEmpty()) {
                        LOG.trace("No PlatformTransactionManager found in registry.");
                    } else {
                        LOG.debug("Found " + map.size() + " PlatformTransactionManager in registry. "
                                + "Cannot determine which one to use for TransactionTemplate. Please configure a TransactionTemplate on the TransactionErrorHandlerBuilder");
                    }
                }
            }

            if (transactionTemplate != null) {
                LOG.debug("Found TransactionTemplate in registry to use: " + transactionTemplate);
            }
        }

        ObjectHelper.notNull(transactionTemplate, "transactionTemplate", this);

        TransactionErrorHandler answer = new TransactionErrorHandler(routeContext.getCamelContext(), processor,
                getLogger(), getOnRedelivery(), getRedeliveryPolicy(), getHandledPolicy(),
                getExceptionPolicyStrategy(), transactionTemplate, getRetryWhilePolicy(routeContext.getCamelContext()), getExecutorServiceRef());
        // configure error handler before we can use it
        configure(answer);
        return answer;
    }

    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setSpringTransactionPolicy(SpringTransactionPolicy policy) {
        this.transactionTemplate = policy.getTransactionTemplate();
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Builder methods
    // -------------------------------------------------------------------------

    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(TransactionErrorHandler.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "TransactionErrorHandlerBuilder";
    }

}
