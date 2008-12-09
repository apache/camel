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
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;setProperty/&gt; element
 */
@XmlRootElement(name = "setProperty")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetPropertyType extends ExpressionNode {
    @XmlAttribute(required = true)
    private String propertyName;
    
    public SetPropertyType() {
    }

    public SetPropertyType(String propertyName, ExpressionType expression) {
        super(expression);
        setPropertyName(propertyName);
    }

    public SetPropertyType(String propertyName, Expression expression) {
        super(expression);
        setPropertyName(propertyName);        
    }

    public SetPropertyType(String propertyName, String value) {
        super(ExpressionBuilder.constantExpression(value));
        setPropertyName(propertyName);        
    }   
    
    @Override
    public String toString() {
        return "SetProperty[" + getPropertyName() + ", " + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "setProperty";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        ObjectHelper.notNull(getPropertyName(), "propertyName");
        Expression expr = getExpression().createExpression(routeContext);
        return ProcessorBuilder.setProperty(getPropertyName(), expr);
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
