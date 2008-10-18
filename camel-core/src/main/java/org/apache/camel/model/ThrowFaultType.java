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

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelException;
import org.apache.camel.Processor;
import org.apache.camel.processor.ThrowFaultProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;throwFault/&gt; element
 */
@XmlRootElement(name = "throwFault")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThrowFaultType extends ProcessorType<ThrowFaultType> {
    @XmlTransient
    private Throwable fault;
    @XmlTransient
    private Processor processor;
    @XmlAttribute (required = true)
    private String faultRef;

    public ThrowFaultType() {
    }

    @Override
    public String getShortName() {
        return "throwFault";
    }

    @Override
    public String toString() {
        if (faultRef != null) {
            return "ThrowFault[ref: " + faultRef + "]";
        } else {
            return "ThrowFault[" + fault.getClass().getCanonicalName();
        }
    }

    public void setFault(Throwable fault) {
        this.fault = fault;
    }

    public Throwable getFault() {
        return fault;
    }

    public void setFaultRef(String ref) {
        this.faultRef = ref;
    }

    public String getFaultRef() {
        return faultRef;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        if (processor == null) {
            if (fault == null) {
                fault = routeContext.lookup(faultRef, Throwable.class);
                if (fault == null) {
                    // can't find the fault instance, create a new one
                    fault = new CamelException(faultRef);
                }
            }
            processor = new ThrowFaultProcessor(fault);
        }
        return processor;
    }

    @Override
    public List<ProcessorType<?>> getOutputs() {
        return Collections.EMPTY_LIST;
    }
}
