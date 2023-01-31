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

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.Metadata;

/**
 * Transactional error handler (requires either camel-spring or camel-jta using traditional JTA transactions).
 */
public abstract class TransactionErrorHandlerDefinition extends DefaultErrorHandlerDefinition {

    @XmlTransient
    private Object transactedPolicy;

    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.spi.TransactedPolicy")
    private String transactedPolicyRef;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.LoggingLevel", defaultValue = "WARN", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private String rollbackLoggingLevel;

    @Override
    public boolean supportTransacted() {
        return true;
    }

    protected void cloneBuilder(TransactionErrorHandlerDefinition other) {
        other.setTransactedPolicyRef(getTransactedPolicyRef());
        other.setRollbackLoggingLevel(getRollbackLoggingLevel());
        super.cloneBuilder(other);
    }

    public Object getTransactedPolicy() {
        return transactedPolicy;
    }

    /**
     * The transacted policy to use that is configured for either Spring or JTA based transactions.
     */
    public void setTransactedPolicy(Object transactedPolicy) {
        this.transactedPolicy = transactedPolicy;
    }

    public String getTransactedPolicyRef() {
        return transactedPolicyRef;
    }

    /**
     * The transacted policy to use that is configured for either Spring or JTA based transactions. If no policy has
     * been configured then Camel will attempt to auto-discover.
     */
    public void setTransactedPolicyRef(String transactedPolicyRef) {
        this.transactedPolicyRef = transactedPolicyRef;
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
     * The transacted policy to use that is configured for either Spring or JTA based transactions.
     */
    public TransactionErrorHandlerDefinition transactedPolicy(Object transactedPolicy) {
        setTransactedPolicy(transactedPolicy);
        return this;
    }

    /**
     * References to the transacted policy to use that is configured for either Spring or JTA based transactions.
     */
    public TransactionErrorHandlerDefinition transactedPolicyRef(String transactedPolicyRef) {
        setTransactedPolicyRef(transactedPolicyRef);
        return this;
    }

    /**
     * Sets the logging level to use for logging transactional rollback.
     * <p/>
     * This option is default WARN.
     */
    public TransactionErrorHandlerDefinition rollbackLoggingLevel(String rollbackLoggingLevel) {
        setRollbackLoggingLevel(rollbackLoggingLevel);
        return this;
    }

    /**
     * Sets the logging level to use for logging transactional rollback.
     * <p/>
     * This option is default WARN.
     */
    public TransactionErrorHandlerDefinition rollbackLoggingLevel(LoggingLevel rollbackLoggingLevel) {
        setRollbackLoggingLevel(rollbackLoggingLevel.name());
        return this;
    }

}
