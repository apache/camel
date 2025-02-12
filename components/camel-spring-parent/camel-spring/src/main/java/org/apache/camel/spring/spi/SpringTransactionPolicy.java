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
package org.apache.camel.spring.spi;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.SpringTransactionErrorHandlerBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.errorhandler.ErrorHandlerHelper;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.model.errorhandler.SpringTransactionErrorHandlerDefinition;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring transaction policy when using spring based transactions.
 */
public class SpringTransactionPolicy implements TransactedPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(SpringTransactionPolicy.class);
    private TransactionTemplate template;
    private String name;
    private String propagationBehaviorName;
    private PlatformTransactionManager transactionManager;

    static {
        // register camel-spring as transaction error handler (both builder and definition)
        ErrorHandlerReifier.registerReifier(SpringTransactionErrorHandlerBuilder.class,
                (route, errorHandlerFactory) -> new TransactionErrorHandlerReifier(
                        route, (SpringTransactionErrorHandlerDefinition) errorHandlerFactory));
        ErrorHandlerReifier.registerReifier(SpringTransactionErrorHandlerDefinition.class,
                (route, errorHandlerFactory) -> new TransactionErrorHandlerReifier(
                        route, (SpringTransactionErrorHandlerDefinition) errorHandlerFactory));
    }

    /**
     * Default constructor for easy spring configuration.
     */
    public SpringTransactionPolicy() {
    }

    public SpringTransactionPolicy(TransactionTemplate template) {
        this.template = template;
    }

    public SpringTransactionPolicy(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
    }

    @Override
    public Processor wrap(Route route, Processor processor) {
        TransactionErrorHandler answer;

        // the goal is to configure the error handler builder on the route as a transacted error handler,
        // either its already a transacted or if not we replace it with a transacted one that we configure here
        // and wrap the processor in the transacted error handler as we can have transacted routes that change
        // propagation behavior, eg: from A required -> B -> requiresNew C (advanced use-case)
        // if we should not support this we do not need to wrap the processor as we only need one transacted error handler

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

        if (builder != null && builder.supportTransacted()) {
            // already a TX error handler then we are good to go
            LOG.debug("The ErrorHandlerBuilder configured is already a TransactionErrorHandlerBuilder: {}", builder);
            answer = createTransactionErrorHandler(route, processor, builder);
        } else {
            // no transaction error handler builder configure so create a temporary one as we got all
            // the needed information form the configured builder anyway this allow us to use transacted
            // routes anyway even though the error handler is not transactional, eg ease of configuration
            if (builder != null) {
                LOG.debug("The ErrorHandlerBuilder configured is not a TransactionErrorHandlerBuilder: {}", builder);
            } else {
                LOG.debug("No ErrorHandlerBuilder configured, will use default LegacyTransactionErrorHandlerBuilder settings");
            }
            // use legacy transaction to also support camel-spring-xml
            LegacyTransactionErrorHandlerBuilder txBuilder = new LegacyTransactionErrorHandlerBuilder();
            txBuilder.setTransactionTemplate(getTransactionTemplate());
            txBuilder.setSpringTransactionPolicy(this);
            if (builder != null) {
                // use error handlers from the configured builder
                route.addErrorHandlerFactoryReference(builder, txBuilder);
            }
            answer = createTransactionErrorHandler(route, processor, txBuilder);

            // set the route to use our transacted error handler builder
            route.setErrorHandlerFactory(txBuilder);
        }

        // return with wrapped transacted error handler
        return answer;
    }

    protected TransactionErrorHandler createTransactionErrorHandler(
            Route route, Processor processor, ErrorHandlerFactory builder) {
        TransactionErrorHandler answer;
        try {
            ModelCamelContext mcc = (ModelCamelContext) route.getCamelContext();
            answer = (TransactionErrorHandler) mcc.getModelReifierFactory().createErrorHandler(route, builder, processor);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    public TransactionTemplate getTransactionTemplate() {
        if (template == null) {
            ObjectHelper.notNull(transactionManager, "transactionManager");
            template = new TransactionTemplate(transactionManager);
            if (name != null) {
                template.setName(name);
            }
            if (propagationBehaviorName != null) {
                template.setPropagationBehaviorName(propagationBehaviorName);
            }
        }
        return template;
    }

    public void setTransactionTemplate(TransactionTemplate template) {
        this.template = template;
    }

    public TransactionTemplate getTemplate() {
        return template;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setPropagationBehaviorName(String propagationBehaviorName) {
        this.propagationBehaviorName = propagationBehaviorName;
    }

    public String getPropagationBehaviorName() {
        return propagationBehaviorName;
    }
}
