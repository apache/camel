package org.apache.camel.cdi;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.jta.JtaTransactionErrorHandlerBuilder;

/**
 * An extension of the {@link RouteBuilder} to provide some additional helper
 * methods
 *
 * @version
 */
public abstract class JtaRouteBuilder extends RouteBuilder {

    /**
     * Creates a transaction error handler that will lookup in application
     * context for an exiting transaction manager.
     *
     * @return the created error handler
     */
    public JtaTransactionErrorHandlerBuilder transactionErrorHandler() {
        return new JtaTransactionErrorHandlerBuilder();
    }

}
