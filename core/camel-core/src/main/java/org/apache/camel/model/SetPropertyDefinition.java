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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Sets a named property on the message exchange
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "setProperty")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetPropertyDefinition extends ExpressionNode {
    @XmlAttribute(required = true)
    private String name;

    public SetPropertyDefinition() {
    }

    public SetPropertyDefinition(String name, ExpressionDefinition expression) {
        super(expression);
        setName(name);
    }

    public SetPropertyDefinition(String name, Expression expression) {
        super(expression);
        setName(name);
    }

    public SetPropertyDefinition(String name, String value) {
        super(ExpressionBuilder.constantExpression(value));
        setName(name);
    }

    @Override
    public String toString() {
        return "SetProperty[" + getName() + ", " + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "setProperty";
    }

    @Override
    public String getLabel() {
        return "setProperty[" + getName() + "]";
    }

    /**
     * Expression to return the value of the message exchange property
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    /**
     * Name of exchange property to set a new value.
     * <p/>
     * The <tt>simple</tt> language can be used to define a dynamic evaluated
     * exchange property name to be used. Otherwise a constant name will be
     * used.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
