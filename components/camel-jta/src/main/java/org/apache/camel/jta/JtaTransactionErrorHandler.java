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
package org.apache.camel.jta;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This error handler does redelivering. If the transaction fails it can be retried if configured to do so. In the
 * Spring implementation redelivering is done within the transaction which is not appropriate in JTA since every error
 * breaks the current transaction.
 */
public class JtaTransactionErrorHandler extends RedeliveryErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JtaTransactionErrorHandler.class);
    private final JtaTransactionPolicy transactionPolicy;
    private final LoggingLevel rollbackLoggingLevel;

    public JtaTransactionErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger,
                                      Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy,
                                      JtaTransactionPolicy transactionPolicy,
                                      Predicate retryWhile, ScheduledExecutorService executorService,
                                      LoggingLevel rollbackLoggingLevel,
                                      Processor onExceptionOccurredProcessor) {
        super(camelContext,
              new TransactionErrorHandler(camelContext, output, transactionPolicy, rollbackLoggingLevel),
              logger, redeliveryProcessor, redeliveryPolicy,
              null, null, false, false, false,
              retryWhile, executorService, null, onExceptionOccurredProcessor);
        this.transactionPolicy = transactionPolicy;
        this.rollbackLoggingLevel = rollbackLoggingLevel;
    }

    @Override
    public ErrorHandler clone(Processor output) {
        JtaTransactionErrorHandler answer = new JtaTransactionErrorHandler(
                camelContext, output, logger, redeliveryProcessor, redeliveryPolicy, transactionPolicy, retryWhilePolicy,
                executorService, rollbackLoggingLevel, onExceptionProcessor);
        // shallow clone is okay as we do not mutate these
        if (exceptionPolicies != null) {
            answer.exceptionPolicies = exceptionPolicies;
        }
        return answer;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (!exchange.isTransacted()) {
            try {
                LOG.debug("Mark {} as transacted", exchange);
                exchange.getUnitOfWork().beginTransactedBy("camel-jta");
                return super.process(exchange, callback);
            } finally {
                exchange.getUnitOfWork().endTransactedBy("camel-jta");
            }
        }

        return super.process(exchange, callback);
    }
}
