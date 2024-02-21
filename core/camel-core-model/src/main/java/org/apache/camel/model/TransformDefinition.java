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
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Metadata;

/**
 * Transforms the message body based on an expression
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "transform")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransformDefinition extends ExpressionNode {

    @XmlAttribute
    private String fromType;

    @XmlAttribute
    private String toType;

    public TransformDefinition() {
    }

    public TransformDefinition(Expression expression) {
        super(expression);
    }

    public TransformDefinition(DataType fromType, DataType toType) {
        this.fromType = fromType.getFullName();
        this.toType = toType.getFullName();
    }

    @Override
    public String toString() {
        if (toType != null) {
            if (fromType != null) {
                return "Transform[" + fromType + ", " + toType + "]";
            } else {
                return "Transform[" + toType + "]";
            }
        }

        return "Transform[" + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "transform";
    }

    @Override
    public String getLabel() {
        if (toType != null) {
            if (fromType != null) {
                return "transform[" + fromType + ", " + toType + "]";
            } else {
                return "transform[" + toType + "]";
            }
        }

        return "transform[" + getExpression() + "]";
    }

    /**
     * Expression to return the transformed message body (the new message body to use)
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    /**
     * From type used in data type transformation.
     */
    public void setFromType(String fromType) {
        this.fromType = fromType;
    }

    public String getFromType() {
        return fromType;
    }

    /**
     * To type used as a target data type in the transformation.
     */
    public void setToType(String toType) {
        this.toType = toType;
    }

    public String getToType() {
        return toType;
    }
}
