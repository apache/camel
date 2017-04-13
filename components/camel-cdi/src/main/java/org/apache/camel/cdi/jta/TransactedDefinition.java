package org.apache.camel.cdi.jta;

import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;

/**
 * Used to expose the method &apos;resolvePolicy&apos; used by
 * {@link JtaTransactionErrorHandlerBuilder} to resolve configured policy
 * references.
 */
public class TransactedDefinition extends org.apache.camel.model.TransactedDefinition {

    @Override
    public Policy resolvePolicy(RouteContext routeContext) {
        return super.resolvePolicy(routeContext);
    }

}
