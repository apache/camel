package org.apache.camel.cdi.transaction;

import javax.inject.Named;

@Named("PROPAGATION_REQUIRED")
public class RequiredJtaTransactionPolicy extends TransactionalJtaTransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Throwable {

        runWithTransaction(runnable, !hasActiveTransaction());

    }

}
