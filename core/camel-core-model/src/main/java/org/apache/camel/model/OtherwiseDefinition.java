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
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Route to be executed when all other choices evaluate to false
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "otherwise")
@XmlAccessorType(XmlAccessType.FIELD)
public class OtherwiseDefinition extends OptionalIdentifiedDefinition<OtherwiseDefinition>
        implements CopyableDefinition<OtherwiseDefinition>, Block, OutputNode {

    @XmlTransient
    private ProcessorDefinition<?> parent;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<>();

    public OtherwiseDefinition() {
    }

    protected OtherwiseDefinition(OtherwiseDefinition source) {
        super(source);
        this.parent = source.parent;
        this.outputs = ProcessorDefinitionHelper.deepCopyDefinitions(source.outputs);
    }

    @Override
    public OtherwiseDefinition copyDefinition() {
        return new OtherwiseDefinition(this);
    }

    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
    }

    @Override
    public ProcessorDefinition<?> getParent() {
        return parent;
    }

    public void setParent(ProcessorDefinition<?> parent) {
        this.parent = parent;
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        output.setParent(parent);
        outputs.add(output);
    }

    @Override
    public void setId(String id) {
        if (outputs.isEmpty()) {
            super.setId(id);
        } else {
            var last = outputs.get(outputs.size() - 1);
            last.setId(id);
        }
    }

    @Override
    public String toString() {
        return "Otherwise[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "otherwise";
    }

    @Override
    public String getLabel() {
        return "otherwise";
    }
}
