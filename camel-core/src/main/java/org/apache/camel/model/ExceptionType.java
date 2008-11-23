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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
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
public class ExceptionType extends ProcessorType<ProcessorType> {

    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();
    @XmlElement(name = "onWhen", required = false)
    private WhenType onWhen;
    @XmlElement(name = "redeliveryPolicy", required = false)
    private RedeliveryPolicyType redeliveryPolicy;
    @XmlElement(name = "handled", required = false)
    private ExpressionSubElementType handled;
    @XmlElementRef
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();
    @XmlTransient
    private List<Class> exceptionClasses;
    @XmlTransient
    private Processor errorHandler;
    @XmlTransient
    private Predicate handledPolicy;

    public ExceptionType() {
    }

    public ExceptionType(List<Class> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    public ExceptionType(Class exceptionType) {
        exceptionClasses = new ArrayList<Class>();
        exceptionClasses.add(exceptionType);
    }

    @Override
    public String toString() {
        return "Exception[" + getExceptionClasses() + (onWhen != null ? " " + onWhen : "") + " -> " + getOutputs() + "]";
    }
    
    /**
     * Catches an exception type.
     */
    @Override
    public ExceptionType onException(Class exceptionType) {
        getExceptionClasses().add(exceptionType);
        return this;
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
        // lets attach a processor to an error handler
        errorHandler = routeContext.createProcessor(this);
        ErrorHandlerBuilder builder = routeContext.getRoute().getErrorHandlerBuilder();
        builder.addErrorHandlers(this);
    }

    @Override
    public CatchProcessor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        return new CatchProcessor(getExceptionClasses(), childProcessor);
    }


    // Fluent API
    //-------------------------------------------------------------------------
    public ExceptionType handled(boolean handled) {
        ConstantLanguage constant = new ConstantLanguage();
        return handled(constant.createPredicate(Boolean.toString(handled)));
    }
    
    public ExceptionType handled(Predicate handled) {
        setHandledPolicy(handled);
        return this;
    }
    
    public ExceptionType handled(Expression handled) {
        setHandledPolicy(toPredicate(handled));
        return this;
    }

    public ExceptionType onWhen(Predicate predicate) {
        setOnWhen(new WhenType(predicate));
        return this;
    }

    public ExpressionClause<ExceptionType> onWhen() {
        onWhen = new WhenType();
        ExpressionClause<ExceptionType> clause = new ExpressionClause<ExceptionType>(this);
        onWhen.setExpression(clause);
        return clause;
    }

    public ExceptionType backOffMultiplier(double backOffMultiplier) {
        getOrCreateRedeliveryPolicy().backOffMultiplier(backOffMultiplier);
        return this;
    }

    public ExceptionType collisionAvoidanceFactor(double collisionAvoidanceFactor) {
        getOrCreateRedeliveryPolicy().collisionAvoidanceFactor(collisionAvoidanceFactor);
        return this;
    }

    public ExceptionType collisionAvoidancePercent(short collisionAvoidancePercent) {
        getOrCreateRedeliveryPolicy().collisionAvoidancePercent(collisionAvoidancePercent);
        return this;
    }

    public ExceptionType initialRedeliveryDelay(long initialRedeliveryDelay) {
        getOrCreateRedeliveryPolicy().initialRedeliveryDelay(initialRedeliveryDelay);
        return this;
    }
    
    public ExceptionType retriesExhaustedLogLevel(LoggingLevel retriesExhaustedLogLevel) {
        getOrCreateRedeliveryPolicy().retriesExhaustedLogLevel(retriesExhaustedLogLevel);
        return this;
    }

    public ExceptionType retryAttemptedLogLevel(LoggingLevel retryAttemptedLogLevel) {
        getOrCreateRedeliveryPolicy().retryAttemptedLogLevel(retryAttemptedLogLevel);
        return this;
    }
    
    public ExceptionType maximumRedeliveries(int maximumRedeliveries) {
        getOrCreateRedeliveryPolicy().maximumRedeliveries(maximumRedeliveries);
        return this;
    }

    public ExceptionType useCollisionAvoidance() {
        getOrCreateRedeliveryPolicy().useCollisionAvoidance();
        return this;
    }

    public ExceptionType useExponentialBackOff() {
        getOrCreateRedeliveryPolicy().useExponentialBackOff();
        return this;
    }

    public ExceptionType maximumRedeliveryDelay(long maximumRedeliveryDelay) {
        getOrCreateRedeliveryPolicy().maximumRedeliveryDelay(maximumRedeliveryDelay);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType<?>> outputs) {
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

    public RedeliveryPolicyType getRedeliveryPolicy() {
        return redeliveryPolicy;
    }

    public void setRedeliveryPolicy(RedeliveryPolicyType redeliveryPolicy) {
        this.redeliveryPolicy = redeliveryPolicy;
    }

    public Predicate getHandledPolicy() {
        return handledPolicy;
    }

    public void setHandled(ExpressionSubElementType handled) {
        this.handled = handled;
    }

    public ExpressionSubElementType getHandled() {
        return handled;
    }    
    
    private void setHandledFromExpressionType(RouteContext routeContext) {
        if (getHandled() != null && handledPolicy == null && routeContext != null) {  
            handled(getHandled().createPredicate(routeContext));
        }
    }

    public void setHandledPolicy(Predicate handledPolicy) {
        this.handledPolicy = handledPolicy;
    }

    public WhenType getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(WhenType onWhen) {
        this.onWhen = onWhen;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected RedeliveryPolicyType getOrCreateRedeliveryPolicy() {
        if (redeliveryPolicy == null) {
            redeliveryPolicy = new RedeliveryPolicyType();
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
}
