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
package org.apache.camel.cdi.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;

/**
 * The &lt;errorHandler&gt; tag element.
 */
@XmlRootElement(name = "errorHandler")
@XmlAccessorType(XmlAccessType.FIELD)
public class ErrorHandlerDefinition extends IdentifiedType {

    @XmlAttribute
    private ErrorHandlerType type = ErrorHandlerType.DefaultErrorHandler;

    @XmlAttribute
    private String deadLetterUri;

    @XmlAttribute
    private String deadLetterHandleNewException;

    @XmlAttribute
    private String rollbackLoggingLevel;

    @XmlAttribute
    private String useOriginalMessage;

    @XmlAttribute
    private String useOriginalBody;

    @XmlAttribute
    private String transactionTemplateRef;

    @XmlAttribute
    private String transactionManagerRef;

    @XmlAttribute
    private String onRedeliveryRef;

    @XmlAttribute
    private String onExceptionOccurredRef;

    @XmlAttribute
    private String onPrepareFailureRef;

    @XmlAttribute
    private String retryWhileRef;

    @XmlAttribute
    private String redeliveryPolicyRef;

    @XmlAttribute
    private String executorServiceRef;

    @XmlElement
    private RedeliveryPolicyFactoryBean redeliveryPolicy;

    public ErrorHandlerType getType() {
        return type;
    }

    public void setType(ErrorHandlerType type) {
        this.type = type;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public String getDeadLetterHandleNewException() {
        return deadLetterHandleNewException;
    }

    public void setDeadLetterHandleNewException(String deadLetterHandleNewException) {
        this.deadLetterHandleNewException = deadLetterHandleNewException;
    }

    public String getRollbackLoggingLevel() {
        return rollbackLoggingLevel;
    }

    public void setRollbackLoggingLevel(String rollbackLoggingLevel) {
        this.rollbackLoggingLevel = rollbackLoggingLevel;
    }

    public String getUseOriginalMessage() {
        return useOriginalMessage;
    }

    public void setUseOriginalMessage(String useOriginalMessage) {
        this.useOriginalMessage = useOriginalMessage;
    }

    public String getUseOriginalBody() {
        return useOriginalBody;
    }

    public void setUseOriginalBody(String useOriginalBody) {
        this.useOriginalBody = useOriginalBody;
    }

    public String getTransactionTemplateRef() {
        return transactionTemplateRef;
    }

    public void setTransactionTemplateRef(String transactionTemplateRef) {
        this.transactionTemplateRef = transactionTemplateRef;
    }

    public String getTransactionManagerRef() {
        return transactionManagerRef;
    }

    public void setTransactionManagerRef(String transactionManagerRef) {
        this.transactionManagerRef = transactionManagerRef;
    }

    public String getOnRedeliveryRef() {
        return onRedeliveryRef;
    }

    public void setOnRedeliveryRef(String onRedeliveryRef) {
        this.onRedeliveryRef = onRedeliveryRef;
    }

    public String getOnExceptionOccurredRef() {
        return onExceptionOccurredRef;
    }

    public void setOnExceptionOccurredRef(String onExceptionOccurredRef) {
        this.onExceptionOccurredRef = onExceptionOccurredRef;
    }

    public String getOnPrepareFailureRef() {
        return onPrepareFailureRef;
    }

    public void setOnPrepareFailureRef(String onPrepareFailureRef) {
        this.onPrepareFailureRef = onPrepareFailureRef;
    }

    public String getRetryWhileRef() {
        return retryWhileRef;
    }

    public void setRetryWhileRef(String retryWhileRef) {
        this.retryWhileRef = retryWhileRef;
    }

    public String getRedeliveryPolicyRef() {
        return redeliveryPolicyRef;
    }

    public void setRedeliveryPolicyRef(String redeliveryPolicyRef) {
        this.redeliveryPolicyRef = redeliveryPolicyRef;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public RedeliveryPolicyFactoryBean getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public void setRedeliveryPolicy(RedeliveryPolicyFactoryBean redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }
}
