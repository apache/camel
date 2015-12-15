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
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Sets the value of a message header
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "setHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class SetHeaderDefinition extends NoOutputExpressionNode {
    @XmlAttribute(required = true)
    private String headerName;

    public SetHeaderDefinition() {
    }

    public SetHeaderDefinition(String headerName, ExpressionDefinition expression) {
        super(expression);
        setHeaderName(headerName);
    }

    public SetHeaderDefinition(String headerName, Expression expression) {
        super(expression);
        setHeaderName(headerName);
    }

    public SetHeaderDefinition(String headerName, String value) {
        super(ExpressionBuilder.constantExpression(value));
        setHeaderName(headerName);
    }

    @Override
    public String toString() {
        return "SetHeader[" + getHeaderName() + ", " + getExpression() + "]";
    }

    @Override
    public String getLabel() {
        return "setHeader[" + getHeaderName() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        ObjectHelper.notNull(headerName, "headerName");
        Expression expr = getExpression().createExpression(routeContext);
        Expression nameExpr = ExpressionBuilder.parseSimpleOrFallbackToConstantExpression(getHeaderName(), routeContext.getCamelContext());
        return new SetHeaderProcessor(nameExpr, expr);
    }

    /**
     * Expression to return the value of the header
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    /**
     * Name of message header to set a new value
     * <p/>
     * The <tt>simple</tt> language can be used to define a dynamic evaluated header name to be used.
     * Otherwise a constant name will be used.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderName() {
        return headerName;
    }

}
