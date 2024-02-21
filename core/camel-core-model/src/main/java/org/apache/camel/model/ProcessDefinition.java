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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * Calls a Camel processor
 */
@Metadata(label = "eip,endpoint")
@XmlRootElement(name = "process")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProcessDefinition extends NoOutputDefinition<ProcessDefinition> {

    @XmlTransient
    private Processor processor;

    @XmlAttribute(required = true)
    private String ref;

    public ProcessDefinition() {
    }

    public ProcessDefinition(Processor processor) {
        this.processor = processor;
    }

    @Override
    public String toString() {
        if (ref != null) {
            return "process[ref:" + ref + "]";
        } else {
            // do not invoke toString on the processor as we do not know what it
            // would do
            String id = ObjectHelper.getIdentityHashCode(processor);
            return "process[Processor@" + id + "]";
        }
    }

    @Override
    public String getShortName() {
        return "process";
    }

    @Override
    public String getLabel() {
        if (ref != null) {
            return "ref:" + ref;
        } else if (processor != null) {
            // do not invoke toString on the processor as we do not know what it
            // would do
            String id = ObjectHelper.getIdentityHashCode(processor);
            return "Processor@" + id;
        } else {
            return "";
        }
    }

    public Processor getProcessor() {
        return processor;
    }

    public String getRef() {
        return ref;
    }

    /**
     * Reference to the Processor to lookup in the registry to use.
     *
     * Can also be used for creating new beans by their class name by prefixing with #class, eg
     * #class:com.foo.MyClassType.
     *
     * And it is also possible to refer to singleton beans by their type in the registry by prefixing with #type:
     * syntax, eg #type:com.foo.MyClassType
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

}
