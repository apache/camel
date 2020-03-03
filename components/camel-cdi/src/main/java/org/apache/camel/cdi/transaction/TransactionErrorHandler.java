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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import javax.transaction.TransactionRolledbackException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.errorhandler.ErrorHandlerSupport;
import org.apache.camel.processor.errorhandler.ExceptionPolicyStrategy;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does transactional execution according given policy. This class is based on
 * {@link org.apache.camel.spring.spi.TransactionErrorHandler} excluding
 * redelivery functionality. In the Spring implementation redelivering is done
 * within the transaction which is not appropriate in JTA since every error
 * breaks the current transaction.
 */
public class TransactionErrorHandler extends ErrorHandlerSupport
        implements AsyncProcessor, ShutdownPrepared, Navigate<Processor> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionErrorHandler.class);

    protected final Processor output;

    protected volatile boolean preparingShutdown;

    private ExceptionPolicyStrategy exceptionPolicy;

    private JtaTransactionPolicy transactionPolicy;

    private final String transactionKey;

    private final LoggingLevel rollbackLoggingLevel;

    /**
     * Creates the transaction error handler.
     *
     * @param camelContext
     *            the camel context
     * @param output
     *            outer processor that should use this default error handler
     * @param exceptionPolicyStrategy
     *            strategy for onException handling
     * @param transactionPolicy
     *            the transaction policy
     * @param executorService
     *            the {@link java.util.concurrent.ScheduledExecutorService} to
     *            be used for redelivery thread pool. Can be <tt>null</tt>.
     * @param rollbackLoggingLevel
     *            logging level to use for logging transaction rollback occurred
     */
    public TransactionErrorHandler(CamelContext camelContext, Processor output,
            ExceptionPolicyStrategy exceptionPolicyStrategy, JtaTransactionPolicy transactionPolicy,
            ScheduledExecutorService executorService, LoggingLevel rollbackLoggingLevel) {
        this.output = output;
        this.transactionPolicy = transactionPolicy;
        this.rollbackLoggingLevel = rollbackLoggingLevel;
        this.transactionKey = ObjectHelper.getIdentityHashCode(transactionPolicy);

        setExceptionPolicy(exceptionPolicyStrategy);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // we have to run this synchronously as a JTA Transaction does *not*
        // support using multiple threads to span a transaction
        if (exchange.getUnitOfWork().isTransactedBy(transactionKey)) {
            // already transacted by this transaction template
            // so lets just let the error handler process it
            processByErrorHandler(exchange);
        } else {
            // not yet wrapped in transaction so lets do that
            // and then have it invoke the error handler from within that
            // transaction
            processInTransaction(exchange);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // invoke this synchronous method as JTA Transaction does *not*
        // support using multiple threads to span a transaction
        try {
            process(exchange);
        } catch (Throwable e) {
            exchange.setException(e);
        }

        // notify callback we are done synchronously
        callback.done(true);
        return true;
    }

    @Override
    public CompletableFuture<Exchange> processAsync(Exchange exchange) {
        AsyncCallbackToCompletableFutureAdapter<Exchange> callback = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
        process(exchange, callback);
        return callback.getFuture();
    }

    protected void processInTransaction(final Exchange exchange) throws Exception {
        // is the exchange redelivered, for example JMS brokers support such details
        final String redelivered = Boolean.toString(exchange.isExternalRedelivered());
        final String ids = ExchangeHelper.logIds(exchange);

        try {
            // mark the beginning of this transaction boundary
            exchange.getUnitOfWork().beginTransactedBy(transactionKey);
            // do in transaction
            logTransactionBegin(redelivered, ids);
            doInTransactionTemplate(exchange);
            logTransactionCommit(redelivered, ids);
        } catch (TransactionRolledbackException e) {
            // do not set as exception, as its just a dummy exception to force
            // spring TX to rollback
            logTransactionRollback(redelivered, ids, null, true);
        } catch (Throwable e) {
            exchange.setException(e);
            logTransactionRollback(redelivered, ids, e, false);
        } finally {
            // mark the end of this transaction boundary
            exchange.getUnitOfWork().endTransactedBy(transactionKey);
        }

        // if it was a local rollback only then remove its marker so outer
        // transaction wont see the marker
        boolean onlyLast = exchange.isRollbackOnlyLast();
        exchange.setRollbackOnlyLast(false);
        if (onlyLast) {
            // we only want this logged at debug level
            if (LOG.isDebugEnabled()) {
                // log exception if there was a cause exception so we have the
                // stack trace
                Exception cause = exchange.getException();
                if (cause != null) {
                    LOG.debug("Transaction rollback ({}) redelivered({}) for {} "
                        + "due exchange was marked for rollbackOnlyLast and caught: ",
                        transactionKey, redelivered, ids, cause);
                } else {
                    LOG.debug("Transaction rollback ({}) redelivered({}) for {} "
                        + "due exchange was marked for rollbackOnlyLast",
                        transactionKey, redelivered, ids);
                }
            }
            // remove caused exception due we was marked as rollback only last
            // so by removing the exception, any outer transaction will not be
            // affected
            exchange.setException(null);
        }
    }

    public void setTransactionPolicy(JtaTransactionPolicy transactionPolicy) {
        this.transactionPolicy = transactionPolicy;
    }

    protected void doInTransactionTemplate(final Exchange exchange) throws Throwable {
        // spring transaction template is working best with rollback if you
        // throw it a runtime exception
        // otherwise it may not rollback messages send to JMS queues etc.
        transactionPolicy.run(new JtaTransactionPolicy.Runnable() {

            @Override
            public void run() throws Throwable {
                // wrapper exception to throw if the exchange failed
                // IMPORTANT: Must be a runtime exception to let Spring regard
                // it as to do "rollback"
                Throwable rce;

                // and now let process the exchange by the error handler
                processByErrorHandler(exchange);

                // after handling and still an exception or marked as rollback
                // only then rollback
                if (exchange.getException() != null || exchange.isRollbackOnly()) {

                    // wrap exception in transacted exception
                    if (exchange.getException() != null) {
                        rce = exchange.getException();
                    } else {
                        // create dummy exception to force spring transaction
                        // manager to rollback
                        rce = new TransactionRolledbackException();
                    }

                    // throw runtime exception to force rollback (which works
                    // best to rollback with Spring transaction manager)
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Throwing runtime exception to force transaction to rollback on {}",
                                transactionPolicy);
                    }
                    throw rce;
                }
            }
        });
    }

    /**
     * Processes the {@link Exchange} using the error handler.
     * <p/>
     * This implementation will invoke ensure this occurs synchronously, that
     * means if the async routing engine did kick in, then this implementation
     * will wait for the task to complete before it continues.
     *
     * @param exchange
     *            the exchange
     */
    protected void processByErrorHandler(final Exchange exchange) {
        try {
            output.process(exchange);
        } catch (Throwable e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Logs the transaction begin
     */
    private void logTransactionBegin(String redelivered, String ids) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Transaction begin ({}) redelivered({}) for {})",
                    transactionKey, redelivered, ids);
        }
    }

    /**
     * Logs the transaction commit
     */
    private void logTransactionCommit(String redelivered, String ids) {
        if ("true".equals(redelivered)) {
            // okay its a redelivered message so log at INFO level if
            // rollbackLoggingLevel is INFO or higher
            // this allows people to know that the redelivered message was
            // committed this time
            if (rollbackLoggingLevel == LoggingLevel.INFO || rollbackLoggingLevel == LoggingLevel.WARN
                    || rollbackLoggingLevel == LoggingLevel.ERROR) {
                LOG.info("Transaction commit ({}) redelivered({}) for {})",
                        transactionKey, redelivered, ids);
                // return after we have logged
                return;
            }
        }

        // log non redelivered by default at DEBUG level
        LOG.debug("Transaction commit ({}) redelivered({}) for {})", transactionKey, redelivered, ids);
    }

    /**
     * Logs the transaction rollback.
     */
    private void logTransactionRollback(String redelivered, String ids, Throwable e, boolean rollbackOnly) {
        if (rollbackLoggingLevel == LoggingLevel.OFF) {
            return;
        } else if (rollbackLoggingLevel == LoggingLevel.ERROR && LOG.isErrorEnabled()) {
            if (rollbackOnly) {
                LOG.error("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly",
                        transactionKey, redelivered, ids);
            } else {
                LOG.error("Transaction rollback ({}) redelivered({}) for {} caught: {}",
                        transactionKey, redelivered, ids, e.getMessage());
            }
        } else if (rollbackLoggingLevel == LoggingLevel.WARN && LOG.isWarnEnabled()) {
            if (rollbackOnly) {
                LOG.warn("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly",
                        transactionKey, redelivered, ids);
            } else {
                LOG.warn("Transaction rollback ({}) redelivered({}) for {} caught: {}",
                        transactionKey, redelivered, ids, e.getMessage());
            }
        } else if (rollbackLoggingLevel == LoggingLevel.INFO && LOG.isInfoEnabled()) {
            if (rollbackOnly) {
                LOG.info("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly",
                        transactionKey, redelivered, ids);
            } else {
                LOG.info("Transaction rollback ({}) redelivered({}) for {} caught: {}",
                        transactionKey, redelivered, ids, e.getMessage());
            }
        } else if (rollbackLoggingLevel == LoggingLevel.DEBUG && LOG.isDebugEnabled()) {
            if (rollbackOnly) {
                LOG.debug("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly",
                        transactionKey, redelivered, ids);
            } else {
                LOG.debug("Transaction rollback ({}) redelivered({}) for {} caught: {}",
                        transactionKey, redelivered, ids, e.getMessage());
            }
        } else if (rollbackLoggingLevel == LoggingLevel.TRACE && LOG.isTraceEnabled()) {
            if (rollbackOnly) {
                LOG.trace("Transaction rollback ({}) redelivered({}) for {} due exchange was marked for rollbackOnly",
                        transactionKey, redelivered, ids);
            } else {
                LOG.trace("Transaction rollback ({}) redelivered({}) for {} caught: {}",
                        transactionKey, redelivered, ids, e.getMessage());
            }
        }
    }

    @Override
    public void setExceptionPolicy(ExceptionPolicyStrategy exceptionPolicy) {
        this.exceptionPolicy = exceptionPolicy;
    }

    public ExceptionPolicyStrategy getExceptionPolicy() {
        return exceptionPolicy;
    }

    @Override
    public Processor getOutput() {
        return output;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(output);
        preparingShutdown = false;
    }

    @Override
    protected void doStop() throws Exception {
        // noop, do not stop any services which we only do when shutting down
        // as the error handler can be context scoped, and should not stop in
        // case a route stops
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(output);
    }

    @Override
    public boolean supportTransacted() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return output != null;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>(1);
        answer.add(output);
        return answer;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // prepare for shutdown, eg do not allow redelivery if configured
        LOG.trace("Prepare shutdown on error handler {}", this);
        preparingShutdown = true;
    }
}
