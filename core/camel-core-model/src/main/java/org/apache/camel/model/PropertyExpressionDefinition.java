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
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * A key value pair where the value is an expression.
 *
 * @see PropertyDefinition
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "propertyExpression")
@XmlAccessorType(XmlAccessType.FIELD)
public class PropertyExpressionDefinition {
    @XmlAttribute(required = true)
    private String key;
    @XmlElementRef
    private ExpressionDefinition expression;

    public PropertyExpressionDefinition() {
    }

    public PropertyExpressionDefinition(String key, Expression expression) {
        this.key = key;
        this.expression = new ExpressionDefinition(expression);
    }

    /**
     * Property key
     */
    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        this.expression = expression;
    }
}
