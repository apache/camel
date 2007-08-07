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

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version $Revision: 1.1 $
 */
public class TransactionInterceptor extends DelegateProcessor {
    private static final transient Log LOG = LogFactory.getLog(TransactionInterceptor.class);
    private final TransactionTemplate transactionTemplate;

    public TransactionInterceptor(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void process(final Exchange exchange) {
        LOG.info("transaction begin");

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                try {
                    processNext(exchange);
                } catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
            }
        });

        LOG.info("transaction commit");
    }

    @Override
    public String toString() {
        return "TransactionInterceptor:" + propagationBehaviorToString(transactionTemplate.getPropagationBehavior()) + "[" + getProcessor() + "]";
    }

    private String propagationBehaviorToString(int propagationBehavior) {
        String rc;
        switch (propagationBehavior) {
        case TransactionDefinition.PROPAGATION_MANDATORY:
            rc = "PROPAGATION_MANDATORY";
            break;
        case TransactionDefinition.PROPAGATION_NESTED:
            rc = "PROPAGATION_NESTED";
            break;
        case TransactionDefinition.PROPAGATION_NEVER:
            rc = "PROPAGATION_NEVER";
            break;
        case TransactionDefinition.PROPAGATION_NOT_SUPPORTED:
            rc = "PROPAGATION_NOT_SUPPORTED";
            break;
        case TransactionDefinition.PROPAGATION_REQUIRED:
            rc = "PROPAGATION_REQUIRED";
            break;
        case TransactionDefinition.PROPAGATION_REQUIRES_NEW:
            rc = "PROPAGATION_REQUIRES_NEW";
            break;
        case TransactionDefinition.PROPAGATION_SUPPORTS:
            rc = "PROPAGATION_SUPPORTS";
            break;
        default:
            rc = "UNKOWN"; 
        }
        return rc;
    }
}
