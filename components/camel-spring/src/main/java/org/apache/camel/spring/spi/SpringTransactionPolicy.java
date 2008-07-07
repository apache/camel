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
import org.apache.camel.spi.Policy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wraps the processor in a Spring transaction
 *
 * @version $Revision$
 */
public class SpringTransactionPolicy<E> implements Policy<E> {
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

    public Processor wrap(Processor processor) {
        final TransactionTemplate transactionTemplate = getTemplate();
        if (transactionTemplate == null) {
            LOG.warn("No TransactionTemplate available so transactions will not be enabled!");
            return processor;
        }

        TransactionInterceptor answer = new TransactionInterceptor(transactionTemplate);
        answer.setProcessor(processor);
        return answer;
    }

    public TransactionTemplate getTemplate() {
        if (template == null) {
            template = new TransactionTemplate(transactionManager);
            if (propagationBehaviorName != null) {
                template.setPropagationBehaviorName(propagationBehaviorName);
            }
        }
        return template;
    }

    public void setTemplate(TransactionTemplate template) {
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
