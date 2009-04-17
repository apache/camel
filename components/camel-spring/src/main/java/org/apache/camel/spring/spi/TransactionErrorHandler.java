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
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.processor.ErrorHandlerSupport;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ServiceHelper;
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
public class TransactionErrorHandler extends ErrorHandlerSupport {

    private static final transient Log LOG = LogFactory.getLog(TransactionErrorHandler.class);
    private final TransactionTemplate transactionTemplate;
    private Processor output;

    public TransactionErrorHandler(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public TransactionErrorHandler(TransactionTemplate transactionTemplate, Processor output,
                                   ExceptionPolicyStrategy exceptionPolicy) {
        this.transactionTemplate = transactionTemplate;
        setOutput(output);
        setExceptionPolicy(exceptionPolicy);
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

    public void process(final Exchange exchange) {
        if (output == null) {
            // no output then just return as nothing to wrap in a transaction
            return;
        }

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
                    // process the exchange
                    output.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }

                // an exception occured maybe an onException can handle it
                if (exchange.getException() != null) {
                    // handle onException
                    // but test beforehand if we have already handled it, if so we should not do it again
                    boolean handled = false;
                    if (exchange.getException() instanceof TransactedRuntimeCamelException) {
                        TransactedRuntimeCamelException trce = exchange.getException(TransactedRuntimeCamelException.class);
                        handled = trce.isHandled();
                    }
                    if (!handled) {
                        // not handled before so handle it once
                        handleException(exchange);
                    }
                }

                // after handling and still an exception or marked as rollback only then rollback
                if (exchange.getException() != null || exchange.isRollbackOnly()) {
                    rce = wrapTransactedRuntimeException(exchange.getException());

                    if (activeTx) {
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

    /**
     * Handles when an exception occured during processing. Is used to let the exception policy
     * deal with it, eg letting an onException handle it.
     *
     * @param exchange  the current exchange
     */
    protected void handleException(Exchange exchange) {
        Exception e = exchange.getException();
        // store the original caused exception in a property, so we can restore it later
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);

        // find the error handler to use (if any)
        OnExceptionDefinition exceptionPolicy = getExceptionPolicy(exchange, e);
        if (exceptionPolicy != null) {
            Predicate handledPredicate = exceptionPolicy.getHandledPolicy();

            Processor processor = exceptionPolicy.getErrorHandler();
            prepareExchangeBeforeOnException(exchange);
            if (processor != null) {
                deliverToFaultProcessor(exchange, processor);
            }
            prepareExchangeAfterOnException(exchange, handledPredicate);
        }
    }

    private void deliverToFaultProcessor(Exchange exchange, Processor faultProcessor) {
        try {
            faultProcessor.process(exchange);
        } catch (Exception e) {
            // fault processor also failed so set the exception
            exchange.setException(e);
        }
    }

    private void prepareExchangeBeforeOnException(Exchange exchange) {
        // okay lower the exception as we are handling it by onException
        if (exchange.getException() != null) {
            exchange.setException(null);
        }

        // clear rollback flags
        exchange.setProperty(Exchange.ROLLBACK_ONLY, null);

        // reset cached streams so they can be read again
        MessageHelper.resetStreamCache(exchange.getIn());
    }

    private void prepareExchangeAfterOnException(Exchange exchange, Predicate handledPredicate) {
        if (handledPredicate == null || !handledPredicate.matches(exchange)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("This exchange is not handled so its marked as rollback only: " + exchange);
            }
            // exception not handled, put exception back in the exchange
            exchange.setException(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
            // mark as rollback so we dont do multiple onException for this one
            exchange.setProperty(Exchange.ROLLBACK_ONLY, Boolean.TRUE);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("This exchange is handled so its marked as not failed: " + exchange);
            }
            exchange.setProperty(Exchange.EXCEPTION_HANDLED, Boolean.TRUE);
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

    protected void doStart() throws Exception {
        ServiceHelper.startServices(output);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(output);
    }

    /**
     * Returns the output processor
     */
    public Processor getOutput() {
        return output;
    }

    public void setOutput(Processor output) {
        this.output = output;
    }

}
