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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.errorhandler.ExceptionPolicyStrategy;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spi.CamelLogger;

/**
 * This error handler does redelivering. If the transaction fails it can be
 * retried if configured to do so. In the Spring implementation redelivering is
 * done within the transaction which is not appropriate in JTA since every error
 * breaks the current transaction.
 */
public class JtaTransactionErrorHandler extends RedeliveryErrorHandler {

    public JtaTransactionErrorHandler(CamelContext camelContext, Processor output, CamelLogger logger,
            Processor redeliveryProcessor, RedeliveryPolicy redeliveryPolicy,
            ExceptionPolicyStrategy exceptionPolicyStrategy, JtaTransactionPolicy transactionPolicy,
            Predicate retryWhile, ScheduledExecutorService executorService, LoggingLevel rollbackLoggingLevel,
            Processor onExceptionOccurredProcessor) {

        super(camelContext,
                new TransactionErrorHandler(camelContext,
                        output,
                        exceptionPolicyStrategy,
                        transactionPolicy,
                        executorService,
                        rollbackLoggingLevel),
                logger,
                redeliveryProcessor,
                redeliveryPolicy,
                null,
                null,
                false,
                false,
                false,
                retryWhile,
                executorService,
                null,
                onExceptionOccurredProcessor);
    }
}
