package org.apache.camel.cdi.transaction;

import javax.inject.Named;

@Named("PROPAGATION_MANDATORY")
public class MandatoryJtaTransactionPolicy extends TransactionalJtaTransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Exception {

        if (!hasActiveTransaction()) {
            throw new IllegalStateException(
                    "Policy 'PROPAGATION_MANDATORY' is configured but no active transaction was found!");
        }

    }

}
