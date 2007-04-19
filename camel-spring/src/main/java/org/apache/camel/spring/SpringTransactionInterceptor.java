/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring;

import org.apache.camel.Processor;
import org.apache.camel.spi.Interceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wraps the processor in a Spring transaction
 *
 * @version $Revision: 1.1 $
 */
public class SpringTransactionInterceptor<E> implements Interceptor<E> {
    private static final transient Log log = LogFactory.getLog(SpringTransactionInterceptor.class);

    private TransactionTemplate template;

    public SpringTransactionInterceptor() {
    }

    public SpringTransactionInterceptor(TransactionTemplate template) {
        this.template = template;
    }

    public Processor<E> addIntercetors(final Processor<E> processor) {
        final TransactionTemplate transactionTemplate = getTemplate();
        if (transactionTemplate == null) {
            log.warn("No TransactionTemplate available so transactions will not be enabled!");
            return processor;
        }

        return new Processor<E>() {
            public void process(final E exchange) {
                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        processor.process(exchange);
                    }
                });
            }

            @Override
            public String toString() {
                return "SpringTransaction[" + processor + "]";
            }
        };
    }

    public TransactionTemplate getTemplate() {
        return template;
    }

    public void setTemplate(TransactionTemplate template) {
        this.template = template;
    }
}
