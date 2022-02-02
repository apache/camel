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
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Allows declaring options on Sagas
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "sagaOption")
@XmlAccessorType(XmlAccessType.FIELD)
public class SagaOptionDefinition extends ExpressionNode {

    @XmlAttribute(required = true)
    private String name;

    public SagaOptionDefinition() {
    }

    public SagaOptionDefinition(String name, Expression expression) {
        setName(name);
        setExpression(ExpressionNodeHelper.toExpressionDefinition(expression));
    }

    @Override
    public String toString() {
        return "option:" + getName() + "=" + getExpression();
    }

    /**
     * Name of the option. It identifies the name of the header where the value of the expression will be stored when
     * the compensation or completion routes will be called.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public ExpressionDefinition getExpressionType() {
        return getExpression();
    }

    @Override
    public void setExpressionType(ExpressionDefinition expressionType) {
        setExpression(expressionType);
    }

    @Override
    public String getShortName() {
        return "sagaOption";
    }
}
