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

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;onException/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "onException")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExceptionType extends ProcessorType<ProcessorType> {

/*
    @XmlElementRef
    private List<InterceptorType> interceptors = new ArrayList<InterceptorType>();
*/
    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();
    @XmlElement(name = "redeliveryPolicy", required = false)
    private RedeliveryPolicyType redeliveryPolicy;
    @XmlElementRef
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();
    @XmlTransient
    private List<Class> exceptionClasses;
    @XmlTransient
    private Processor errorHandler;

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
        return "Exception[ " + getExceptionClasses() + " -> " + getOutputs() + "]";
    }

    /**
     * Allows an exception handler to create a new redelivery policy for this exception type
     * @param parentPolicy the current redelivery policy
     * @return a newly created redelivery policy, or return the original policy if no customization is required
     * for this exception handler.
     */
    public RedeliveryPolicy createRedeliveryPolicy(RedeliveryPolicy parentPolicy) {
        if (redeliveryPolicy != null) {
            return redeliveryPolicy.createRedeliveryPolicy(parentPolicy);
        } else if (errorHandler != null) {
            // lets create a new error handler that has no retries
            RedeliveryPolicy answer = parentPolicy.copy();
            answer.setMaximumRedeliveries(0);
            return answer;
        }
        return parentPolicy;
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
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
