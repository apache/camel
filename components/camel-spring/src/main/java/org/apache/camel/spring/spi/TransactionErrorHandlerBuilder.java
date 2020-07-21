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

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.CamelLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A transactional error handler that supports leveraging Spring TransactionManager.
 */
public class TransactionErrorHandlerBuilder extends DefaultErrorHandlerBuilder {

    static {
        ErrorHandlerReifier.registerReifier(TransactionErrorHandlerBuilder.class, TransactionErrorHandlerReifier::new);
    }

    private static final Logger LOG = LoggerFactory.getLogger(TransactionErrorHandlerBuilder.class);
    private static final String PROPAGATION_REQUIRED = "PROPAGATION_REQUIRED";
    private TransactionTemplate transactionTemplate;
    private LoggingLevel rollbackLoggingLevel = LoggingLevel.WARN;

    public TransactionErrorHandlerBuilder() {
        // no-arg constructor used by Spring DSL
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    @Override
    public boolean supportTransacted() {
        return true;
    }

    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setSpringTransactionPolicy(SpringTransactionPolicy policy) {
        this.transactionTemplate = policy.getTransactionTemplate();
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public LoggingLevel getRollbackLoggingLevel() {
        return rollbackLoggingLevel;
    }

    /**
     * Sets the logging level to use for logging transactional rollback.
     * <p/>
     * This option is default WARN.
     *
     * @param rollbackLoggingLevel the logging level
     */
    public void setRollbackLoggingLevel(LoggingLevel rollbackLoggingLevel) {
        this.rollbackLoggingLevel = rollbackLoggingLevel;
    }

    // Builder methods
    // -------------------------------------------------------------------------

    /**
     * Sets the logging level to use for logging transactional rollback.
     * <p/>
     * This option is default WARN.
     *
     * @param rollbackLoggingLevel the logging level
     */
    public TransactionErrorHandlerBuilder rollbackLoggingLevel(LoggingLevel rollbackLoggingLevel) {
        setRollbackLoggingLevel(rollbackLoggingLevel);
        return this;
    }

    // Implementation
    // -------------------------------------------------------------------------

    @Override
    protected CamelLogger createLogger() {
        return new CamelLogger(LoggerFactory.getLogger(TransactionErrorHandler.class), LoggingLevel.ERROR);
    }

    @Override
    public String toString() {
        return "TransactionErrorHandlerBuilder";
    }

}
