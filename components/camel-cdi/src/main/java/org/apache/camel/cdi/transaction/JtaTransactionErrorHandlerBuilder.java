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
package org.apache.camel.cdi.transaction;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds transactional error handlers. This class is based on
 * {@link org.apache.camel.spring.spi.TransactionErrorHandlerBuilder}.
 */
public class JtaTransactionErrorHandlerBuilder extends DefaultErrorHandlerBuilder {

    public static final String ROLLBACK_LOGGING_LEVEL_PROPERTY =
        JtaTransactionErrorHandlerBuilder.class.getName() + "#rollbackLoggingLevel";

    private static final Logger LOG = LoggerFactory.getLogger(JtaTransactionErrorHandlerBuilder.class);

    private static final String PROPAGATION_REQUIRED = "PROPAGATION_REQUIRED";

    private LoggingLevel rollbackLoggingLevel = LoggingLevel.WARN;

    private JtaTransactionPolicy transactionPolicy;

    private String policyRef;

    @Override
    public boolean supportTransacted() {
        return true;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        final JtaTransactionErrorHandlerBuilder answer = new JtaTransactionErrorHandlerBuilder();
        cloneBuilder(answer);
        return answer;
    }

    @Override
    protected void cloneBuilder(DefaultErrorHandlerBuilder other) {
        super.cloneBuilder(other);
        if (other instanceof JtaTransactionErrorHandlerBuilder) {
            final JtaTransactionErrorHandlerBuilder otherTx = (JtaTransactionErrorHandlerBuilder) other;
            transactionPolicy = otherTx.transactionPolicy;
            rollbackLoggingLevel = otherTx.rollbackLoggingLevel;
        }
    }

    public Processor createErrorHandler(final RouteContext routeContext, final Processor processor) throws Exception {
        // resolve policy reference, if given
        if (transactionPolicy == null) {
            if (policyRef != null) {
                final TransactedDefinition transactedDefinition = new TransactedDefinition();
                transactedDefinition.setRef(policyRef);
                final Policy policy = transactedDefinition.resolvePolicy(routeContext);
                if (policy != null) {
                    if (!(policy instanceof JtaTransactionPolicy)) {
                        throw new RuntimeCamelException("The configured policy '" + policyRef + "' is of type '"
                                + policyRef.getClass().getName() + "' but an instance of '"
                                + JtaTransactionPolicy.class.getName() + "' is required!");
                    }
                    transactionPolicy = (JtaTransactionPolicy) policy;
                }
            }
        }

        // try to lookup default policy
        if (transactionPolicy == null) {
            LOG.debug(
                    "No transaction policy configured on TransactionErrorHandlerBuilder. Will try find it in the registry.");

            Map<String, TransactedPolicy> mapPolicy = routeContext.lookupByType(TransactedPolicy.class);
            if (mapPolicy != null && mapPolicy.size() == 1) {
                TransactedPolicy policy = mapPolicy.values().iterator().next();
                if (policy instanceof JtaTransactionPolicy) {
                    transactionPolicy = (JtaTransactionPolicy) policy;
                }
            }

            if (transactionPolicy == null) {
                TransactedPolicy policy = routeContext.lookup(PROPAGATION_REQUIRED, TransactedPolicy.class);
                if (policy instanceof JtaTransactionPolicy) {
                    transactionPolicy = (JtaTransactionPolicy) policy;
                }
            }

            if (transactionPolicy != null) {
                LOG.debug("Found TransactionPolicy in registry to use: {}", transactionPolicy);
            }
        }

        ObjectHelper.notNull(transactionPolicy, "transactionPolicy", this);

        final CamelContext camelContext = routeContext.getCamelContext();
        final Map<String, String> properties = camelContext.getProperties();
        if ((properties != null) && properties.containsKey(ROLLBACK_LOGGING_LEVEL_PROPERTY)) {
            rollbackLoggingLevel = LoggingLevel.valueOf(properties.get(ROLLBACK_LOGGING_LEVEL_PROPERTY));
        }

        JtaTransactionErrorHandler answer = new JtaTransactionErrorHandler(camelContext,
                processor,
                getLogger(),
                getOnRedelivery(),
                getRedeliveryPolicy(),
                getExceptionPolicyStrategy(),
                transactionPolicy,
                getRetryWhilePolicy(camelContext),
                getExecutorService(camelContext),
                rollbackLoggingLevel,
                getOnExceptionOccurred());

        // configure error handler before we can use it
        configure(routeContext, answer);
        return answer;
    }

    public JtaTransactionErrorHandlerBuilder setTransactionPolicy(final String ref) {
        policyRef = ref;
        return this;
    }

    public JtaTransactionErrorHandlerBuilder setTransactionPolicy(final JtaTransactionPolicy transactionPolicy) {
        this.transactionPolicy = transactionPolicy;
        return this;
    }

    public JtaTransactionErrorHandlerBuilder setRollbackLoggingLevel(final LoggingLevel rollbackLoggingLevel) {
        this.rollbackLoggingLevel = rollbackLoggingLevel;
        return this;
    }

    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(TransactionErrorHandler.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "JtaTransactionErrorHandlerBuilder";
    }
}
