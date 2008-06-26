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
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;setHeader/&gt; element
 */
@XmlRootElement(name = "setHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetHeaderType extends ExpressionNode {
    @XmlAttribute
    private String headerName;
    @XmlAttribute(required = false)
    private String value;
    
    public SetHeaderType() {
    }

    public SetHeaderType(String headerName, ExpressionType expression) {
        super(expression);
        setHeaderName(headerName);
    }

    public SetHeaderType(String headerName, Expression expression) {
        super(expression);
        setHeaderName(headerName);        
    }

    public SetHeaderType(String headerName, String value) {
        super(ExpressionBuilder.constantExpression(value));
        setHeaderName(headerName);        
    }   
    
    @Override
    public String toString() {
        return "SetHeader[ " + getHeaderName() + ", " + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "setHeader";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Expression expr;

        if (getValue() == null) {
            expr = getExpression().createExpression(routeContext);
        } else {
            expr = ExpressionBuilder.constantExpression(getValue());
        }

        Processor childProcessor = routeContext.createProcessor(this);
        
        return new SetHeaderProcessor(getHeaderName(), expr, childProcessor);
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
