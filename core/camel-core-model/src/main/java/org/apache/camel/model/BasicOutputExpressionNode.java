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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;

/**
 * A basic {@link BasicOutputExpressionNode} which support outputs.
 * <p/>
 * This node is to be extended by definitions which should have expression and outputs both should not be a processor,
 * such as {@link WhenDefinition}.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlTransient
public abstract class BasicOutputExpressionNode extends BasicExpressionNode<BasicOutputExpressionNode>
        implements Block, OutputNode {

    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<>();

    public BasicOutputExpressionNode() {
    }

    public BasicOutputExpressionNode(BasicOutputExpressionNode source) {
        super(source);
        this.outputs = ProcessorDefinitionHelper.deepCopyDefinitions(source.outputs);
    }

    public BasicOutputExpressionNode(ExpressionDefinition expression) {
        super(expression);
    }

    public BasicOutputExpressionNode(Expression expression) {
        super(expression);
    }

    public BasicOutputExpressionNode(Predicate predicate) {
        super(predicate);
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
        this.outputs.add(output);
    }
}
