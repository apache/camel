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

import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.util.CollectionStringBuffer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "choice")
@XmlAccessorType(XmlAccessType.FIELD)
public class ChoiceType extends ProcessorType {
    @XmlElementRef
    private List<InterceptorType> interceptors = new ArrayList<InterceptorType>();
    @XmlElementRef
    private List<WhenType> whenClauses = new ArrayList<WhenType>();
    @XmlElement(required = false)
    private OtherwiseType otherwise;

    @Override
    public String toString() {
        return "Choice[ " + getWhenClauses() + " " + getOtherwise() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        List<FilterProcessor> filters = new ArrayList<FilterProcessor>();
        for (WhenType whenClaus : whenClauses) {
            filters.add(whenClaus.createProcessor(routeContext));
        }
        Processor otherwiseProcessor = null;
        if (otherwise != null) {
            otherwiseProcessor = otherwise.createProcessor(routeContext);
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }

    // Fluent API
    // -------------------------------------------------------------------------
    public ChoiceType when(Predicate predicate) {
        getWhenClauses().add(new WhenType(predicate));
        return this;
    }

    public OtherwiseType otherwise() {
        OtherwiseType answer = new OtherwiseType();
        setOtherwise(answer);
        return answer;
    }

    public ChoiceType proceed() {
        super.proceed();
        return this;
    }

    public ChoiceType to(Endpoint endpoint) {
        super.to(endpoint);
        return this;
    }

    public ChoiceType to(Collection<Endpoint> endpoints) {
        super.to(endpoints);
        return this;
    }

    public ChoiceType to(Endpoint... endpoints) {
        super.to(endpoints);
        return this;
    }

    public ChoiceType to(String uri) {
        super.to(uri);
        return this;
    }

    public ChoiceType to(String... uris) {
        super.to(uris);
        return this;
    }

    @Override
    public ChoiceType bean(Object bean) {
        super.bean(bean);
        return this;
    }

    @Override
    public ChoiceType bean(Object bean, String method) {
        super.bean(bean, method);
        return this;
    }

    @Override
    public ChoiceType beanRef(String ref) {
        super.beanRef(ref);
        return this;
    }

    @Override
    public ChoiceType beanRef(String ref, String method) {
        super.beanRef(ref, method);
        return this;
    }

    @Override
    public ChoiceType convertBodyTo(Class type) {
        super.convertBodyTo(type);
        return this;
    }

    @Override
    public ChoiceType convertFaultBodyTo(Class type) {
        super.convertFaultBodyTo(type);
        return this;
    }

    @Override
    public ChoiceType convertOutBodyTo(Class type) {
        super.convertOutBodyTo(type);
        return this;
    }

    @Override
    public ChoiceType inheritErrorHandler(boolean condition) {
        super.inheritErrorHandler(condition);
        return this;
    }

    @Override
    public ChoiceType intercept(DelegateProcessor interceptor) {
        super.intercept(interceptor);
        return this;
    }

    @Override
    public ChoiceType interceptor(String ref) {
        super.interceptor(ref);
        return this;
    }

    @Override
    public ChoiceType interceptors(String... refs) {
        super.interceptors(refs);
        return this;
    }

    @Override
    public ChoiceType pipeline(Collection<Endpoint> endpoints) {
        super.pipeline(endpoints);
        return this;
    }

    @Override
    public ChoiceType pipeline(Endpoint... endpoints) {
        super.pipeline(endpoints);
        return this;
    }

    @Override
    public ChoiceType pipeline(String... uris) {
        super.pipeline(uris);
        return this;
    }

    @Override
    public ChoiceType process(Processor processor) {
        super.process(processor);
        return this;
    }

    @Override
    public ChoiceType recipientList(Expression receipients) {
        super.recipientList(receipients);
        return this;
    }

    @Override
    public ChoiceType removeHeader(String name) {
        super.removeHeader(name);
        return this;
    }

    @Override
    public ChoiceType removeOutHeader(String name) {
        super.removeOutHeader(name);
        return this;
    }

    @Override
    public ChoiceType removeProperty(String name) {
        super.removeProperty(name);
        return this;
    }

    @Override
    public ChoiceType setBody(Expression expression) {
        super.setBody(expression);
        return this;
    }

    @Override
    public ChoiceType setHeader(String name, Expression expression) {
        super.setHeader(name, expression);
        return this;
    }

    @Override
    public ChoiceType setOutBody(Expression expression) {
        super.setOutBody(expression);
        return this;
    }

    @Override
    public ChoiceType setOutHeader(String name, Expression expression) {
        super.setOutHeader(name, expression);
        return this;
    }

    @Override
    public ChoiceType setProperty(String name, Expression expression) {
        super.setProperty(name, expression);
        return this;
    }

    @Override
    public ChoiceType trace() {
        super.trace();
        return this;
    }

    @Override
    public ChoiceType trace(String category) {
        super.trace(category);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public String getLabel() {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        List<WhenType> list = getWhenClauses();
        for (WhenType whenType : list) {
            buffer.append(whenType.getLabel());
        }
        return buffer.toString();
    }

    public List<WhenType> getWhenClauses() {
        return whenClauses;
    }

    public void setWhenClauses(List<WhenType> whenClauses) {
        this.whenClauses = whenClauses;
    }

    public List<ProcessorType> getOutputs() {
        if (otherwise != null) {
            return otherwise.getOutputs();
        }
        else if (whenClauses.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        else {
            WhenType when = whenClauses.get(whenClauses.size() - 1);
            return when.getOutputs();
        }
    }

    public OtherwiseType getOtherwise() {
        return otherwise;
    }

    public void setOtherwise(OtherwiseType otherwise) {
        this.otherwise = otherwise;
    }

    public List<InterceptorType> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorType> interceptors) {
        this.interceptors = interceptors;
    }
}
