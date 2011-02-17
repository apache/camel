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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.processor.WrapProcessor;
import org.apache.camel.spi.Required;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;process/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "process")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProcessDefinition extends OutputDefinition<ProcessDefinition> {
    @XmlAttribute(required = true)
    private String ref;
    @XmlTransient
    private Processor processor;

    public ProcessDefinition() {
    }

    public ProcessDefinition(Processor processor) {
        this.processor = processor;
    }

    @Override
    public String getShortName() {
        return "process";
    }

    @Override
    public String toString() {
        return "process["
                + ((ref != null) ? "ref:" + ref : processor)
                + "]";
    }

    @Override
    public String getLabel() {
        if (ref != null) {
            return "ref:" + ref;
        } else if (processor != null) {
            return processor.toString();
        } else {
            return "";
        }
    }

    public String getRef() {
        return ref;
    }

    @Required
    public void setRef(String ref) {
        this.ref = ref;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        if (processor == null) {
            ObjectHelper.notNull(ref, "ref", this);
            processor = routeContext.lookup(getRef(), Processor.class);
            ObjectHelper.notNull(processor, "registry entry called " + getRef(), this);
        }

        // ensure its wrapped in a Service so we can manage it from eg. JMX
        // (a Processor must be a Service to be enlisted in JMX)
        if (!(processor instanceof Service)) {
            processor = new WrapProcessor(processor, processor);
        }
        return processor;
    }
}
