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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;

/**
 * A base class for nodes which contain an expression and a number of outputs
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class OutputExpressionNode extends ExpressionNode {
    @XmlElementRef
    protected List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();

    public OutputExpressionNode() {
    }

    public OutputExpressionNode(ExpressionDefinition expression) {
        super(expression);
    }

    public OutputExpressionNode(Expression expression) {
        super(expression);
    }

    public OutputExpressionNode(Predicate predicate) {
        super(predicate);
    }

    public List<ProcessorDefinition> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;
    }

    @Override
    public boolean isOutputSupported() {
        return true;
    }

    @Override
    public void addOutput(ProcessorDefinition output) {
        outputs.add(output);
    }
}
