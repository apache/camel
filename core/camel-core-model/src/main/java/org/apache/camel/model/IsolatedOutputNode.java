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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlTransient;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlTransient
public abstract class IsolatedOutputNode<T extends IsolatedOutputNode<T>> extends OptionalIdentifiedDefinition<T>
        implements CopyableDefinition<T>, Block, OutputNode {

    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<>();

    public IsolatedOutputNode() {
    }

    public IsolatedOutputNode(T source) {
        this.outputs = ProcessorDefinitionHelper.deepCopyDefinitions(source.getOutputs());
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        outputs.add(output);
    }

}
