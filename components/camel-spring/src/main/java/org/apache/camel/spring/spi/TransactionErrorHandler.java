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
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The <a href="http://camel.apache.org/transactional-client.html">Transactional Client</a>
 * EIP pattern.
 *
 * @version $Revision$
 */
public class TransactionErrorHandler extends RedeliveryErrorHandler {

    private static final transient Log LOG = LogFactory.getLog(TransactionErrorHandler.class);
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
            // if no output then dont do any description
            return "";
        }
        return "TransactionErrorHandler:"
                + propagationBehaviorToString(transactionTemplate.getPropagationBehavior())
                + "[" + getOutput() + "]";
    }

    public void process(final Exchange exchange) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Transaction error handler is processing: " + exchange);
        }

        // just to let the stacktrace reveal that this is a transaction error handler
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // wrapper exception to throw if the exchange failed
                // IMPORTANT: Must be a runtime exception to let Spring regard it as to do "rollback"
                TransactedRuntimeCamelException rce;

                // find out if there is an actual transaction alive, and thus we are in transacted mode
                boolean activeTx = TransactionSynchronizationManager.isActualTransactionActive();
                if (!activeTx) {
                    activeTx = status.isNewTransaction() && !status.isCompleted();
                    if (!activeTx) {
                        if (DefaultTransactionStatus.class.isAssignableFrom(status.getClass())) {
                            DefaultTransactionStatus defStatus = DefaultTransactionStatus.class.cast(status);
                            activeTx = defStatus.hasTransaction() && !status.isCompleted();
                        }
                    }
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Is actual transaction active: " + activeTx);
                }

                // okay mark the exchange as transacted, then the DeadLetterChannel or others know
                // its a transacted exchange
                if (activeTx) {
                    exchange.setProperty(Exchange.TRANSACTED, Boolean.TRUE);
                }

                try {
                    TransactionErrorHandler.super.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }

                // after handling and still an exception or marked as rollback only then rollback
                if (exchange.getException() != null || exchange.isRollbackOnly()) {
                    rce = wrapTransactedRuntimeException(exchange.getException());

                    if (activeTx && !status.isRollbackOnly()) {
                        status.setRollbackOnly();
                        if (LOG.isDebugEnabled()) {
                            if (rce != null) {
                                LOG.debug("Setting transaction to rollbackOnly due to exception being thrown: " + rce.getMessage());
                            } else {
                                LOG.debug("Setting transaction to rollbackOnly as Exchange was marked as rollback only");
                            }
                        }
                    }

                    // rethrow if an exception occured
                    if (rce != null) {
                        throw rce;
                    }
                }
            }
        });

        log.trace("Transaction error handler done");
    }

    @Override
    protected boolean shouldHandleException(Exchange exchange) {
        boolean answer = false;
        if (exchange.getException() != null) {
            answer = true;
            // handle onException
            // but test beforehand if we have already handled it, if so we should not do it again
            if (exchange.getException() instanceof TransactedRuntimeCamelException) {
                TransactedRuntimeCamelException trce = exchange.getException(TransactedRuntimeCamelException.class);
                answer = !trce.isHandled();
            }
        }
        return answer;
    }

    protected TransactedRuntimeCamelException wrapTransactedRuntimeException(Exception exception) {
        if (exception instanceof TransactedRuntimeCamelException) {
            return (TransactedRuntimeCamelException) exception;
        } else {
            // Mark as handled so we dont want to handle the same exception twice or more in other
            // wrapped transaction error handlers in this route.
            // We need to mark this information in the exception as we need to propagage
            // the exception back by rehtrowing it. We cannot mark it on the exchange as Camel
            // uses copies of exchanges in its pipeline and the data isnt copied back in case
            // when an exception occured
            return new TransactedRuntimeCamelException(exception, true);
        }
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
