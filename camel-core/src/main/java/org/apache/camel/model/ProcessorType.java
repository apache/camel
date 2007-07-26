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
package org.apache.camel.model;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.FromBuilder;
import org.apache.camel.builder.IdempotentConsumerBuilder;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.processor.DelegateProcessor;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import java.util.Collection;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
public abstract class ProcessorType {
    private ErrorHandlerBuilder errorHandlerBuilder;
    private Boolean inheritErrorHandlerFlag = Boolean.TRUE; // TODO not sure how else to use an optional attribute in JAXB2

    public abstract List<ProcessorType> getOutputs();

    public abstract List<InterceptorRef> getInterceptors();

    public ProcessorType interceptor(String ref) {
        getInterceptors().add(new InterceptorRef(ref));
        return this;
    }

    public ProcessorType interceptors(String... refs) {
        for (String ref : refs) {
            interceptor(ref);
        }
        return this;
    }

    public FilterType filter(ExpressionType expression) {
        FilterType filter = new FilterType();
        filter.setExpression(expression);
        getOutputs().add(filter);
        return filter;
    }

    public FilterType filter(String language, String expression) {
        return filter(new LanguageExpression(language, expression));
    }

    public ProcessorType to(String uri) {
        ToType to = new ToType();
        to.setUri(uri);
        getOutputs().add(to);
        return this;
    }

    public Processor createProcessor(RouteContext routeContext) throws Exception {
        throw new UnsupportedOperationException("Not implemented yet for class: " + getClass().getName());
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        Processor processor = makeProcessor(routeContext);
        routeContext.addEventDrivenProcessor(processor);
    }

    /**
     * Wraps the child processor in whatever necessary interceptors and error handlers
     */
    public Processor wrapProcessor(RouteContext routeContext, Processor processor) throws Exception {
    	processor = wrapProcessorInInterceptors(routeContext, processor);
        return wrapInErrorHandler(processor);
    }

    // Properties
    //-------------------------------------------------------------------------

    @XmlTransient
    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = createErrorHandlerBuilder();
        }
        return errorHandlerBuilder;
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    @XmlTransient
    public boolean isInheritErrorHandler() {
        return ObjectConverter.toBoolean(getInheritErrorHandlerFlag());
    }

    @XmlAttribute(name = "inheritErrorHandler", required = false)
    public Boolean getInheritErrorHandlerFlag() {
        return inheritErrorHandlerFlag;
    }

    public void setInheritErrorHandlerFlag(Boolean inheritErrorHandlerFlag) {
        this.inheritErrorHandlerFlag = inheritErrorHandlerFlag;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * Creates the processor and wraps it in any necessary interceptors and error handlers
     */
    protected Processor makeProcessor(RouteContext routeContext) throws Exception {
        Processor processor = createProcessor(routeContext);
        return wrapProcessor(routeContext, processor);
    }

    /**
     * A strategy method which allows derived classes to wrap the child processor in some kind of interceptor such as
     * a filter for the {@link IdempotentConsumerBuilder}.
     *
     * @param routeContext
     * @param target       the processor which can be wrapped @return the original processor or a new wrapped interceptor
     */
    protected Processor wrapProcessorInInterceptors(RouteContext routeContext, Processor target) {
        // The target is required.
        if (target == null) {
            throw new RuntimeCamelException("target provided.");
        }

        // Interceptors are optional
        DelegateProcessor first = null;
        DelegateProcessor last = null;
        List<InterceptorRef> interceptors = getInterceptors();
        if (interceptors != null) {
            for (InterceptorRef interceptorRef : interceptors) {
                DelegateProcessor p = interceptorRef.createInterceptor(routeContext);
                if (first == null) {
                    first = p;
                }
                if (last != null) {
                    last.setProcessor(p);
                }
                last = p;
            }
        }

        if (last != null) {
            last.setProcessor(target);
        }
        return first == null ? target : first;
    }

    /**
     * A strategy method to allow newly created processors to be wrapped in an error handler. This feature
     * could be disabled for child builders such as {@link IdempotentConsumerBuilder} which will rely on the
     * {@link FromBuilder} to perform the error handling to avoid doubly-wrapped processors with 2 nested error handlers
     */
    protected Processor wrapInErrorHandler(Processor processor) throws Exception {
        return getErrorHandlerBuilder().createErrorHandler(processor);
    }

    protected ErrorHandlerBuilder createErrorHandlerBuilder() {
        if (isInheritErrorHandler()) {
            return new DeadLetterChannelBuilder();
        }
        else {
            return new NoErrorHandlerBuilder();
        }
    }
}
