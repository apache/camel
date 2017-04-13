package org.apache.camel.cdi.jta;

import javax.inject.Named;

@Named("PROPAGATION_REQUIRED")
public class RequiredJtaTransactionPolicy extends TransactionalJtaTransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Throwable {

        runWithTransaction(runnable, !hasActiveTransaction());

    }

}
