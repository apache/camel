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

import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wraps the processor in a Spring transaction
 *
 * @version $Revision$
 */
public class SpringTransactionPolicy implements TransactedPolicy {
    private static final transient Log LOG = LogFactory.getLog(SpringTransactionPolicy.class);
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

    public Processor wrap(RouteContext routeContext, Processor processor) {
        TransactionErrorHandler answer = new TransactionErrorHandler(getTransactionTemplate());
        answer.setOutput(processor);

        ErrorHandlerBuilder builder = routeContext.getRoute().getErrorHandlerBuilder();
        if (builder instanceof ErrorHandlerBuilderRef) {
            // its a reference to a error handler so lookup the reference
            ErrorHandlerBuilderRef ref = (ErrorHandlerBuilderRef) builder;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Looking up ErrorHandlerBuilder with ref: " + ref.getRef());
            }
            builder = ref.lookupErrorHandlerBuilder(routeContext);
        }

        if (builder instanceof TransactionErrorHandlerBuilder) {
            // use existing transaction error handler builder
            TransactionErrorHandlerBuilder txBuilder = (TransactionErrorHandlerBuilder) builder;
            answer.setExceptionPolicy(txBuilder.getExceptionPolicyStrategy());
            txBuilder.configure(answer);
        } else {
            // no transaction error handler builder configure so create a temporary one as we got all
            // the needed information form the configured builder anyway this allow us to use transacted
            // routes anway even though the error handler is not transactional, eg ease of configuration
            if (LOG.isDebugEnabled()) {
                if (builder != null) {
                    LOG.debug("The ErrorHandlerBuilder configured is not a TransactionErrorHandlerBuilder: " + builder);
                } else {
                    LOG.debug("No ErrorHandlerBuilder configured, will use default TransactionErrorHandlerBuilder settings");
                }
            }
            TransactionErrorHandlerBuilder txBuilder = new TransactionErrorHandlerBuilder();
            txBuilder.setTransactionTemplate(getTransactionTemplate());
            txBuilder.setSpringTransactionPolicy(this);
            if (builder != null) {
                // use error handlers from the configured builder
                txBuilder.setErrorHandlers(builder.getErrorHandlers());
            }
            answer.setExceptionPolicy(txBuilder.getExceptionPolicyStrategy());
            txBuilder.configure(answer);
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
