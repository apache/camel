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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.ErrorHandlerSupport;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.exceptionpolicy.ExceptionPolicyStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.builder.PredicateBuilder.toPredicate;

/**
 * A builder of a <a
 * href="http://camel.apache.org/dead-letter-channel.html">Dead Letter
 * Channel</a>
 *
 * @version $Revision$
 */
public class DeadLetterChannelBuilder extends ErrorHandlerBuilderSupport {
    private Logger logger = new Logger(LogFactory.getLog(DeadLetterChannel.class), LoggingLevel.ERROR);
    private ExceptionPolicyStrategy exceptionPolicyStrategy = ErrorHandlerSupport.createDefaultExceptionPolicyStrategy();
    private RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
    private Processor onRedelivery;
    private Processor failureProcessor;
    private Endpoint deadLetter;
    private String deadLetterUri;
    private Predicate handledPolicy;

    /**
     * Creates a default DeadLetterChannel with a default endpoint
     */
    public DeadLetterChannelBuilder() {
        this("log:org.apache.camel.DeadLetterChannel?level=error");
    }

    /**
     * Creates a DeadLetterChannel using the given endpoint
     *
     * @param deadLetter the dead letter queue
     */
    public DeadLetterChannelBuilder(Endpoint deadLetter) {
        setDeadLetter(deadLetter);
    }

    /**
     * Creates a DeadLetterChannel using the given endpoint
     *
     * @param uri the dead letter queue
     */
    public DeadLetterChannelBuilder(String uri) {
        setDeadLetterUri(uri);
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        DeadLetterChannel answer = new DeadLetterChannel(processor, getFailureProcessor(), deadLetterUri, onRedelivery,
                getRedeliveryPolicy(), getLogger(), getExceptionPolicyStrategy(), getHandledPolicy());
        // must enable stream cache as DeadLetterChannel can do redeliveries and
        // thus it needs to be able to read the stream again
        configure(answer);
        return answer;
    }

    public boolean supportTransacted() {
        return false;
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

    public DeadLetterChannelBuilder delay(long delay) {
        getRedeliveryPolicy().delay(delay);
        return this;
    }

    public DeadLetterChannelBuilder delayPattern(String delayPattern) {
        getRedeliveryPolicy().delayPattern(delayPattern);
        return this;
    }

    public DeadLetterChannelBuilder maximumRedeliveries(int maximumRedeliveries) {
        getRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    public DeadLetterChannelBuilder disableRedelivery() {
        getRedeliveryPolicy().maximumRedeliveries(0);
        return this;
    }

    public DeadLetterChannelBuilder maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        getRedeliveryPolicy().maximumRedeliveryDelay(maximumRedeliveryDelay);
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

    public DeadLetterChannelBuilder retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        getRedeliveryPolicy().setRetriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }

    public DeadLetterChannelBuilder retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        getRedeliveryPolicy().setRetryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }

    public DeadLetterChannelBuilder logStackTrace(boolean logStackTrace) {
        getRedeliveryPolicy().setLogStackTrace(logStackTrace);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  handled or not
     * @return the builder
     */
    public DeadLetterChannelBuilder handled(boolean handled) {
        Expression expression = ExpressionBuilder.constantExpression(Boolean.toString(handled));
        return handled(expression);
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  predicate that determines true or false
     * @return the builder
     */
    public DeadLetterChannelBuilder handled(Predicate handled) {
        this.setHandledPolicy(handled);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  expression that determines true or false
     * @return the builder
     */
    public DeadLetterChannelBuilder handled(Expression handled) {
        this.setHandledPolicy(toPredicate(handled));
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

    /**
     * Sets the exception policy to use
     */
    public DeadLetterChannelBuilder exceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        setExceptionPolicyStrategy(exceptionPolicyStrategy);
        return this;
    }

    /**
     * Sets a processor that should be processed <b>before</b> a redelivey attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     */
    public DeadLetterChannelBuilder onRedelivery(Processor processor) {
        setOnRedelivery(processor);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Processor getFailureProcessor() {
        if (failureProcessor == null) {
            if (deadLetter != null) {
                failureProcessor = new SendProcessor(deadLetter);
            } else {
                // use a recipient list since we only have an uri for the endpoint
                failureProcessor = new RecipientList(new Expression() {
                    public Object evaluate(Exchange exchange) {
                        return deadLetterUri;
                    }

                    public <T> T evaluate(Exchange exchange, Class<T> type) {
                        return exchange.getContext().getTypeConverter().convertTo(type, deadLetterUri);
                    }
                });
            }
        }
        return failureProcessor;
    }

    public void setFailureProcessor(Processor failureProcessor) {
        this.failureProcessor = failureProcessor;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetter = null;
        this.deadLetterUri = deadLetterUri;
    }

    public Endpoint getDeadLetter() {
        return deadLetter;
    }

    public void setDeadLetter(Endpoint deadLetter) {
        this.deadLetter = deadLetter;
        this.deadLetterUri = deadLetter.getEndpointUri();
    }

    public RedeliveryPolicy getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    /**
     * Sets the redelivery policy
     */
    public void setRedeliveryPolicy(RedeliveryPolicy redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Sets the exception policy strategy to use for resolving the {@link org.apache.camel.model.OnExceptionDefinition}
     * to use for a given thrown exception
     */
    public ExceptionPolicyStrategy getExceptionPolicyStrategy() {
        return exceptionPolicyStrategy;
    }

    public void setExceptionPolicyStrategy(ExceptionPolicyStrategy exceptionPolicyStrategy) {
        this.exceptionPolicyStrategy = exceptionPolicyStrategy;
    }

    public Processor getOnRedelivery() {
        return onRedelivery;
    }

    public void setOnRedelivery(Processor onRedelivery) {
        this.onRedelivery = onRedelivery;
    }

    public Predicate getHandledPolicy() {
        return handledPolicy;
    }

    public void setHandledPolicy(Predicate handled) {
        this.handledPolicy = handled;
    }

    /**
     * Sets the handled using a boolean and thus easier to use for Spring XML configuration as well
     */
    public void setHandled(boolean handled) {
        handled(handled);
    }

    @Override
    public String toString() {
        return "DeadLetterChannelBuilder(" + deadLetterUri + ")";
    }
}
