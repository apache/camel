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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Transforms the message body based on an expression
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "transform")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransformDefinition extends ExpressionNode {

    public TransformDefinition() {
    }

    public TransformDefinition(Expression expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "Transform[" + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "transform";
    }

    @Override
    public String getLabel() {
        return "transform[" + getExpression() + "]";
    }

    /**
     * Expression to return the transformed message body (the new message body
     * to use)
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

}
