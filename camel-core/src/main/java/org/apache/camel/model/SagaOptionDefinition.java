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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Allows to declare options on sagas
 *
 */
@Metadata(label = "eip,routing")
@XmlAccessorType(XmlAccessType.FIELD)
public class SagaOptionDefinition {

    @XmlAttribute(required = true)
    private String optionName;

    @XmlElementRef
    private ExpressionDefinition expression;


    public SagaOptionDefinition() {
    }

    public SagaOptionDefinition(String optionName, Expression expression) {
        setOptionName(optionName);
        setExpression(ExpressionNodeHelper.toExpressionDefinition(expression));
    }

    @Override
    public String toString() {
        return "option:" + getOptionName() + "=" + getExpression();
    }

    /**
     * Name of the option. It identifies the name of the header where the value of the expression will be stored when the
     * compensation or completion routes will be called.
     */
    public void setOptionName(String optionName) {
        this.optionName = optionName;
    }

    public String getOptionName() {
        return optionName;
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    /**
     * The expression to be used to determine the value of the option.
     */
    public void setExpression(ExpressionDefinition expression) {
        this.expression = expression;
    }
}
