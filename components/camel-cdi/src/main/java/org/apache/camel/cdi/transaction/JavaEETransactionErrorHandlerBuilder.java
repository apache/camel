package org.apache.camel.cdi.transaction;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds transactional error handlers. This class is based on
 * {@link org.apache.camel.spring.spi.TransactionErrorHandlerBuilder}.
 */
public class JavaEETransactionErrorHandlerBuilder extends DefaultErrorHandlerBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(JavaEETransactionErrorHandlerBuilder.class);

    private static final String PROPAGATION_REQUIRED = "PROPAGATION_REQUIRED";

    public static final String ROLLBACK_LOGGING_LEVEL_PROPERTY = JavaEETransactionErrorHandlerBuilder.class.getName()
            + "#rollbackLoggingLevel";

    private LoggingLevel rollbackLoggingLevel = LoggingLevel.WARN;

    private JavaEETransactionPolicy transactionPolicy;

    private String policyRef;

    @Override
    public boolean supportTransacted() {
        return true;
    }
    
    @Override
    public ErrorHandlerBuilder cloneBuilder() {

        final JavaEETransactionErrorHandlerBuilder answer = new JavaEETransactionErrorHandlerBuilder();
        cloneBuilder(answer);
        return answer;

    }

    @Override
    protected void cloneBuilder(DefaultErrorHandlerBuilder other) {

        super.cloneBuilder(other);
        if (other instanceof JavaEETransactionErrorHandlerBuilder) {
            final JavaEETransactionErrorHandlerBuilder otherTx = (JavaEETransactionErrorHandlerBuilder) other;
            transactionPolicy = otherTx.transactionPolicy;
            rollbackLoggingLevel = otherTx.rollbackLoggingLevel;
        }

    }

    public Processor createErrorHandler(final RouteContext routeContext, final Processor processor) throws Exception {

        // resolve policy reference, if given
        if (transactionPolicy == null) {

            if (policyRef != null) {

                final TransactedDefinition transactedDefinition = new TransactedDefinition();
                transactedDefinition.setRef(policyRef);
                final Policy policy = transactedDefinition.resolvePolicy(routeContext);
                if (policy != null) {
                    if (!(policy instanceof JavaEETransactionPolicy)) {
                        throw new RuntimeCamelException("The configured policy '" + policyRef + "' is of type '"
                                + policyRef.getClass().getName() + "' but an instance of '"
                                + JavaEETransactionPolicy.class.getName() + "' is required!");
                    }
                    transactionPolicy = (JavaEETransactionPolicy) policy;
                }

            }

        }

        // try to lookup default policy
        if (transactionPolicy == null) {

            LOG.debug(
                    "No tranaction policiy configured on TransactionErrorHandlerBuilder. Will try find it in the registry.");

            Map<String, TransactedPolicy> mapPolicy = routeContext.lookupByType(TransactedPolicy.class);
            if (mapPolicy != null && mapPolicy.size() == 1) {
                TransactedPolicy policy = mapPolicy.values().iterator().next();
                if (policy != null && policy instanceof JavaEETransactionPolicy) {
                    transactionPolicy = ((JavaEETransactionPolicy) policy);
                }
            }

            if (transactionPolicy == null) {
                TransactedPolicy policy = routeContext.lookup(PROPAGATION_REQUIRED, TransactedPolicy.class);
                if (policy != null && policy instanceof JavaEETransactionPolicy) {
                    transactionPolicy = ((JavaEETransactionPolicy) policy);
                }
            }

            if (transactionPolicy != null) {
                LOG.debug("Found TransactionPolicy in registry to use: " + transactionPolicy);
            }

        }

        ObjectHelper.notNull(transactionPolicy, "transactionPolicy", this);

        final CamelContext camelContext = routeContext.getCamelContext();
        final Map<String, String> properties = camelContext.getProperties();
        if ((properties != null) && properties.containsKey(ROLLBACK_LOGGING_LEVEL_PROPERTY)) {
            rollbackLoggingLevel = LoggingLevel.valueOf(properties.get(ROLLBACK_LOGGING_LEVEL_PROPERTY));
        }

        JavaEETransactionErrorHandler answer = new JavaEETransactionErrorHandler(camelContext,
                processor,
                getLogger(),
                getOnRedelivery(),
                getRedeliveryPolicy(),
                getExceptionPolicyStrategy(),
                transactionPolicy,
                getRetryWhilePolicy(camelContext),
                getExecutorService(camelContext),
                rollbackLoggingLevel,
                getOnExceptionOccurred());

        // configure error handler before we can use it
        configure(routeContext, answer);
        return answer;

    }

    public JavaEETransactionErrorHandlerBuilder setTransactionPolicy(final String ref) {
        policyRef = ref;
        return this;
    }

    public JavaEETransactionErrorHandlerBuilder setTransactionPolicy(final JavaEETransactionPolicy transactionPolicy) {
        this.transactionPolicy = transactionPolicy;
        return this;
    }

    public JavaEETransactionErrorHandlerBuilder setRollbackLoggingLevel(final LoggingLevel rollbackLoggingLevel) {
    	this.rollbackLoggingLevel = rollbackLoggingLevel;
        return this;
    }
    
    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(TransactionErrorHandler.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "JavaEETransactionErrorHandlerBuilder";
    }

}
