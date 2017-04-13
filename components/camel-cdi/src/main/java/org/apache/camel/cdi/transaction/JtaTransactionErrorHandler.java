package org.apache.camel.cdi.transaction;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.util.CamelLogger;

/**
 * This error handler does redelivering. If the transaction fails it can be
 * retried if configured to do so. In the Spring implementation redelivering is
 * done within the transaction which is not appropriate in JTA since every error
 * breaks the current transaction.
 */
public class JtaTransactionErrorHandler extends org.apache.camel.processor.RedeliveryErrorHandler {

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
                retryWhile,
                executorService,
                null,
                onExceptionOccurredProcessor);

    }

}
