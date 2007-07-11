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
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.ChoiceProcessor;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "choice")
public class ChoiceType extends ProcessorType {
    private List<WhenType> whenClauses = new ArrayList<WhenType>();
    private OtherwiseType otherwise;
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();

    @Override
    public String toString() {
        return "Choice[ " + getWhenClauses() + " " + getOtherwise() + "]";
    }

    @Override
    public Processor createProcessor(RouteType route) {
        List<FilterProcessor> filters = new ArrayList<FilterProcessor>();
        for (WhenType whenClaus : whenClauses) {
            filters.add(whenClaus.createProcessor(route));
        }
        Processor otherwiseProcessor = null;
        if (otherwise != null) {
            otherwiseProcessor = otherwise.createProcessor(route);
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }

    @XmlElementRef
    public List<WhenType> getWhenClauses() {
        return whenClauses;
    }

    public void setWhenClauses(List<WhenType> whenClauses) {
        this.whenClauses = whenClauses;
    }

    public List<ProcessorType> getOutputs() {
        return Collections.EMPTY_LIST;
    }

    @XmlElementRef
    public OtherwiseType getOtherwise() {
        return otherwise;
    }

    public void setOtherwise(OtherwiseType otherwise) {
        this.otherwise = otherwise;
    }

    @XmlElement(required = false)
    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }


}

