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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.language.constant.ConstantLanguage;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.builder.PredicateBuilder.toPredicate;

/**
 * Represents an XML &lt;onException/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "onException")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnExceptionDefinition extends ProcessorDefinition<ProcessorDefinition> {

    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();
    @XmlElement(name = "onWhen", required = false)
    private WhenDefinition onWhen;
    @XmlElement(name = "retryUntil", required = false)
    private ExpressionSubElementDefinition retryUntil;
    @XmlElement(name = "redeliveryPolicy", required = false)
    private RedeliveryPolicyDefinition redeliveryPolicy;
    @XmlElement(name = "handled", required = false)
    private ExpressionSubElementDefinition handled;
    @XmlAttribute(name = "onRedeliveryRef", required = false)
    private String onRedeliveryRef;
    @XmlElementRef
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    @XmlTransient
    private List<Class> exceptionClasses;
    @XmlTransient
    private Processor errorHandler;
    @XmlTransient
    private Predicate handledPolicy;
    @XmlTransient
    private Predicate retryUntilPolicy;
    @XmlTransient
    private Processor onRedelivery;

    public OnExceptionDefinition() {
    }

    public OnExceptionDefinition(List<Class> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    public OnExceptionDefinition(Class exceptionType) {
        exceptionClasses = new ArrayList<Class>();
        exceptionClasses.add(exceptionType);
    }

    @Override
    public String getShortName() {
        return "onException";
    }

    @Override
    public String toString() {
        return "OnException[" + getExceptionClasses() + (onWhen != null ? " " + onWhen : "") + " -> " + getOutputs() + "]";
    }
    
    /**
     * Allows an exception handler to create a new redelivery policy for this exception type
     * @param context the camel context
     * @param parentPolicy the current redelivery policy
     * @return a newly created redelivery policy, or return the original policy if no customization is required
     * for this exception handler.
     */
    public RedeliveryPolicy createRedeliveryPolicy(CamelContext context, RedeliveryPolicy parentPolicy) {
        if (redeliveryPolicy != null) {
            return redeliveryPolicy.createRedeliveryPolicy(context, parentPolicy);
        } else if (errorHandler != null) {
            // lets create a new error handler that has no retries
            RedeliveryPolicy answer = parentPolicy.copy();
            answer.setMaximumRedeliveries(0);
            return answer;
        }
        return parentPolicy;
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        setHandledFromExpressionType(routeContext);
        setRetryUntilFromExpressionType(routeContext);
        // lets attach a processor to an error handler
        errorHandler = routeContext.createProcessor(this);
        ErrorHandlerBuilder builder = routeContext.getRoute().getErrorHandlerBuilder();
        builder.addErrorHandlers(this);

        // lookup onRedelivery if ref is provided
        if (ObjectHelper.isNotEmpty(onRedeliveryRef)) {
            onRedelivery = routeContext.lookup(onRedeliveryRef, Processor.class);
        }
    }

    @Override
    public CatchProcessor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        return new CatchProcessor(getExceptionClasses(), childProcessor);
    }


    // Fluent API
    //-------------------------------------------------------------------------

    @Override
    public OnExceptionDefinition onException(Class exceptionType) {
        getExceptionClasses().add(exceptionType);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  handled or not
     * @return the builder
     */
    public OnExceptionDefinition handled(boolean handled) {
        ConstantLanguage constant = new ConstantLanguage();
        return handled(constant.createPredicate(Boolean.toString(handled)));
    }
    
    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  predicate that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition handled(Predicate handled) {
        setHandledPolicy(handled);
        return this;
    }
    
    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  expression that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition handled(Expression handled) {
        setHandledPolicy(toPredicate(handled));
        return this;
    }

    /**
     * Sets an additional predicate that should be true before the onException is triggered.
     * <p/>
     * To be used for fine grained controlling whether a thrown exception should be intercepted
     * by this exception type or not.
     *
     * @param predicate  predicate that determines true or false
     * @return the builder
     */
    public OnExceptionDefinition onWhen(Predicate predicate) {
        setOnWhen(new WhenDefinition(predicate));
        return this;
    }

    /**
     * Creates an expression to configure an additional predicate that should be true before the
     * onException is triggered.
     * <p/>
     * To be used for fine grained controlling whether a thrown exception should be intercepted
     * by this exception type or not.
     *
     * @return the expression clause to configure
     */
    public ExpressionClause<OnExceptionDefinition> onWhen() {
        onWhen = new WhenDefinition();
        ExpressionClause<OnExceptionDefinition> clause = new ExpressionClause<OnExceptionDefinition>(this);
        onWhen.setExpression(clause);
        return clause;
    }

    /**
     * Sets the retry until predicate.
     *
     * @param until predicate that determines when to stop retrying
     * @return the builder
     */
    public OnExceptionDefinition retryUntil(Predicate until) {
        setRetryUntilPolicy(until);
        return this;
    }

    /**
     * Sets the retry until expression.
     *
     * @param until expression that determines when to stop retrying
     * @return the builder
     */
    public OnExceptionDefinition retryUntil(Expression until) {
        setRetryUntilPolicy(toPredicate(until));
        return this;
    }

    /**
     * Sets the back off multiplier
     *
     * @param backOffMultiplier  the back off multiplier
     * @return the builder
     */
    public OnExceptionDefinition backOffMultiplier(double backOffMultiplier) {
        getOrCreateRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    /**
     * Sets the collision avoidance factor
     *
     * @param collisionAvoidanceFactor  the factor
     * @return the builder
     */
    public OnExceptionDefinition collisionAvoidanceFactor(double collisionAvoidanceFactor) {
        getOrCreateRedeliveryPolicy().collisionAvoidanceFactor(collisionAvoidanceFactor);
        return this;
    }

    /**
     * Sets the collision avoidance percentage
     *
     * @param collisionAvoidancePercent  the percentage
     * @return the builder
     */
    public OnExceptionDefinition collisionAvoidancePercent(short collisionAvoidancePercent) {
        getOrCreateRedeliveryPolicy().collisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    /**
     * Sets the fixed delay between redeliveries
     *
     * @param delay  delay in millis
     * @return the builder
     */
    public OnExceptionDefinition redeliveryDelay(long delay) {
        getOrCreateRedeliveryPolicy().redeliveryDelay(delay);
        return this;
    }

    /**
     * Sets the logging level to use when retries has exhausted
     *
     * @param retriesExhaustedLogLevel  the logging level
     * @return the builder
     */
    public OnExceptionDefinition retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        getOrCreateRedeliveryPolicy().retriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }

    /**
     * Sets the logging level to use for logging retry attempts
     *
     * @param retryAttemptedLogLevel  the logging level
     * @return the builder
     */
    public OnExceptionDefinition retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        getOrCreateRedeliveryPolicy().retryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }

    /**
     * Sets the maximum redeliveries
     * <ul>
     *   <li>5 = default value</li>
     *   <li>0 = no redeliveries</li>
     *   <li>-1 = redeliver forever</li>
     * </ul>
     *
     * @param maximumRedeliveries  the value
     * @return the builder
     */
    public OnExceptionDefinition maximumRedeliveries(int maximumRedeliveries) {
        getOrCreateRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    /**
     * Turn on collision avoidance.
     *
     * @return the builder
     */
    public OnExceptionDefinition useCollisionAvoidance() {
        getOrCreateRedeliveryPolicy().useCollisionAvoidance();
        return this;
    }

    /**
     * Turn on exponential backk off
     *
     * @return the builder
     */
    public OnExceptionDefinition useExponentialBackOff() {
        getOrCreateRedeliveryPolicy().useExponentialBackOff();
        return this;
    }

    /**
     * Sets the maximum delay between redelivery
     *
     * @param maximumRedeliveryDelay  the delay in millis
     * @return the builder
     */
    public OnExceptionDefinition maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        getOrCreateRedeliveryPolicy().maximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    /**
     * Sets a processor that should be processed <b>before</b> a redelivey attempt.
     * <p/>
     * Can be used to change the {@link org.apache.camel.Exchange} <b>before</b> its being redelivered.
     */
    public OnExceptionDefinition onRedelivery(Processor processor) {
        setOnRedelivery(processor);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public List<ProcessorDefinition> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;
    }

    public List<Class> getExceptionClasses() {
        if (exceptionClasses == null) {
            exceptionClasses = createExceptionClasses();
        }
        return exceptionClasses;
    }

    public void setExceptionClasses(List<Class> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    public Processor getErrorHandler() {
        return errorHandler;
    }

    public RedeliveryPolicyDefinition getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public void setRedeliveryPolicy(RedeliveryPolicyDefinition redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public Predicate getHandledPolicy() {
        return handledPolicy;
    }

    public void setHandled(ExpressionSubElementDefinition handled) {
        this.handled = handled;
    }

    public ExpressionSubElementDefinition getHandled() {
        return handled;
    }    

    public void setHandledPolicy(Predicate handledPolicy) {
        this.handledPolicy = handledPolicy;
    }

    public WhenDefinition getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(WhenDefinition onWhen) {
        this.onWhen = onWhen;
    }

    public ExpressionSubElementDefinition getRetryUntil() {
        return retryUntil;
    }

    public void setRetryUntil(ExpressionSubElementDefinition retryUntil) {
        this.retryUntil = retryUntil;
    }

    public Predicate getRetryUntilPolicy() {
        return retryUntilPolicy;
    }

    public void setRetryUntilPolicy(Predicate retryUntilPolicy) {
        this.retryUntilPolicy = retryUntilPolicy;
    }

    public Processor getOnRedelivery() {
        return onRedelivery;
    }

    public void setOnRedelivery(Processor onRedelivery) {
        this.onRedelivery = onRedelivery;
    }

    public String getOnRedeliveryRef() {
        return onRedeliveryRef;
    }

    public void setOnRedeliveryRef(String onRedeliveryRef) {
        this.onRedeliveryRef = onRedeliveryRef;
    }


    // Implementation methods
    //-------------------------------------------------------------------------
    protected RedeliveryPolicyDefinition getOrCreateRedeliveryPolicy() {
        if (redeliveryPolicy == null) {
            redeliveryPolicy = new RedeliveryPolicyDefinition();
        }
        return redeliveryPolicy;
    }

    protected List<Class> createExceptionClasses() {
        List<String> list = getExceptions();
        List<Class> answer = new ArrayList<Class>(list.size());
        for (String name : list) {
            Class type = ObjectHelper.loadClass(name, getClass().getClassLoader());
            answer.add(type);
        }
        return answer;
    }


    private void setHandledFromExpressionType(RouteContext routeContext) {
        if (getHandled() != null && handledPolicy == null && routeContext != null) {
            handled(getHandled().createPredicate(routeContext));
        }
    }

    private void setRetryUntilFromExpressionType(RouteContext routeContext) {
        if (getRetryUntil() != null && retryUntilPolicy == null && routeContext != null) {
            retryUntil(getRetryUntil().createPredicate(routeContext));
        }
    }

}
