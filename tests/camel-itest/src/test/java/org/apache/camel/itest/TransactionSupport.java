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

package org.apache.camel.itest;

import org.apache.camel.spring.spi.LegacyTransactionErrorHandlerBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionSupport {
    private TransactionSupport() {
    }

    /**
     * Creates a transaction error handler.
     *
     * @param  policy using this transaction policy (eg: required, supports, ...)
     * @return        the created error handler
     */
    public static LegacyTransactionErrorHandlerBuilder transactionErrorHandler(SpringTransactionPolicy policy) {
        return transactionErrorHandler(policy.getTransactionTemplate());
    }

    /**
     * Creates a transaction error handler.
     *
     * @param  template the spring transaction template
     * @return          the created error handler
     */
    private static LegacyTransactionErrorHandlerBuilder transactionErrorHandler(TransactionTemplate template) {
        LegacyTransactionErrorHandlerBuilder answer = new LegacyTransactionErrorHandlerBuilder();
        answer.setTransactionTemplate(template);
        return answer;
    }
}
