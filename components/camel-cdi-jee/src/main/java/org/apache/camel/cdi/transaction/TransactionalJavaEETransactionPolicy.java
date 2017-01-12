package org.apache.camel.cdi.transaction;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.apache.camel.CamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper methods for transaction handling
 */
public abstract class TransactionalJavaEETransactionPolicy extends JavaEETransactionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionalJavaEETransactionPolicy.class);

    protected void runWithTransaction(final Runnable runnable, final boolean isNew) throws Throwable {

        if (isNew) {
            begin();
        }
        try {
            runnable.run();
        } catch (RuntimeException e) {
            rollback(isNew);
            throw e;
        } catch (Error e) {
            rollback(isNew);
            throw e;
        } catch (Throwable e) {
            rollback(isNew);
            throw e;
        }
        if (isNew) {
            commit();
        }
        return;

    }

    private void begin() throws Exception {

        transactionManager.begin();

    }

    private void commit() throws Exception {

        try {
            transactionManager.commit();
        } catch (HeuristicMixedException e) {
            throw new CamelException("Unable to commit transaction", e);
        } catch (HeuristicRollbackException e) {
            throw new CamelException("Unable to commit transaction", e);
        } catch (RollbackException e) {
            throw new CamelException("Unable to commit transaction", e);
        } catch (SystemException e) {
            throw new CamelException("Unable to commit transaction", e);
        } catch (RuntimeException e) {
            rollback(true);
            throw e;
        } catch (Exception e) {
            rollback(true);
            throw e;
        } catch (Error e) {
            rollback(true);
            throw e;
        }

    }

    protected void rollback(boolean isNew) throws Exception {

        try {

            if (isNew) {
                transactionManager.rollback();
            } else {
                transactionManager.setRollbackOnly();
            }

        } catch (Throwable e) {

            LOG.warn("Could not rollback transaction!", e);

        }

    }

    protected Transaction suspendTransaction() throws Exception {

        return transactionManager.suspend();

    }

    protected void resumeTransaction(final Transaction suspendedTransaction) {

        if (suspendedTransaction == null) {
            return;
        }

        try {
            transactionManager.resume(suspendedTransaction);
        } catch (Throwable e) {
            LOG.warn("Could not resume transaction!", e);
        }

    }

    protected boolean hasActiveTransaction() throws Exception {

        return transactionManager.getStatus() != Status.STATUS_MARKED_ROLLBACK
                && transactionManager.getStatus() != Status.STATUS_NO_TRANSACTION;

    }

}
