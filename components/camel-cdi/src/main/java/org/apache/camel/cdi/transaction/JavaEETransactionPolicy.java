package org.apache.camel.cdi.transaction;

import javax.annotation.Resource;
import javax.transaction.TransactionManager;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets a proper error handler. This class is based on
 * {@link org.apache.camel.spring.spi.SpringTransactionPolicy}.
 * <p>
 * This class requires the resource {@link TransactionManager} to be available
 * through JNDI url &quot;java:/TransactionManager&quot;
 */
public abstract class JavaEETransactionPolicy implements TransactedPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(JavaEETransactionPolicy.class);

    public static interface Runnable {
        void run() throws Throwable;
    }

    @Resource(lookup = "java:/TransactionManager")
    protected TransactionManager transactionManager;

    @Override
    public void beforeWrap(RouteContext routeContext, ProcessorDefinition<?> definition) {
        // do not inherit since we create our own
        // (otherwise the default error handler would be used two times
        // because we inherit it on our own but only in case of a
        // non-transactional
        // error handler)
        definition.setInheritErrorHandler(false);
    }

    public abstract void run(final Runnable runnable) throws Throwable;

    @Override
    public Processor wrap(RouteContext routeContext, Processor processor) {

        JavaEETransactionErrorHandler answer;

        // the goal is to configure the error handler builder on the route as a
        // transacted error handler,
        // either its already a transacted or if not we replace it with a
        // transacted one that we configure here
        // and wrap the processor in the transacted error handler as we can have
        // transacted routes that change
        // propagation behavior, eg: from A required -> B -> requiresNew C
        // (advanced use-case)
        // if we should not support this we do not need to wrap the processor as
        // we only need one transacted error handler

        // find the existing error handler builder
        ErrorHandlerBuilder builder = (ErrorHandlerBuilder) routeContext.getRoute().getErrorHandlerBuilder();

        // check if its a ref if so then do a lookup
        if (builder instanceof ErrorHandlerBuilderRef) {
            // its a reference to a error handler so lookup the reference
            ErrorHandlerBuilderRef builderRef = (ErrorHandlerBuilderRef) builder;
            String ref = builderRef.getRef();
            // only lookup if there was explicit an error handler builder
            // configured
            // otherwise its just the "default" that has not explicit been
            // configured
            // and if so then we can safely replace that with our transacted
            // error handler
            if (ErrorHandlerBuilderRef.isErrorHandlerBuilderConfigured(ref)) {
                LOG.debug("Looking up ErrorHandlerBuilder with ref: {}", ref);
                builder = (ErrorHandlerBuilder) ErrorHandlerBuilderRef.lookupErrorHandlerBuilder(routeContext, ref);
            }
        }

        JavaEETransactionErrorHandlerBuilder txBuilder;
        if ((builder != null) && builder.supportTransacted()) {
            if (!(builder instanceof JavaEETransactionErrorHandlerBuilder)) {
                throw new RuntimeCamelException("The given transactional error handler builder '" + builder
                        + "' is not of type '" + JavaEETransactionErrorHandlerBuilder.class.getName()
                        + "' which is required in this environment!");
            }
            LOG.debug("The ErrorHandlerBuilder configured is a JavaEETransactionErrorHandlerBuilder: {}", builder);
            txBuilder = (JavaEETransactionErrorHandlerBuilder) builder.cloneBuilder();
        } else {
            LOG.debug(
                    "No or no transactional ErrorHandlerBuilder configured, will use default JavaEETransactionErrorHandlerBuilder settings");
            txBuilder = new JavaEETransactionErrorHandlerBuilder();
        }

        txBuilder.setTransactionPolicy(this);

        // use error handlers from the configured builder
        if (builder != null) {
            txBuilder.setErrorHandlers(routeContext, builder.getErrorHandlers(routeContext));
        }

        answer = createTransactionErrorHandler(routeContext, processor, txBuilder);
        answer.setExceptionPolicy(txBuilder.getExceptionPolicyStrategy());
        // configure our answer based on the existing error handler
        txBuilder.configure(routeContext, answer);

        // set the route to use our transacted error handler builder
        routeContext.getRoute().setErrorHandlerBuilder(txBuilder);

        // return with wrapped transacted error handler
        return answer;

    }

    protected JavaEETransactionErrorHandler createTransactionErrorHandler(RouteContext routeContext, Processor processor,
            ErrorHandlerBuilder builder) {

        JavaEETransactionErrorHandler answer;
        try {
            answer = (JavaEETransactionErrorHandler) builder.createErrorHandler(routeContext, processor);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return answer;

    }

    @Override
    public String toString() {
        return getClass().getName();
    }

}
