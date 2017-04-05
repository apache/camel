package org.apache.camel.cdi;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.transaction.JavaEETransactionErrorHandlerBuilder;

/**
 * An extension of the {@link RouteBuilder} to provide some additional helper
 * methods
 *
 * @version
 */
public abstract class JavaEERouteBuilder extends RouteBuilder {

    /**
     * Creates a transaction error handler that will lookup in application
     * context for an exiting transaction manager.
     *
     * @return the created error handler
     */
    public JavaEETransactionErrorHandlerBuilder transactionErrorHandler() {
        return new JavaEETransactionErrorHandlerBuilder();
    }

}
