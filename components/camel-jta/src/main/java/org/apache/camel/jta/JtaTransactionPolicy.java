/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jta;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.JtaTransactionErrorHandlerBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.errorhandler.ErrorHandlerHelper;
import org.apache.camel.model.errorhandler.JtaTransactionErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.TransactedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JTA transaction policy when using JTA based transactions.
 *
 * This class requires the resource TransactionManager to be available through JNDI url
 * &quot;java:/TransactionManager&quot;
 */
public abstract class JtaTransactionPolicy implements TransactedPolicy {

    static {
        // register camel-jta as transaction error handler (both builder and definition)
        ErrorHandlerReifier.registerReifier(JtaTransactionErrorHandlerBuilder.class,
                (route, errorHandlerFactory) -> new JtaTransactionErrorHandlerReifier(
                        route, (JtaTransactionErrorHandlerDefinition) errorHandlerFactory));
        ErrorHandlerReifier.registerReifier(JtaTransactionErrorHandlerDefinition.class,
                (route, errorHandlerFactory) -> new JtaTransactionErrorHandlerReifier(
                        route, (JtaTransactionErrorHandlerDefinition) errorHandlerFactory));
    }

    private static final Logger LOG = LoggerFactory.getLogger(JtaTransactionPolicy.class);

    public interface Runnable {
        void run() throws Throwable;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        // do not inherit since we create our own
        // (otherwise the default error handler would be used two times
        // because we inherit it on our own but only in case of a
        // non-transactional error handler)
        ((ProcessorDefinition<?>) definition).setInheritErrorHandler(false);
    }

    public abstract void run(Runnable runnable) throws Throwable;

    @Override
    public Processor wrap(Route route, Processor processor) {
        JtaTransactionErrorHandler answer;
        // the goal is to configure the error handler builder on the route as a
        // transacted error handler. If the configured builder is not transacted,
        // we replace it with a transacted one that we configure here
        // and wrap the processor in the transacted error handler as we can have
        // transacted routes that change propagation behavior,
        // eg: from A required -> B -> requiresNew C (advanced use-case)
        // if we should not support this we do not need to wrap the processor as
        // we only need one transacted error handler

        // find the existing error handler builder
        RouteDefinition routeDefinition = (RouteDefinition) route.getRoute();
        ErrorHandlerFactory builder = routeDefinition.getErrorHandlerFactory();

        // check if its a ref if so then do a lookup
        if (builder instanceof RefErrorHandlerDefinition) {
            // its a reference to a error handler so lookup the reference
            RefErrorHandlerDefinition builderRef = (RefErrorHandlerDefinition) builder;
            String ref = builderRef.getRef();
            // only lookup if there was explicit an error handler builder configured
            // otherwise its just the "default" that has not explicit been configured
            // and if so then we can safely replace that with our transacted error handler
            if (ErrorHandlerHelper.isErrorHandlerFactoryConfigured(ref)) {
                LOG.debug("Looking up ErrorHandlerBuilder with ref: {}", ref);
                builder = ErrorHandlerHelper.lookupErrorHandlerFactory(route, ref, true);
            }
        }

        JtaTransactionErrorHandlerDefinition txBuilder;
        if (builder != null && builder.supportTransacted()) {
            if (!(builder instanceof JtaTransactionErrorHandlerDefinition)) {
                throw new RuntimeCamelException(
                        "The given transactional error handler builder '" + builder
                                                + "' is not of type '" + JtaTransactionErrorHandlerDefinition.class.getName()
                                                + "' which is required in this environment!");
            }
            LOG.debug("The ErrorHandlerBuilder configured is a JtaTransactionErrorHandlerDefinition: {}", builder);
            txBuilder = (JtaTransactionErrorHandlerDefinition) builder.cloneBuilder();
        } else {
            LOG.debug(
                    "No or no transactional ErrorHandlerBuilder configured, will use default JtaTransactionErrorHandlerDefinition settings");
            txBuilder = new JtaTransactionErrorHandlerDefinition();
        }

        txBuilder.setTransactedPolicy(this);

        // use error handlers from the configured builder
        if (builder != null) {
            route.addErrorHandlerFactoryReference(builder, txBuilder);
        }

        answer = createTransactionErrorHandler(route, processor, txBuilder);

        // set the route to use our transacted error handler builder
        route.setErrorHandlerFactory(txBuilder);

        // return with wrapped transacted error handler
        return answer;
    }

    protected JtaTransactionErrorHandler createTransactionErrorHandler(
            Route route, Processor processor,
            ErrorHandlerFactory builder) {
        JtaTransactionErrorHandler answer;
        try {
            ModelCamelContext mcc = (ModelCamelContext) route.getCamelContext();
            answer = (JtaTransactionErrorHandler) mcc.getModelReifierFactory().createErrorHandler(route, builder, processor);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    @Override
    public String toString() {
        return getClass().getName();
    }
}
