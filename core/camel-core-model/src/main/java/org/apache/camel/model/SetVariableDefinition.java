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
import org.apache.camel.spi.annotations.DslArg;

/**
 * Sets the value of a variable
 */
@Metadata(label = "eip,messaging,transformation",
          description = "Sets a variable to a value computed by an expression")
@XmlRootElement(name = "setVariable")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetVariableDefinition extends ExpressionNode {

    @XmlAttribute(required = true)
    @Metadata(description = "Name of variable to set a new value. The simple language can be used to define a dynamic evaluated variable name. Otherwise a constant name will be used.")
    @DslArg
    private String name;

    public SetVariableDefinition() {
    }

    protected SetVariableDefinition(SetVariableDefinition source) {
        super(source);
        this.name = source.name;
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
    public SetVariableDefinition copyDefinition() {
        return new SetVariableDefinition(this);
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

    @Override
    @Metadata(description = "The expression whose result is used as the variable value.")
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
