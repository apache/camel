/**
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

import org.apache.camel.Processor;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wraps the processor in a Spring transaction
 *
 * @version 
 */
public class SpringTransactionPolicy implements TransactedPolicy {
    private static final Logger LOG = LoggerFactory.getLogger(SpringTransactionPolicy.class);
    private TransactionTemplate template;
    private String propagationBehaviorName;
    private PlatformTransactionManager transactionManager;

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

    public void beforeWrap(RouteContext routeContext, ProcessorDefinition<?> definition) {
    }

    public Processor wrap(RouteContext routeContext, Processor processor) {
        TransactionErrorHandler answer;

        // the goal is to configure the error handler builder on the route as a transacted error handler,
        // either its already a transacted or if not we replace it with a transacted one that we configure here
        // and wrap the processor in the transacted error handler as we can have transacted routes that change
        // propagation behavior, eg: from A required -> B -> requiresNew C (advanced use-case)
        // if we should not support this we do not need to wrap the processor as we only need one transacted error handler

        // find the existing error handler builder
        ErrorHandlerBuilder builder = (ErrorHandlerBuilder)routeContext.getRoute().getErrorHandlerBuilder();

        // check if its a ref if so then do a lookup
        if (builder instanceof ErrorHandlerBuilderRef) {
            // its a reference to a error handler so lookup the reference
            ErrorHandlerBuilderRef builderRef = (ErrorHandlerBuilderRef) builder;
            String ref = builderRef.getRef();
            // only lookup if there was explicit an error handler builder configured
            // otherwise its just the "default" that has not explicit been configured
            // and if so then we can safely replace that with our transacted error handler
            if (ErrorHandlerBuilderRef.isErrorHandlerBuilderConfigured(ref)) {
                LOG.debug("Looking up ErrorHandlerBuilder with ref: {}", ref);
                builder = (ErrorHandlerBuilder)ErrorHandlerBuilderRef.lookupErrorHandlerBuilder(routeContext, ref);
            }
        }

        if (builder != null && builder.supportTransacted()) {
            // already a TX error handler then we are good to go
            LOG.debug("The ErrorHandlerBuilder configured is already a TransactionErrorHandlerBuilder: {}", builder);
            answer = createTransactionErrorHandler(routeContext, processor, builder);
            answer.setExceptionPolicy(builder.getExceptionPolicyStrategy());
            // configure our answer based on the existing error handler
            builder.configure(routeContext, answer);
        } else {
            // no transaction error handler builder configure so create a temporary one as we got all
            // the needed information form the configured builder anyway this allow us to use transacted
            // routes anyway even though the error handler is not transactional, eg ease of configuration
            if (builder != null) {
                LOG.debug("The ErrorHandlerBuilder configured is not a TransactionErrorHandlerBuilder: {}", builder);
            } else {
                LOG.debug("No ErrorHandlerBuilder configured, will use default TransactionErrorHandlerBuilder settings");
            }
            TransactionErrorHandlerBuilder txBuilder = new TransactionErrorHandlerBuilder();
            txBuilder.setTransactionTemplate(getTransactionTemplate());
            txBuilder.setSpringTransactionPolicy(this);
            if (builder != null) {
                // use error handlers from the configured builder
                txBuilder.setErrorHandlers(routeContext, builder.getErrorHandlers(routeContext));
            }
            answer = createTransactionErrorHandler(routeContext, processor, txBuilder);
            answer.setExceptionPolicy(txBuilder.getExceptionPolicyStrategy());
            // configure our answer based on the existing error handler
            txBuilder.configure(routeContext, answer);

            // set the route to use our transacted error handler builder
            routeContext.getRoute().setErrorHandlerBuilder(txBuilder);
        }

        // return with wrapped transacted error handler
        return answer;
    }

    protected TransactionErrorHandler createTransactionErrorHandler(RouteContext routeContext, Processor processor, ErrorHandlerBuilder builder) {
        TransactionErrorHandler answer;
        try {
            answer = (TransactionErrorHandler) builder.createErrorHandler(routeContext, processor);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    public TransactionTemplate getTransactionTemplate() {
        if (template == null) {
            ObjectHelper.notNull(transactionManager, "transactionManager");
            template = new TransactionTemplate(transactionManager);
            if (propagationBehaviorName != null) {
                template.setPropagationBehaviorName(propagationBehaviorName);
            }
        }
        return template;
    }

    public void setTransactionTemplate(TransactionTemplate template) {
        this.template = template;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setPropagationBehaviorName(String propagationBehaviorName) {
        this.propagationBehaviorName = propagationBehaviorName;
    }

    public String getPropagationBehaviorName() {
        return propagationBehaviorName;
    }
}
