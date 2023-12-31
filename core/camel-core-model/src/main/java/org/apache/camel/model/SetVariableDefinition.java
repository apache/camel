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

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Sets the value of a variable
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "setVariable")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetVariableDefinition extends ExpressionNode {

    @XmlAttribute(required = true)
    private String name;

    public SetVariableDefinition() {
    }

    public SetVariableDefinition(String name, ExpressionDefinition expression) {
        super(expression);
        setName(name);
    }

    public SetVariableDefinition(String name, Expression expression) {
        super(expression);
        setName(name);
    }

    public SetVariableDefinition(String name, String value) {
        super(ExpressionBuilder.constantExpression(value));
        setName(name);
    }

    @Override
    public String toString() {
        return "SetVariable[" + getName() + ", " + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "setVariable";
    }

    @Override
    public String getLabel() {
        return "setVariable[" + getName() + "]";
    }

    /**
     * Expression to return the value of the variable
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    /**
     * Name of variable to set a new value
     * <p/>
     * The <tt>simple</tt> language can be used to define a dynamic evaluated variable name to be used. Otherwise a
     * constant name will be used.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
