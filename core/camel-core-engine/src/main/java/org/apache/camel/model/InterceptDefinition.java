/*
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
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;

/**
 * Intercepts a message at each step in the route
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "intercept")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptDefinition extends OutputDefinition<InterceptDefinition> {
    @XmlTransient
    protected final List<Processor> intercepted = new ArrayList<>();

    public InterceptDefinition() {
    }

    @Override
    public String toString() {
        return "Intercept[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "intercept";
    }

    @Override
    public String getLabel() {
        return "intercept";
    }

    @Override
    public boolean isAbstract() {
        return true;
    }

    @Override
    public boolean isTopLevelOnly() {
        return true;
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

    /**
     * Applies this interceptor only if the given predicate is true
     *
     * @param predicate the predicate
     * @return the builder
     */
    public InterceptDefinition when(@AsPredicate Predicate predicate) {
        WhenDefinition when = new WhenDefinition(predicate);
        addOutput(when);
        return this;
    }

    /**
     * This method is <b>only</b> for handling some post configuration that is
     * needed since this is an interceptor, and we have to do a bit of magic
     * logic to fixup to handle predicates with or without proceed/stop set as
     * well.
     */
    public void afterPropertiesSet() {
        if (getOutputs().size() == 0) {
            // no outputs
            return;
        }

        ProcessorDefinition<?> first = getOutputs().get(0);
        if (first instanceof WhenDefinition) {
            WhenDefinition when = (WhenDefinition)first;
            // move this outputs to the when, expect the first one
            // as the first one is the interceptor itself
            for (int i = 1; i < outputs.size(); i++) {
                ProcessorDefinition<?> out = outputs.get(i);
                when.addOutput(out);
            }
            // remove the moved from the original output, by just keeping the
            // first one
            ProcessorDefinition<?> keep = outputs.get(0);
            clearOutput();
            outputs.add(keep);
        }
    }

    public List<Processor> getIntercepted() {
        return intercepted;
    }

}
