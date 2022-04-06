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
package org.apache.camel.model.errorhandler;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.spi.Metadata;

/**
 * Transactional error handler (requires either camel-spring or camel-jta using traditional JTA transactions).
 */
@Metadata(label = "configuration,error")
@XmlRootElement(name = "transactionErrorHandler")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransactionErrorHandlerDefinition extends DefaultErrorHandlerDefinition {

    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.spi.TransactedPolicy")
    private String transactedPolicy;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String transactionTemplateRef;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String transactionManagerRef;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.LoggingLevel", defaultValue = "WARN", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private String rollbackLoggingLevel;

    @Override
    public boolean supportTransacted() {
        return true;
    }

    @Override
    public ErrorHandlerFactory cloneBuilder() {
        TransactionErrorHandlerDefinition answer = new TransactionErrorHandlerDefinition();
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(TransactionErrorHandlerDefinition other) {
        other.setTransactedPolicy(getTransactedPolicy());
        other.setTransactionManagerRef(getTransactionManagerRef());
        other.setTransactionTemplateRef(getTransactionTemplateRef());
        other.setRollbackLoggingLevel(getRollbackLoggingLevel());
        super.cloneBuilder(other);
    }

    public String getTransactedPolicy() {
        return transactedPolicy;
    }

    /**
     * The transacted policy to use that is configured for either Spring or JTA based transactions.
     */
    public void setTransactedPolicy(String transactedPolicy) {
        this.transactedPolicy = transactedPolicy;
    }

    public String getTransactionTemplateRef() {
        return transactionTemplateRef;
    }

    /**
     * References to the spring transaction template (org.springframework.transaction.support.TransactionTemplate) to
     * use.
     */
    public void setTransactionTemplateRef(String transactionTemplateRef) {
        this.transactionTemplateRef = transactionTemplateRef;
    }

    public String getTransactionManagerRef() {
        return transactionManagerRef;
    }

    /**
     * References to the spring platform transaction manager
     * (org.springframework.transaction.PlatformTransactionManager) to use.
     */
    public void setTransactionManagerRef(String transactionManagerRef) {
        this.transactionManagerRef = transactionManagerRef;
    }

    public String getRollbackLoggingLevel() {
        return rollbackLoggingLevel;
    }

    /**
     * Sets the logging level to use for logging transactional rollback.
     * <p/>
     * This option is default WARN.
     */
    public void setRollbackLoggingLevel(String rollbackLoggingLevel) {
        this.rollbackLoggingLevel = rollbackLoggingLevel;
    }

    /**
     * References to the spring transaction template (org.springframework.transaction.support.TransactionTemplate) to
     * use.
     */
    public TransactionErrorHandlerDefinition transactedPolicy(String transactedPolicy) {
        setTransactedPolicy(transactedPolicy);
        return this;
    }

    /**
     * References to the spring transaction template (org.springframework.transaction.support.TransactionTemplate) to
     * use.
     */
    public TransactionErrorHandlerDefinition transactionTemplate(String transactionTemplateRef) {
        setTransactionTemplateRef(transactionTemplateRef);
        return this;
    }

    /**
     * References to the spring platform transaction manager
     * (org.springframework.transaction.PlatformTransactionManager) to use.
     */
    public TransactionErrorHandlerDefinition transactionManager(String transactionManagerRef) {
        setTransactionManagerRef(transactionManagerRef);
        return this;
    }

    /**
     * References to the spring transaction template (org.springframework.transaction.support.TransactionTemplate) to
     * use.
     */
    public TransactionErrorHandlerDefinition rollbackLoggingLevel(String rollbackLoggingLevel) {
        setRollbackLoggingLevel(rollbackLoggingLevel);
        return this;
    }

}
