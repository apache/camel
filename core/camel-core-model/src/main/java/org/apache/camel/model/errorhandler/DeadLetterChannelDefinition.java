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

import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.spi.Metadata;

/**
 * Dead letter channel error handler.
 */
@Metadata(label = "configuration,error")
@XmlRootElement(name = "deadLetterChannel")
@XmlAccessorType(XmlAccessType.FIELD)
public class DeadLetterChannelDefinition extends DefaultErrorHandlerDefinition implements ErrorHandlerBuilder {

    // TODO: fluent builders
    // TODO: label, java type, ref

    @XmlAttribute(required = true)
    private String deadLetterUri;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private String deadLetterHandleNewException;

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    /**
     * The dead letter endpoint uri for the Dead Letter error handler.
     */
    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public String getDeadLetterHandleNewException() {
        return deadLetterHandleNewException;
    }

    /**
     * Whether the dead letter channel should handle (and ignore) any new exception that may been thrown during sending
     * the message to the dead letter endpoint.
     * <p/>
     * The default value is <tt>true</tt> which means any such kind of exception is handled and ignored. Set this to
     * <tt>false</tt> to let the exception be propagated back on the {@link org.apache.camel.Exchange}. This can be used
     * in situations where you use transactions, and want to use Camel's dead letter channel to deal with exceptions
     * during routing, but if the dead letter channel itself fails because of a new exception being thrown, then by
     * setting this to <tt>false</tt> the new exceptions is propagated back and set on the
     * {@link org.apache.camel.Exchange}, which allows the transaction to detect the exception, and rollback.
     */
    public void setDeadLetterHandleNewException(String deadLetterHandleNewException) {
        this.deadLetterHandleNewException = deadLetterHandleNewException;
    }

    @Override
    public boolean supportTransacted() {
        return false;
    }

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        DeadLetterChannelDefinition answer = new DeadLetterChannelDefinition();
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(DeadLetterChannelDefinition other) {
        other.setLoggerBean(getLoggerBean());
        if (getRedeliveryPolicy() != null) {
            // TODO: copy
            //            other.setRedeliveryPolicy(getRedeliveryPolicy().copy());
        }
        other.setOnRedeliveryProcessor(getOnRedeliveryProcessor());
        other.setOnRedeliveryRef(getOnRedeliveryRef());
        other.setRetryWhilePredicate(getRetryWhilePredicate());
        other.setRetryWhileRef(getRetryWhileRef());
        other.setDeadLetterUri(getDeadLetterUri());
        other.setOnPrepareFailureProcessor(getOnPrepareFailureProcessor());
        other.setOnPrepareFailureRef(getOnPrepareFailureRef());
        other.setOnExceptionOccurredProcessor(getOnExceptionOccurredProcessor());
        other.setOnExceptionOccurredRef(getOnExceptionOccurredRef());
        other.setDeadLetterHandleNewException(getDeadLetterHandleNewException());
        other.setUseOriginalMessage(getUseOriginalMessage());
        other.setUseOriginalBody(getUseOriginalBody());
        other.setExecutorServiceBean(getExecutorServiceBean());
        other.setExecutorServiceRef(getExecutorServiceRef());
    }

}
