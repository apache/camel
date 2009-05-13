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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Processor;
import org.apache.camel.processor.OnCompletionProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;onCompletion/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "onCompletion")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnCompletionDefinition extends OutputDefinition<OnCompletionDefinition> {

    @XmlAttribute(required = false)
    private Boolean onCompleteOnly = Boolean.TRUE;
    @XmlAttribute(required = false)
    private Boolean onFailureOnly = Boolean.TRUE;

    public OnCompletionDefinition() {
    }

    @Override
    public String toString() {
        return "Synchronize[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "onCompletion";
    }

    @Override
    public String getLabel() {
        return "onCompletion";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = createOutputsProcessor(routeContext);
        return new OnCompletionProcessor(childProcessor, onCompleteOnly, onFailureOnly);
    }

    @Override
    public ProcessorDefinition<? extends ProcessorDefinition> end() {
        // pop parent block, as we added outself as block to parent when synchronized was defined in the route
        getParent().popBlock();
        return super.end();
    }

    /**
     * Will only synchronize when the {@link org.apache.camel.Exchange} completed succesfully (no errors).
     *
     * @return the builder
     */
    public OutputDefinition onCompleteOnly() {
        // must define return type as OutputDefinition and not this type to avoid end user being able
        // to invoke onFailureOnly/onCompleteOnly more than once
        setOnCompleteOnly(Boolean.TRUE);
        setOnFailureOnly(Boolean.FALSE);
        return this;
    }

    /**
     * Will only synchronize when the {@link org.apache.camel.Exchange} ended with failure (exception or FAULT message).
     *
     * @return the builder
     */
    public OutputDefinition onFailureOnly() {
        // must define return type as OutputDefinition and not this type to avoid end user being able
        // to invoke onFailureOnly/onCompleteOnly more than once
        setOnCompleteOnly(Boolean.FALSE);
        setOnFailureOnly(Boolean.TRUE);
        return this;
    }

    public Boolean getOnCompleteOnly() {
        return onCompleteOnly;
    }

    public void setOnCompleteOnly(Boolean onCompleteOnly) {
        this.onCompleteOnly = onCompleteOnly;
    }

    public Boolean getOnFailureOnly() {
        return onFailureOnly;
    }

    public void setOnFailureOnly(Boolean onFailureOnly) {
        this.onFailureOnly = onFailureOnly;
    }
}
