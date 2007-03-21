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
package org.apache.camel.builder;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RedeliveryPolicy;

/**
 * A builder of a <a href="http://activemq.apache.org/camel/dead-letter-channel.html">Dead Letter Channel</a>
 *
 * @version $Revision$
 */
public class DeadLetterChannelBuilder<E extends Exchange> implements ErrorHandlerBuilder<E> {
    private RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
    private ProcessorFactory<E> deadLetterFactory;
    private Processor<E> defaultDeadLetterEndpoint;
    private Expression<E> defaultDeadLetterEndpointExpression;
    private String defaultDeadLetterEndpointUri = "log:org.apache.camel.DeadLetterChannel:error";

    public DeadLetterChannelBuilder() {
    }

    public DeadLetterChannelBuilder(Processor<E> processor) {
        this(new ConstantProcessorBuilder<E>(processor));
    }

    public DeadLetterChannelBuilder(ProcessorFactory<E> deadLetterFactory) {
        this.deadLetterFactory = deadLetterFactory;
    }

    public ErrorHandlerBuilder<E> copy() {
        DeadLetterChannelBuilder<E> answer = new DeadLetterChannelBuilder<E>(deadLetterFactory);
        answer.setRedeliveryPolicy(getRedeliveryPolicy().copy());
        return answer;
    }

    public Processor<E> createErrorHandler(Processor<E> processor) {
        Processor<E> deadLetter = getDeadLetterFactory().createProcessor();
        return new DeadLetterChannel<E>(processor, deadLetter, getRedeliveryPolicy());
    }

    // Builder methods
    //-------------------------------------------------------------------------
    public DeadLetterChannelBuilder<E> backOffMultiplier(double backOffMultiplier) {
        getRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    public DeadLetterChannelBuilder<E> collisionAvoidancePercent(short collisionAvoidancePercent) {
        getRedeliveryPolicy().collisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    public DeadLetterChannelBuilder<E> initialRedeliveryDelay(long initialRedeliveryDelay) {
        getRedeliveryPolicy().initialRedeliveryDelay(initialRedeliveryDelay);
        return this;
    }

    public DeadLetterChannelBuilder<E> maximumRedeliveries(int maximumRedeliveries) {
        getRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    public DeadLetterChannelBuilder<E> useCollisionAvoidance() {
        getRedeliveryPolicy().useCollisionAvoidance();
        return this;
    }

    public DeadLetterChannelBuilder<E> useExponentialBackOff() {
        getRedeliveryPolicy().useExponentialBackOff();
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * Sets the redelivery policy
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public ProcessorFactory<E> getDeadLetterFactory() {
        if (deadLetterFactory == null) {
            deadLetterFactory = new ProcessorFactory<E>() {
                public Processor<E> createProcessor() {
                    return getDefaultDeadLetterEndpoint();
                }
            };
        }
        return deadLetterFactory;
    }

    /**
     * Sets the default dead letter queue factory
     */
    public void setDeadLetterFactory(ProcessorFactory<E> deadLetterFactory) {
        this.deadLetterFactory = deadLetterFactory;
    }

    public Processor<E> getDefaultDeadLetterEndpoint() {
        if (defaultDeadLetterEndpoint == null) {
            defaultDeadLetterEndpoint = new RecipientList<E>(getDefaultDeadLetterEndpointExpression());
        }
        return defaultDeadLetterEndpoint;
    }

    /**
     * Sets the default dead letter endpoint used
     */
    public void setDefaultDeadLetterEndpoint(Processor<E> defaultDeadLetterEndpoint) {
        this.defaultDeadLetterEndpoint = defaultDeadLetterEndpoint;
    }

    public Expression<E> getDefaultDeadLetterEndpointExpression() {
        if (defaultDeadLetterEndpointExpression == null) {
            defaultDeadLetterEndpointExpression = ExpressionBuilder.constantExpression(getDefaultDeadLetterEndpointUri());
        }
        return defaultDeadLetterEndpointExpression;
    }

    /**
     * Sets the expression used to decide the dead letter channel endpoint for an exchange
     * if no factory is provided via {@link #setDeadLetterFactory(ProcessorFactory)}
     */
    public void setDefaultDeadLetterEndpointExpression(Expression<E> defaultDeadLetterEndpointExpression) {
        this.defaultDeadLetterEndpointExpression = defaultDeadLetterEndpointExpression;
    }

    public String getDefaultDeadLetterEndpointUri() {
        return defaultDeadLetterEndpointUri;
    }

    /**
     * Sets the default dead letter endpoint URI used if no factory is provided via {@link #setDeadLetterFactory(ProcessorFactory)}
     * and no expression is provided via {@link #setDefaultDeadLetterEndpointExpression(Expression)}
     *
     * @param defaultDeadLetterEndpointUri the default URI if no deadletter factory or expression is provided
     */
    public void setDefaultDeadLetterEndpointUri(String defaultDeadLetterEndpointUri) {
        this.defaultDeadLetterEndpointUri = defaultDeadLetterEndpointUri;
    }
}
