package org.apache.camel.cdi.jta;

import javax.inject.Named;
import javax.transaction.Transaction;

@Named("PROPAGATION_REQUIRES_NEW")
public class RequiresNewJtaTransactionPolicy extends TransactionalJtaTransactionPolicy {

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
