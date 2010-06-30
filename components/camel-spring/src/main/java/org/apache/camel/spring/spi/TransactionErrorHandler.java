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

import java.util.concurrent.CountDownLatch;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
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
     * @param camelContext            the camel context
     * @param output                  outer processor that should use this default error handler
     * @param logger                  logger to use for logging failures and redelivery attempts
     * @param redeliveryProcessor     an optional processor to run before redelivery attempt
     * @param redeliveryPolicy        policy for redelivery
     * @param handledPolicy           policy for handling failed exception that are moved to the dead letter queue
     * @param exceptionPolicyStrategy strategy for onException handling
     * @param transactionTemplate     the transaction template
     */
    public TransactionErrorHandler(CamelContext camelContext, Processor output, Logger logger, Processor redeliveryProcessor,
                                   RedeliveryPolicy redeliveryPolicy, Predicate handledPolicy,
                                   ExceptionPolicyStrategy exceptionPolicyStrategy, TransactionTemplate transactionTemplate) {
        super(camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, handledPolicy, null, null, false);
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

    @Override
    public void process(Exchange exchange) throws Exception {
        // we have to run this synchronously as Spring Transaction does *not* support
        // using multiple threads to span a transaction
        if (exchange.getUnitOfWork().isTransactedBy(transactionTemplate)) {
            // already transacted by this transaction template
            // so lets just let the regular default error handler process it
            processByRegularErrorHandler(exchange);
        } else {
            // not yet wrapped in transaction so lets do that
            processInTransaction(exchange);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // invoke ths synchronous method as Spring Transaction does *not* support
        // using multiple threads to span a transaction
        try {
            process(exchange);
        } catch (Throwable e) {
            exchange.setException(e);
        }

        // notify callback we are done synchronously
        callback.done(true);
        return true;
    }

    protected void processByRegularErrorHandler(Exchange exchange) throws Exception {
        // must invoke the async method and provide an empty callback
        // to have it process by the error handler (because we invoke super)
        super.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // noop
            }
        });
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
                // we have to wait if the async routing engine took over, because transactions have to be done in
                // the same thread (Spring TransactionManager) so by waiting until the async routing is done
                // will let us be able to continue routing thereafter in the same thread context
                final CountDownLatch latch = new CountDownLatch(1);
                boolean sync = TransactionErrorHandler.super.process(exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        if (!doneSync) {
                            if (log.isTraceEnabled()) {
                                log.trace("Asynchronous callback received for exchangeId: " + exchange.getExchangeId());
                            }
                            latch.countDown();
                        }
                    }

                    @Override
                    public String toString() {
                        return "Done TransactionErrorHandler";
                    }
                });
                if (!sync) {
                    if (log.isTraceEnabled()) {
                        log.trace("Waiting for asynchronous callback before continuing for exchangeId: " + exchange.getExchangeId() + " -> " + exchange);
                    }
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        if (log.isDebugEnabled()) {
                            log.debug("Interrupted while waiting for asynchronous callback for exchangeId: " + exchange.getExchangeId(), e);
                        }
                        // we may be shutting down etc., so set exception
                        if (exchange.getException() == null) {
                            exchange.setException(e);
                        }
                    }
                    if (log.isTraceEnabled()) {
                        log.trace("Asynchronous callback received, will continue routing exchangeId: " + exchange.getExchangeId() + " -> " + exchange);
                    }
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
