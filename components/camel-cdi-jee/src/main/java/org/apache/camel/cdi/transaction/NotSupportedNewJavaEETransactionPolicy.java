package org.apache.camel.cdi.transaction;

import javax.inject.Named;
import javax.transaction.Transaction;

@Named("PROPAGATION_NOT_SUPPORTED")
public class NotSupportedNewJavaEETransactionPolicy extends TransactionalJavaEETransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Exception {

        Transaction suspendedTransaction = null;
        try {

            suspendedTransaction = suspendTransaction();
            runnable.run();

        } finally {
            resumeTransaction(suspendedTransaction);
        }

    }

}
