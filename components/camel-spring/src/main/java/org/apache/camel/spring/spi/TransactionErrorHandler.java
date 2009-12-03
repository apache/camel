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

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.ObjectHelper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The <a href="http://camel.apache.org/transactional-client.html">Transactional Client</a>
 * EIP pattern.
 *
 * @version $Revision$
 */
public class TransactionErrorHandler extends RedeliveryErrorHandler {

    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the transaction error handler.
     *
     * @param output                  outer processor that should use this default error handler
     * @param logger                  logger to use for logging failures and redelivery attempts
     * @param redeliveryProcessor     an optional processor to run before redelivery attempt
     * @param redeliveryPolicy        policy for redelivery
     * @param handledPolicy           policy for handling failed exception that are moved to the dead letter queue
     * @param exceptionPolicyStrategy strategy for onException handling
     * @param transactionTemplate     the transaction template
     */
    public TransactionErrorHandler(Processor output, Logger logger, Processor redeliveryProcessor,
                                   RedeliveryPolicy redeliveryPolicy, Predicate handledPolicy,
                                   ExceptionPolicyStrategy exceptionPolicyStrategy, TransactionTemplate transactionTemplate) {
        super(output, logger, redeliveryProcessor, redeliveryPolicy, handledPolicy, null, null, false);
        setExceptionPolicy(exceptionPolicyStrategy);
        this.transactionTemplate = transactionTemplate;
    }

    public boolean supportTransacted() {
        return true;
    }

    @Override
    public String toString() {
        if (output == null) {
            // if no output then don't do any description
            return "";
        }
        return "TransactionErrorHandler:"
                + propagationBehaviorToString(transactionTemplate.getPropagationBehavior())
                + "[" + getOutput() + "]";
    }

    public void process(final Exchange exchange) throws Exception {
        if (exchange.getUnitOfWork().isTransactedBy(transactionTemplate)) {
            // already transacted by this transaction template
            // so lets just let the regular default error handler process it
            processByRegularErrorHandler(exchange);
        } else {
            // not yet wrapped in transaction so lets do that
            processInTransaction(exchange);
        }
    }

    protected void processByRegularErrorHandler(Exchange exchange) {
        try {
            super.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

    protected void processInTransaction(final Exchange exchange) throws Exception {
        String id = ObjectHelper.getIdentityHashCode(transactionTemplate);
        try {
            // mark the beginning of this transaction boundary
            exchange.getUnitOfWork().beginTransactedBy(transactionTemplate);

            if (log.isDebugEnabled()) {
                log.debug("Transaction begin (" + id + ") for ExchangeId: " + exchange.getExchangeId());
            }

            doInTransactionTemplate(exchange);

            if (log.isDebugEnabled()) {
                log.debug("Transaction commit (" + id + ") for ExchangeId: " + exchange.getExchangeId());
            }
        } catch (TransactionRollbackException e) {
            // ignore as its just a dummy exception to force spring TX to rollback
            if (log.isDebugEnabled()) {
                log.debug("Transaction rollback (" + id + ") for ExchangeId: " + exchange.getExchangeId());
            }
        } catch (Exception e) {
            log.warn("Transaction rollback (" + id + ") for ExchangeId: " + exchange.getExchangeId() + " due exception: " + e.getMessage());
            exchange.setException(e);
        } finally {
            // mark the end of this transaction boundary
            exchange.getUnitOfWork().endTransactedBy(transactionTemplate);
        }
    }

    protected void doInTransactionTemplate(final Exchange exchange) {

        // spring transaction template is working best with rollback if you throw it a runtime exception
        // otherwise it may not rollback messages send to JMS queues etc.

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // wrapper exception to throw if the exchange failed
                // IMPORTANT: Must be a runtime exception to let Spring regard it as to do "rollback"
                RuntimeCamelException rce = null;

                exchange.setProperty(Exchange.TRANSACTED, Boolean.TRUE);

                // and now let process the exchange
                try {
                    TransactionErrorHandler.super.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }

                // after handling and still an exception or marked as rollback only then rollback
                if (exchange.getException() != null || exchange.isRollbackOnly()) {

                    // if it was a local rollback only then remove its marker so outer transaction
                    // wont rollback as well (Note: isRollbackOnly() also returns true for ROLLBACK_ONLY_LAST)
                    exchange.removeProperty(Exchange.ROLLBACK_ONLY_LAST);

                    // wrap exception in transacted exception
                    if (exchange.getException() != null) {
                        rce = ObjectHelper.wrapRuntimeCamelException(exchange.getException());
                    }

                    if (!status.isRollbackOnly()) {
                        status.setRollbackOnly();
                    }

                    // rethrow if an exception occurred
                    if (rce != null) {
                        throw rce;
                    } else {
                        // create dummy exception to force spring transaction manager to rollback
                        throw new TransactionRollbackException();
                    }
                }
            }
        });
    }

    protected String propagationBehaviorToString(int propagationBehavior) {
        String rc;
        switch (propagationBehavior) {
        case TransactionDefinition.PROPAGATION_MANDATORY:
            rc = "PROPAGATION_MANDATORY";
            break;
        case TransactionDefinition.PROPAGATION_NESTED:
            rc = "PROPAGATION_NESTED";
            break;
        case TransactionDefinition.PROPAGATION_NEVER:
            rc = "PROPAGATION_NEVER";
            break;
        case TransactionDefinition.PROPAGATION_NOT_SUPPORTED:
            rc = "PROPAGATION_NOT_SUPPORTED";
            break;
        case TransactionDefinition.PROPAGATION_REQUIRED:
            rc = "PROPAGATION_REQUIRED";
            break;
        case TransactionDefinition.PROPAGATION_REQUIRES_NEW:
            rc = "PROPAGATION_REQUIRES_NEW";
            break;
        case TransactionDefinition.PROPAGATION_SUPPORTS:
            rc = "PROPAGATION_SUPPORTS";
            break;
        default:
            rc = "UNKNOWN";
        }
        return rc;
    }

}
