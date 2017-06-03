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
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The <a href="http://camel.apache.org/transactional-client.html">Transactional Client</a>
 * EIP pattern.
 *
 * @version 
 */
public class TransactionErrorHandler extends RedeliveryErrorHandler {

    private final TransactionTemplate transactionTemplate;
    private final String transactionKey;
    private final LoggingLevel rollbackLoggingLevel;

    /**
     * Creates the transaction error handler.
     *
     * @param camelContext            the camel context
     * @param output                  outer processor that should use this default error handler
     * @param logger                  logger to use for logging failures and redelivery attempts
     * @param redeliveryProcessor     an optional processor to run before redelivery attempt
     * @param redeliveryPolicy        policy for redelivery
     * @param exceptionPolicyStrategy strategy for onException handling
     * @param transactionTemplate     the transaction template
     * @param retryWhile              retry while
     * @param executorService         the {@link java.util.concurrent.ScheduledExecutorService} to be used for redelivery thread pool. Can be <tt>null</tt>.
     * @param rollbackLoggingLevel    logging level to use for logging transaction rollback occurred
     * @param onExceptionOccurredProcessor  a custom {@link org.apache.camel.Processor} to process the {@link org.apache.camel.Exchange} just after an exception was thrown.
     */
    public TransactionErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger, 
            Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy, ExceptionPolicyStrategy exceptionPolicyStrategy,
            TransactionTemplate transactionTemplate, Predicate retryWhile, ScheduledExecutorService executorService,
            LoggingLevel rollbackLoggingLevel, Processor onExceptionOccurredProcessor) {

        super(camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, null, null, false, false, retryWhile,
                executorService, null, onExceptionOccurredProcessor);
        setExceptionPolicy(exceptionPolicyStrategy);
        this.transactionTemplate = transactionTemplate;
        this.rollbackLoggingLevel = rollbackLoggingLevel;
        this.transactionKey = ObjectHelper.getIdentityHashCode(transactionTemplate);
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
        if (transactionTemplate.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRES_NEW && exchange.getUnitOfWork().isTransactedBy(transactionKey)) {
            // already transacted by this transaction template
            // so lets just let the error handler process it
            processByErrorHandler(exchange);
        } else {
            // not yet wrapped in transaction so lets do that
            // and then have it invoke the error handler from within that transaction
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

    protected void processInTransaction(final Exchange exchange) throws Exception {
        // is the exchange redelivered, for example JMS brokers support such details
        Boolean externalRedelivered = exchange.isExternalRedelivered();
        final String redelivered = externalRedelivered != null ? externalRedelivered.toString() : "unknown";
        final String ids = ExchangeHelper.logIds(exchange);

        try {
            // mark the beginning of this transaction boundary
            exchange.getUnitOfWork().beginTransactedBy(transactionKey);

            // do in transaction
            logTransactionBegin(redelivered, ids);
            doInTransactionTemplate(exchange);
            logTransactionCommit(redelivered, ids);

        } catch (TransactionRollbackException e) {
            // do not set as exception, as its just a dummy exception to force spring TX to rollback
            logTransactionRollback(redelivered, ids, null, true);
        } catch (Throwable e) {
            exchange.setException(e);
            logTransactionRollback(redelivered, ids, e, false);
        } finally {
            // mark the end of this transaction boundary
            exchange.getUnitOfWork().endTransactedBy(transactionKey);
        }

        // if it was a local rollback only then remove its marker so outer transaction wont see the marker
        Boolean onlyLast = (Boolean) exchange.removeProperty(Exchange.ROLLBACK_ONLY_LAST);
        if (onlyLast != null && onlyLast) {
            // we only want this logged at debug level
            if (log.isDebugEnabled()) {
                // log exception if there was a cause exception so we have the stack trace
                Exception cause = exchange.getException();
                if (cause != null) {
                    log.debug("Transaction rollback (" + transactionKey + ") redelivered(" + redelivered + ") for "
                        + ids + " due exchange was marked for rollbackOnlyLast and caught: ", cause);
                } else {
                    log.debug("Transaction rollback ({}) redelivered({}) for {} "
                            + "due exchange was marked for rollbackOnlyLast", new Object[]{transactionKey, redelivered, ids});
                }
            }
            // remove caused exception due we was marked as rollback only last
            // so by removing the exception, any outer transaction will not be affected
            exchange.setException(null);
        }
    }

    protected void doInTransactionTemplate(final Exchange exchange) {

        // spring transaction template is working best with rollback if you throw it a runtime exception
        // otherwise it may not rollback messages send to JMS queues etc.

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // wrapper exception to throw if the exchange failed
                // IMPORTANT: Must be a runtime exception to let Spring regard it as to do "rollback"
                RuntimeException rce;

                // and now let process the exchange by the error handler
                processByErrorHandler(exchange);

                // after handling and still an exception or marked as rollback only then rollback
                if (exchange.getException() != null || exchange.isRollbackOnly()) {

                    // wrap exception in transacted exception
                    if (exchange.getException() != null) {
                        rce = ObjectHelper.wrapRuntimeCamelException(exchange.getException());
                    } else {
                        // create dummy exception to force spring transaction manager to rollback
                        rce = new TransactionRollbackException();
                    }

                    if (!status.isRollbackOnly()) {
                        status.setRollbackOnly();
                    }

                    // throw runtime exception to force rollback (which works best to rollback with Spring transaction manager)
                    if (log.isTraceEnabled()) {
                        log.trace("Throwing runtime exception to force transaction to rollback on {}", transactionTemplate.getName());
                    }
                    throw rce;
                }
            }
        });
    }

    /**
     * Processes the {@link Exchange} using the error handler.
     * <p/>
     * This implementation will invoke ensure this occurs synchronously, that means if the async routing engine
     * did kick in, then this implementation will wait for the task to complete before it continues.
     *
     * @param exchange the exchange
     */
    protected void processByErrorHandler(final Exchange exchange) {
        final CountDownLatch latch = new CountDownLatch(1);
        boolean sync = super.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                if (!doneSync) {
                    log.trace("Asynchronous callback received for exchangeId: {}", exchange.getExchangeId());
                    latch.countDown();
                }
            }

            @Override
            public String toString() {
                return "Done " + TransactionErrorHandler.this.toString();
            }
        });
        if (!sync) {
            log.trace("Waiting for asynchronous callback before continuing for exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);
            try {
                latch.await();
            } catch (InterruptedException e) {
                exchange.setException(e);
            }
            log.trace("Asynchronous callback received, will continue routing exchangeId: {} -> {}",
                    exchange.getExchangeId(), exchange);
        }
    }

    /**
     * Logs the transaction begin
     */
    private void logTransactionBegin(String redelivered, String ids) {
        if (log.isDebugEnabled()) {
            log.debug("Transaction begin ({}) redelivered({}) for {})", new Object[]{transactionKey, redelivered, ids});
        }
    }

    /**
     * Logs the transaction commit
     */
    private void logTransactionCommit(String redelivered, String ids) {
        if ("true".equals(redelivered)) {
            // okay its a redelivered message so log at INFO level if rollbackLoggingLevel is INFO or higher
            // this allows people to know that the redelivered message was committed this time
            if (rollbackLoggingLevel == LoggingLevel.INFO || rollbackLoggingLevel == LoggingLevel.WARN || rollbackLoggingLevel == LoggingLevel.ERROR) {
                log.info("Transaction commit ({}) redelivered({}) for {})", new Object[]{transactionKey, redelivered, ids});
                // return after we have logged
                return;
            }
        }

        // log non redelivered by default at DEBUG level
        log.debug("Transaction commit ({}) redelivered({}) for {})", new Object[]{transactionKey, redelivered, ids});
    }

    /**
     * Logs the transaction rollback.
     */
    private void logTransactionRollback(String redelivered, String ids, Throwable e, boolean rollbackOnly) {
        if (rollbackLoggingLevel == LoggingLevel.OFF) {
            return;
        } else if (rollbackLoggingLevel == LoggingLevel.ERROR && log.isErrorEnabled()) {
            if (rollbackOnly) {
                log.error("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly", new Object[]{transactionKey, redelivered, ids});
            } else {
                log.error("Transaction rollback ({}) redelivered({}) for {} caught: {}", new Object[]{transactionKey, redelivered, ids, e.getMessage()});
            }
        } else if (rollbackLoggingLevel == LoggingLevel.WARN && log.isWarnEnabled()) {
            if (rollbackOnly) {
                log.warn("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly", new Object[]{transactionKey, redelivered, ids});
            } else {
                log.warn("Transaction rollback ({}) redelivered({}) for {} caught: {}", new Object[]{transactionKey, redelivered, ids, e.getMessage()});
            }
        } else if (rollbackLoggingLevel == LoggingLevel.INFO && log.isInfoEnabled()) {
            if (rollbackOnly) {
                log.info("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly", new Object[]{transactionKey, redelivered, ids});
            } else {
                log.info("Transaction rollback ({}) redelivered({}) for {} caught: {}", new Object[]{transactionKey, redelivered, ids, e.getMessage()});
            }
        } else if (rollbackLoggingLevel == LoggingLevel.DEBUG && log.isDebugEnabled()) {
            if (rollbackOnly) {
                log.debug("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly", new Object[]{transactionKey, redelivered, ids});
            } else {
                log.debug("Transaction rollback ({}) redelivered({}) for {} caught: {}", new Object[]{transactionKey, redelivered, ids, e.getMessage()});
            }
        } else if (rollbackLoggingLevel == LoggingLevel.TRACE && log.isTraceEnabled()) {
            if (rollbackOnly) {
                log.trace("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly", new Object[]{transactionKey, redelivered, ids});
            } else {
                log.trace("Transaction rollback ({}) redelivered({}) for {} caught: {}", new Object[]{transactionKey, redelivered, ids, e.getMessage()});
            }
        }
    }

    private static String propagationBehaviorToString(int propagationBehavior) {
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
