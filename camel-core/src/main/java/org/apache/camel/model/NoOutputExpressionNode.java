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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;

/**
 * An {@link org.apache.camel.model.ExpressionNode} which does <b>not</b> support any outputs.
 * <p/>
 * This node is to be extended by definitions which need to support an expression but the definition should not
 * contain any outputs, such as {@link org.apache.camel.model.TransformDefinition}.
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class NoOutputExpressionNode extends ExpressionNode {

    public NoOutputExpressionNode() {
    }

    public NoOutputExpressionNode(ExpressionDefinition expression) {
        super(expression);
    }

    public NoOutputExpressionNode(Expression expression) {
        super(expression);
    }

    public NoOutputExpressionNode(Predicate predicate) {
        super(predicate);
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return Collections.emptyList();
    }

    @Override
    public boolean isOutputSupported() {
        return false;
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        // add it to the parent as we do not support outputs
        getParent().addOutput(output);
    }

    @Override
    public ExpressionNode id(String id) {
        // let parent handle assigning the id, as we do not support outputs
        getParent().id(id);
        return this;
    }
}

