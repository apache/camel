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
package org.apache.camel.builder;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.LoggingLevel;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A builder of a <a
 * href="http://activemq.apache.org/camel/dead-letter-channel.html">Dead Letter
 * Channel</a>
 * 
 * @version $Revision$
 */
public class DeadLetterChannelBuilder extends ErrorHandlerBuilderSupport {
    private RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
    private ProcessorFactory deadLetterFactory;
    private Processor defaultDeadLetterEndpoint;
    private Expression defaultDeadLetterEndpointExpression;
    private String defaultDeadLetterEndpointUri = "log:org.apache.camel.DeadLetterChannel?level=error";
    private Logger logger = DeadLetterChannel.createDefaultLogger();

    public DeadLetterChannelBuilder() {
    }

    public DeadLetterChannelBuilder(Processor processor) {
        this(new ConstantProcessorBuilder(processor));
    }

    public DeadLetterChannelBuilder(ProcessorFactory deadLetterFactory) {
        this.deadLetterFactory = deadLetterFactory;
    }

    public ErrorHandlerBuilder copy() {
        DeadLetterChannelBuilder answer = new DeadLetterChannelBuilder(deadLetterFactory);
        answer.setRedeliveryPolicy(getRedeliveryPolicy().copy());
        return answer;
    }

    public Processor createErrorHandler(Processor processor) throws Exception {
        Processor deadLetter = getDeadLetterFactory().createProcessor();
        DeadLetterChannel answer = new DeadLetterChannel(processor, deadLetter, getRedeliveryPolicy(), getLogger());
        configure(answer);
        return answer;
    }

    // Builder methods
    // -------------------------------------------------------------------------
    public DeadLetterChannelBuilder backOffMultiplier(double backOffMultiplier) {
        getRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    public DeadLetterChannelBuilder collisionAvoidancePercent(short collisionAvoidancePercent) {
        getRedeliveryPolicy().collisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    public DeadLetterChannelBuilder initialRedeliveryDelay(long initialRedeliveryDelay) {
        getRedeliveryPolicy().initialRedeliveryDelay(initialRedeliveryDelay);
        return this;
    }

    public DeadLetterChannelBuilder maximumRedeliveries(int maximumRedeliveries) {
        getRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    public DeadLetterChannelBuilder useCollisionAvoidance() {
        getRedeliveryPolicy().useCollisionAvoidance();
        return this;
    }

    public DeadLetterChannelBuilder useExponentialBackOff() {
        getRedeliveryPolicy().useExponentialBackOff();
        return this;
    }

    /**
     * Sets the logger used for caught exceptions
     */
    public DeadLetterChannelBuilder logger(Logger logger) {
        setLogger(logger);
        return this;
    }

    /**
     * Sets the logging level of exceptions caught
     */
    public DeadLetterChannelBuilder loggingLevel(LoggingLevel level) {
        getLogger().setLevel(level);
        return this;
    }

    /**
     * Sets the log used for caught exceptions
     */
    public DeadLetterChannelBuilder log(Log log) {
        getLogger().setLog(log);
        return this;
    }

    /**
     * Sets the log used for caught exceptions
     */
    public DeadLetterChannelBuilder log(String log) {
        return log(LogFactory.getLog(log));
    }

    /**
     * Sets the log used for caught exceptions
     */
    public DeadLetterChannelBuilder log(Class log) {
        return log(LogFactory.getLog(log));
    }

    // Properties
    // -------------------------------------------------------------------------
    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * Sets the redelivery policy
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public ProcessorFactory getDeadLetterFactory() {
        if (deadLetterFactory == null) {
            deadLetterFactory = new ProcessorFactory() {
                public Processor createProcessor() {
                    return getDefaultDeadLetterEndpoint();
                }
            };
        }
        return deadLetterFactory;
    }

    /**
     * Sets the default dead letter queue factory
     */
    public void setDeadLetterFactory(ProcessorFactory deadLetterFactory) {
        this.deadLetterFactory = deadLetterFactory;
    }

    public Processor getDefaultDeadLetterEndpoint() {
        if (defaultDeadLetterEndpoint == null) {
            defaultDeadLetterEndpoint = new RecipientList(getDefaultDeadLetterEndpointExpression());
        }
        return defaultDeadLetterEndpoint;
    }

    /**
     * Sets the default dead letter endpoint used
     */
    public void setDefaultDeadLetterEndpoint(Processor defaultDeadLetterEndpoint) {
        this.defaultDeadLetterEndpoint = defaultDeadLetterEndpoint;
    }

    public Expression getDefaultDeadLetterEndpointExpression() {
        if (defaultDeadLetterEndpointExpression == null) {
            defaultDeadLetterEndpointExpression = ExpressionBuilder
                .constantExpression(getDefaultDeadLetterEndpointUri());
        }
        return defaultDeadLetterEndpointExpression;
    }

    /**
     * Sets the expression used to decide the dead letter channel endpoint for
     * an exchange if no factory is provided via
     * {@link #setDeadLetterFactory(ProcessorFactory)}
     */
    public void setDefaultDeadLetterEndpointExpression(Expression defaultDeadLetterEndpointExpression) {
        this.defaultDeadLetterEndpointExpression = defaultDeadLetterEndpointExpression;
    }

    public String getDefaultDeadLetterEndpointUri() {
        return defaultDeadLetterEndpointUri;
    }

    /**
     * Sets the default dead letter endpoint URI used if no factory is provided
     * via {@link #setDeadLetterFactory(ProcessorFactory)} and no expression is
     * provided via {@link #setDefaultDeadLetterEndpointExpression(Expression)}
     * 
     * @param defaultDeadLetterEndpointUri the default URI if no deadletter
     *                factory or expression is provided
     */
    public void setDefaultDeadLetterEndpointUri(String defaultDeadLetterEndpointUri) {
        this.defaultDeadLetterEndpointUri = defaultDeadLetterEndpointUri;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }
}
