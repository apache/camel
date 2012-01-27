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
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ExpressionToPredicateAdapter;

/**
 * Represents an XML &lt;catch/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "doCatch")
@XmlAccessorType(XmlAccessType.FIELD)
public class CatchDefinition extends ProcessorDefinition<CatchDefinition> {
    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();
    @XmlElement(name = "onWhen")
    private WhenDefinition onWhen;
    @XmlElement(name = "handled")
    private ExpressionSubElementDefinition handled;
    @XmlElementRef
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    @XmlTransient
    private List<Class<? extends Throwable>> exceptionClasses;
    @XmlTransient
    private Predicate handledPolicy;

    public CatchDefinition() {
    }

    public CatchDefinition(List<Class<? extends Throwable>> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    public CatchDefinition(Class<? extends Throwable> exceptionType) {
        exceptionClasses = new ArrayList<Class<? extends Throwable>>();
        exceptionClasses.add(exceptionType);
    }

    @Override
    public String toString() {
        return "DoCatch[ " + getExceptionClasses() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "doCatch";
    }

    @Override
    public String getLabel() {
        return "doCatch[ " + getExceptionClasses() + "]";
    }

    @Override
    public CatchProcessor createProcessor(RouteContext routeContext) throws Exception {
        // create and load exceptions if not done
        if (exceptionClasses == null) {
            exceptionClasses = createExceptionClasses(routeContext.getCamelContext());
        }

        // must have at least one exception
        if (exceptionClasses.isEmpty()) {
            throw new IllegalArgumentException("At least one Exception must be configured to catch");
        }

        // do catch does not mandate a child processor
        Processor childProcessor = this.createChildProcessor(routeContext, false);

        Predicate when = null;
        if (onWhen != null) {
            when = onWhen.getExpression().createPredicate(routeContext);
        }

        Predicate handle = handledPolicy;
        if (handled != null) {
            handle = handled.createPredicate(routeContext);
        }

        return new CatchProcessor(exceptionClasses, childProcessor, when, handle);
    }

    public List<ProcessorDefinition> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;
    }

    public boolean isOutputSupported() {
        return true;
    }

    public List<Class<? extends Throwable>> getExceptionClasses() {
        return exceptionClasses;
    }

    public void setExceptionClasses(List<Class<? extends Throwable>> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }
    
    // Fluent API
    //-------------------------------------------------------------------------
    /**
     * Sets the exceptionClasses of the CatchType
     *
     * @param exceptionClasses  a list of the exception classes
     * @return the builder
     */
    public CatchDefinition exceptionClasses(List<Class<? extends Throwable>> exceptionClasses) {
        setExceptionClasses(exceptionClasses);
        return this;
    }
    
    /**
     * Sets an additional predicate that should be true before the onCatch is triggered.
     * <p/>
     * To be used for fine grained controlling whether a thrown exception should be intercepted
     * by this exception type or not.
     *
     * @param predicate  predicate that determines true or false
     * @return the builder
     */
    public CatchDefinition onWhen(Predicate predicate) {
        setOnWhen(new WhenDefinition(predicate));
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  handled or not
     * @return the builder
     * @deprecated will be removed in Camel 3.0. Instead of using handled(false) you can re-throw the exception
     * from a {@link Processor} or use the {@link ProcessorDefinition#throwException(Exception)}
     */
    @Deprecated
    public CatchDefinition handled(boolean handled) {
        Expression expression = ExpressionBuilder.constantExpression(Boolean.toString(handled));
        return handled(expression);
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  predicate that determines true or false
     * @return the builder
     * @deprecated will be removed in Camel 3.0. Instead of using handled(false) you can re-throw the exception
     * from a {@link Processor} or use the {@link ProcessorDefinition#throwException(Exception)}
     */
    @Deprecated
    public CatchDefinition handled(Predicate handled) {
        setHandledPolicy(handled);
        return this;
    }

    /**
     * Sets whether the exchange should be marked as handled or not.
     *
     * @param handled  expression that determines true or false
     * @return the builder
     * @deprecated will be removed in Camel 3.0. Instead of using handled(false) you can re-throw the exception
     * from a {@link Processor} or use the {@link ProcessorDefinition#throwException(Exception)}
     */
    @Deprecated
    public CatchDefinition handled(Expression handled) {
        setHandledPolicy(ExpressionToPredicateAdapter.toPredicate(handled));
        return this;
    }

    /**
     * Sets the exception class that the CatchType want to catch
     *
     * @param exception  the exception of class
     * @return the builder
     */
    public CatchDefinition exceptionClasses(Class<? extends Throwable> exception) {
        List<Class<? extends Throwable>> list = getExceptionClasses();
        list.add(exception);
        return this;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
    }

    public WhenDefinition getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(WhenDefinition onWhen) {
        this.onWhen = onWhen;
    }

    public Predicate getHandledPolicy() {
        return handledPolicy;
    }

    public void setHandledPolicy(Predicate handledPolicy) {
        this.handledPolicy = handledPolicy;
    }

    public ExpressionSubElementDefinition getHandled() {
        return handled;
    }

    public void setHandled(ExpressionSubElementDefinition handled) {
        this.handled = handled;
    }

    protected List<Class<? extends Throwable>> createExceptionClasses(CamelContext context) throws ClassNotFoundException {
        // must use the class resolver from CamelContext to load classes to ensure it can
        // be loaded in all kind of environments such as JEE servers and OSGi etc.
        List<String> list = getExceptions();
        List<Class<? extends Throwable>> answer = new ArrayList<Class<? extends Throwable>>(list.size());
        for (String name : list) {
            Class<Throwable> type = context.getClassResolver().resolveMandatoryClass(name, Throwable.class);
            answer.add(type);
        }
        return answer;
    }
}
