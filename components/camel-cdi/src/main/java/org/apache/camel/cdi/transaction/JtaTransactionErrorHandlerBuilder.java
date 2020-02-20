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
package org.apache.camel.cdi.transaction;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.CamelLogger;
import org.slf4j.LoggerFactory;

/**
 * Builds transactional error handlers. This class is based on
 * {@link org.apache.camel.spring.spi.TransactionErrorHandlerBuilder}.
 */
public class JtaTransactionErrorHandlerBuilder extends DefaultErrorHandlerBuilder {

    static {
        ErrorHandlerReifier.registerReifier(JtaTransactionErrorHandlerBuilder.class, JtaTransactionErrorHandlerReifier::new);
    }

    private LoggingLevel rollbackLoggingLevel = LoggingLevel.WARN;

    private JtaTransactionPolicy transactionPolicy;

    private String policyRef;

    @Override
    public boolean supportTransacted() {
        return true;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        final JtaTransactionErrorHandlerBuilder answer = new JtaTransactionErrorHandlerBuilder();
        cloneBuilder(answer);
        return answer;
    }

    @Override
    protected void cloneBuilder(DefaultErrorHandlerBuilder other) {
        super.cloneBuilder(other);
        if (other instanceof JtaTransactionErrorHandlerBuilder) {
            final JtaTransactionErrorHandlerBuilder otherTx = (JtaTransactionErrorHandlerBuilder) other;
            transactionPolicy = otherTx.transactionPolicy;
            rollbackLoggingLevel = otherTx.rollbackLoggingLevel;
        }
    }

    public String getPolicyRef() {
        return policyRef;
    }

    public JtaTransactionErrorHandlerBuilder setTransactionPolicy(final String ref) {
        policyRef = ref;
        return this;
    }

    public JtaTransactionPolicy getTransactionPolicy() {
        return transactionPolicy;
    }

    public JtaTransactionErrorHandlerBuilder setTransactionPolicy(final JtaTransactionPolicy transactionPolicy) {
        this.transactionPolicy = transactionPolicy;
        return this;
    }

    public LoggingLevel getRollbackLoggingLevel() {
        return rollbackLoggingLevel;
    }

    public JtaTransactionErrorHandlerBuilder setRollbackLoggingLevel(final LoggingLevel rollbackLoggingLevel) {
        this.rollbackLoggingLevel = rollbackLoggingLevel;
        return this;
    }

    @Override
    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(TransactionErrorHandler.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "JtaTransactionErrorHandlerBuilder";
    }
}
