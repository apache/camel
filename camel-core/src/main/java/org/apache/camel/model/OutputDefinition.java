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
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.spi.Metadata;

/**
 * A useful base class for output types
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlType(name = "output")
@XmlAccessorType(XmlAccessType.FIELD)
public class OutputDefinition<Type extends ProcessorDefinition<Type>> extends ProcessorDefinition<Type> {

    @XmlElementRef
    protected List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();

    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public boolean isOutputSupported() {
        return true;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
        if (outputs != null) {
            for (ProcessorDefinition<?> output : outputs) {
                configureChild(output);
            }
        }
    }

    @Override
    public String toString() {
        return getShortName() + " -> [" + outputs + "]";
    }
}
