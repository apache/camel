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
package org.apache.camel.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An extension of the {@link RouteBuilder} to provide some additional helper
 * methods
 *
 * @deprecated use plain {@link RouteBuilder}
 */
@Deprecated
public abstract class SpringRouteBuilder extends RouteBuilder implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    /**
     * Looks up the bean with the given name in the application context and
     * returns it, or throws an exception if the bean is not present or is not
     * of the given type
     *
     * @param beanName the name of the bean in the application context
     * @param type the type of the bean
     * @return the bean
     */
    public <T> T lookup(String beanName, Class<T> type) {
        ApplicationContext context = getApplicationContext();
        return context.getBean(beanName, type);
    }

    /**
     * Looks up the bean with the given type in the application context and
     * returns it, or throws an exception if the bean is not present or there
     * are multiple possible beans to choose from for the given type
     *
     * @param type the type of the bean
     * @return the bean
     */
    public <T> T lookup(Class<T> type) {
        ApplicationContext context = getApplicationContext();
        return context.getBean(type);
    }

    /**
     * Returns the application context which has been configured via the
     * {@link #setApplicationContext(ApplicationContext)} method or from the
     * underlying {@link SpringCamelContext}
     */
    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            CamelContext camelContext = getContext();
            if (camelContext instanceof SpringCamelContext) {
                SpringCamelContext springCamelContext = (SpringCamelContext)camelContext;
                return springCamelContext.getApplicationContext();
            } else {
                throw new IllegalArgumentException("This SpringBuilder is not being used with a SpringCamelContext and there is no applicationContext property configured");
            }
        }
        return applicationContext;
    }

    /**
     * Sets the application context to use to lookup beans
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Creates a transaction error handler that will lookup in application context for
     * an exiting transaction manager.
     *
     * @return the created error handler
     */
    public TransactionErrorHandlerBuilder transactionErrorHandler() {
        return new TransactionErrorHandlerBuilder();
    }

    /**
     * Creates a transaction error handler.
     *
     * @param policy   using this transaction policy (eg: required, supports, ...)
     * @return the created error handler
     */
    public TransactionErrorHandlerBuilder transactionErrorHandler(SpringTransactionPolicy policy) {
        return transactionErrorHandler(policy.getTransactionTemplate());
    }

    /**
     * Creates a transaction error handler.
     *
     * @param template the spring transaction template
     * @return the created error handler
     */
    public TransactionErrorHandlerBuilder transactionErrorHandler(TransactionTemplate template) {
        TransactionErrorHandlerBuilder answer = new TransactionErrorHandlerBuilder();
        answer.setTransactionTemplate(template);
        return answer;
    }

    /**
     * Creates a transaction error handler.
     *
     * @param transactionManager the spring transaction manager
     * @return the created error handler
     */
    public TransactionErrorHandlerBuilder transactionErrorHandler(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return transactionErrorHandler(template);
    }

}
