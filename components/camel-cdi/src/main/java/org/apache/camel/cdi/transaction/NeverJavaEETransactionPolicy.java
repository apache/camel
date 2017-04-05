package org.apache.camel.cdi.transaction;

import javax.inject.Named;

@Named("PROPAGATION_NEVER")
public class NeverJavaEETransactionPolicy extends TransactionalJavaEETransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Exception {

        if (hasActiveTransaction()) {
            throw new IllegalStateException(
                    "Policy 'PROPAGATION_NEVER' is configured but an active transaction was found!");
        }

    }

}
