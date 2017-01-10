package org.apache.camel.cdi.transaction;

import javax.inject.Named;

@Named("PROPAGATION_REQUIRED")
public class RequiredJavaEETransactionPolicy extends TransactionalJavaEETransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Exception {

        runWithTransaction(runnable, !hasActiveTransaction());

    }

}
