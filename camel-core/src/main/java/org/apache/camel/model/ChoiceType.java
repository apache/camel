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

import org.apache.camel.Endpoint;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;

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
    @XmlElement(required = false)
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
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
    //-------------------------------------------------------------------------
    public ChoiceType when(Predicate predicate) {
        getWhenClauses().add(new WhenType(predicate));
        return this;
    }

    public OtherwiseType otherwise() {
        OtherwiseType answer = new OtherwiseType();
        setOtherwise(answer);
        return answer;
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

    // Properties
    //-------------------------------------------------------------------------
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

    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }
}

