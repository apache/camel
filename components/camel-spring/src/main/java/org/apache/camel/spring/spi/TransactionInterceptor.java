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
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The <a href="http://activemq.apache.org/camel/transactional-client.html">Transactional Client</a>
 * EIP pattern.
 *
 * @version $Revision$
 */
public class TransactionInterceptor extends DelegateProcessor {
    public static final ExchangeProperty<Boolean> TRANSACTED =
        new ExchangeProperty<Boolean>("transacted", "org.apache.camel.transacted", Boolean.class);
    private static final transient Log LOG = LogFactory.getLog(TransactionInterceptor.class);
    private final TransactionTemplate transactionTemplate;
    private ThreadLocal<RedeliveryData> previousRollback = new ThreadLocal<RedeliveryData>() {
        @Override
        protected RedeliveryData initialValue() {
            return new RedeliveryData();
        }
    };
    private RedeliveryPolicy redeliveryPolicy;

    public TransactionInterceptor(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public TransactionInterceptor(Processor processor, TransactionTemplate transactionTemplate) {
        super(processor);
        this.transactionTemplate = transactionTemplate;
    }

    public TransactionInterceptor(Processor processor, TransactionTemplate transactionTemplate, RedeliveryPolicy redeliveryPolicy) {
        this(processor, transactionTemplate);
        this.redeliveryPolicy = redeliveryPolicy;
    }

    @Override
    public String toString() {
        return "TransactionInterceptor:"
            + propagationBehaviorToString(transactionTemplate.getPropagationBehavior())
            + "[" + getProcessor() + "]";
    }

    public void process(final Exchange exchange) {
        LOG.debug("Transaction begin");

        final RedeliveryData redeliveryData = previousRollback.get();

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // TODO: The delay is in some cases never triggered - see CAMEL-663
                if (redeliveryPolicy != null && redeliveryData.previousRollback) {
                    // lets delay
                    redeliveryData.redeliveryDelay = redeliveryPolicy.sleep(redeliveryData.redeliveryDelay);
                }

                // wrapper exception to throw if the exchange failed
                // IMPORTANT: Must be a runtime exception to let Spring regard it as to do "rollback"
                RuntimeCamelException rce = null;

                boolean activeTx = false;
                try {
                    // find out if there is an actual transaction alive, and thus we are in transacted mode
                    activeTx = TransactionSynchronizationManager.isActualTransactionActive();
                    if (!activeTx) {
                        activeTx = status.isNewTransaction() && !status.isCompleted();
                        if (!activeTx) {
                            if (DefaultTransactionStatus.class.isAssignableFrom(status.getClass())) {
                                DefaultTransactionStatus defStatus = DefaultTransactionStatus.class
                                    .cast(status);
                                activeTx = defStatus.hasTransaction() && !status.isCompleted();
                            }
                        }
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Is actual transaction active: " + activeTx);
                    }

                    // okay mark the exchange as transacted, then the DeadLetterChannel or others know
                    // its an transacted exchange
                    if (activeTx) {
                        TRANSACTED.set(exchange, Boolean.TRUE);
                    }

                    // process the exchange
                    processNext(exchange);

                    // wrap if the exchange failed with an exception
                    if (exchange.getException() != null) {
                        rce = new RuntimeCamelException(exchange.getException());
                    }
                } catch (Exception e) {
                     // wrap if the exchange threw an exception
                    rce = new RuntimeCamelException(e);
                }

                // rehrow exception if the exchange failed
                if (rce != null) {
                    redeliveryData.previousRollback = true;
                    if (activeTx) {
                        status.setRollbackOnly();
                        LOG.debug("Transaction rollback");
                    }
                    throw rce;
                }
            }
        });

        redeliveryData.previousRollback = false;
        redeliveryData.redeliveryDelay = 0L;

        LOG.debug("Transaction commit");
    }


    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    protected static class RedeliveryData {
        boolean previousRollback;
        long redeliveryDelay;
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
