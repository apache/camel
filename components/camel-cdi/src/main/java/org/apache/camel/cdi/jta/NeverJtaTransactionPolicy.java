package org.apache.camel.cdi.jta;

import javax.inject.Named;

@Named("PROPAGATION_NEVER")
public class NeverJtaTransactionPolicy extends TransactionalJtaTransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Exception {

        if (hasActiveTransaction()) {
            throw new IllegalStateException(
                    "Policy 'PROPAGATION_NEVER' is configured but an active transaction was found!");
        }

    }

}
