package org.apache.camel.cdi.jta;

import javax.inject.Named;

@Named("PROPAGATION_SUPPORTS")
public class SupportsJtaTransactionPolicy extends TransactionalJtaTransactionPolicy {

    @Override
    public void run(final Runnable runnable) throws Throwable {

        runnable.run();

    }

}
