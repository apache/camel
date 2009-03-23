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
import org.apache.camel.builder.ErrorHandlerBuilderSupport;
import org.apache.camel.processor.DelayPolicy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An error handler which will roll the exception back if there is an error
 * rather than using the dead letter channel and retry logic.
 *
 * A delay is also used after a rollback
 *
 * @version $Revision$
 */
public class TransactionErrorHandlerBuilder extends ErrorHandlerBuilderSupport implements InitializingBean {

    private TransactionTemplate transactionTemplate;
    private DelayPolicy delayPolicy;

    public TransactionErrorHandlerBuilder() {
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setSpringTransactionPolicy(SpringTransactionPolicy policy) {
        this.transactionTemplate = policy.getTransactionTemplate();
    }

    public DelayPolicy getDelayPolicy() {
        return delayPolicy;
    }

    public void setDelayPolicy(DelayPolicy delayPolicy) {
        this.delayPolicy = delayPolicy;
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        return new TransactionInterceptor(processor, transactionTemplate, delayPolicy);
    }

    public void afterPropertiesSet() throws Exception {
        ObjectHelper.notNull(transactionTemplate, "transactionTemplate");
    }

    // Builder methods
    // -------------------------------------------------------------------------

    public TransactionErrorHandlerBuilder delay(long delay) {
        if (getDelayPolicy() == null) {
            delayPolicy = new DelayPolicy();
        }
        getDelayPolicy().delay(delay);
        return this;
    }

}
