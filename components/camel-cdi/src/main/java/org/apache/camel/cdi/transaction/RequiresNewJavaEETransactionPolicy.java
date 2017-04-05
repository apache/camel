package org.apache.camel.cdi.transaction;

import javax.inject.Named;
import javax.transaction.Transaction;

@Named("PROPAGATION_REQUIRES_NEW")
public class RequiresNewJavaEETransactionPolicy extends TransactionalJavaEETransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Throwable {

        Transaction suspendedTransaction = null;
        try {

            suspendedTransaction = suspendTransaction();
            runWithTransaction(runnable, true);

        } finally {
            resumeTransaction(suspendedTransaction);
        }

    }

}
