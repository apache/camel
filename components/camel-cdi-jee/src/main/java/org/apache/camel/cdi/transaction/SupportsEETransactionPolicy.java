package org.apache.camel.cdi.transaction;

import javax.inject.Named;

@Named("PROPAGATION_SUPPORTS")
public class SupportsEETransactionPolicy extends TransactionalJavaEETransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Exception {

        runnable.run();

    }

}
