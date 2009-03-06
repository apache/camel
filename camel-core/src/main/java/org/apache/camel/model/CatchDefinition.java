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

import org.apache.camel.Processor;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;catch/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "catch")
@XmlAccessorType(XmlAccessType.FIELD)
public class CatchDefinition extends ProcessorDefinition<CatchDefinition> {
    @XmlElement(name = "exception")
    private List<String> exceptions = new ArrayList<String>();
    @XmlElementRef
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    @XmlTransient
    private List<Class> exceptionClasses;

    public CatchDefinition() {
    }

    public CatchDefinition(List<Class> exceptionClasses) {
        this.exceptionClasses = exceptionClasses;
    }

    public CatchDefinition(Class exceptionType) {
        exceptionClasses = new ArrayList<Class>();
        exceptionClasses.add(exceptionType);
    }

    @Override
    public String toString() {
        return "Catch[ " + getExceptionClasses() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "catch";
    }

    @Override
    public String getLabel() {
        return getExceptionClasses().toString();
    }

    @Override
    public CatchProcessor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        return new CatchProcessor(getExceptionClasses(), childProcessor);
    }

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
    
    // Fluent API
    //-------------------------------------------------------------------------
    /**
     * Sets the exceptionClasses of the CatchType
     *
     * @param exceptionClasses  a list of the exception classes
     * @return the builder
     */
    public CatchDefinition exceptionClasses(List<Class> exceptionClasses) {
        setExceptionClasses(exceptionClasses);
        return this;
    }
    
    /**
     * Sets the exception class that the CatchType want to catch
     *
     * @param exception  the exception of class
     * @return the builder
     */
    public CatchDefinition exceptionClasses(Class exception) {
        List<Class> list = getExceptionClasses();
        list.add(exception);
        return this;
    }

    public List<String> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<String> exceptions) {
        this.exceptions = exceptions;
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
