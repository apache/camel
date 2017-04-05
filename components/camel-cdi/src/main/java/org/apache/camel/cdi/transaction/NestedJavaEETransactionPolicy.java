package org.apache.camel.cdi.transaction;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;
import javax.transaction.Transaction;

@Named("PROPAGATION_NESTED")
public class NestedJavaEETransactionPolicy extends TransactionalJavaEETransactionPolicy {

    private static final Logger logger = Logger.getLogger(NestedJavaEETransactionPolicy.class.getCanonicalName());

    @Override
    public void run(final Runnable runnable) throws Throwable {

        Transaction suspendedTransaction = null;
        boolean rollback = false;
        try {

            suspendedTransaction = suspendTransaction();
            runWithTransaction(runnable, true);

        } catch (Throwable e) {
            rollback = true;
            throw e;
        } finally {
            try {
                if (rollback) {
                    rollback(false);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not do rollback of outer transaction", e);
            }
            try {
                resumeTransaction(suspendedTransaction);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not resume outer transaction", e);
            }
        }

    }

}
