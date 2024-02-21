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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Error handler with dead letter queue.
 */
@Metadata(label = "configuration,error")
@XmlRootElement(name = "deadLetterChannel")
@XmlAccessorType(XmlAccessType.FIELD)
public class DeadLetterChannelDefinition extends DefaultErrorHandlerDefinition {

    @XmlAttribute(required = true)
    private String deadLetterUri;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String deadLetterHandleNewException;

    public DeadLetterChannelDefinition() {
    }

    public DeadLetterChannelDefinition(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public DeadLetterChannelDefinition(Endpoint deadLetterUri) {
        this.deadLetterUri = deadLetterUri.getEndpointUri();
    }

    @Override
    protected RedeliveryPolicyDefinition createRedeliveryPolicy() {
        RedeliveryPolicyDefinition answer = super.createRedeliveryPolicy();
        // DLC do not log exhausted by default
        answer.setLogExhausted("false");
        return answer;
    }

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
    public ErrorHandlerFactory cloneBuilder() {
        DeadLetterChannelDefinition answer = new DeadLetterChannelDefinition();
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(DeadLetterChannelDefinition other) {
        other.setDeadLetterUri(getDeadLetterUri());
        other.setDeadLetterHandleNewException(getDeadLetterHandleNewException());
        super.cloneBuilder(other);
    }

    /**
     * The dead letter endpoint uri for the Dead Letter error handler.
     */
    public DeadLetterChannelDefinition deadLetterUri(String deadLetterUri) {
        setDeadLetterUri(deadLetterUri);
        return this;
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
     *
     * @param  handleNewException <tt>true</tt> to handle (and ignore), <tt>false</tt> to catch and propagated the
     *                            exception on the {@link org.apache.camel.Exchange}
     * @return                    the builder
     */
    public DefaultErrorHandlerDefinition deadLetterHandleNewException(boolean handleNewException) {
        setDeadLetterHandleNewException(handleNewException ? "true" : "false");
        return this;
    }

}
