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
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.ScriptProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Executes a script from a language which does not change the message body.
 */
@Metadata(label = "eip,transformation")
@XmlRootElement(name = "script")
@XmlAccessorType(XmlAccessType.FIELD)
public class ScriptDefinition extends NoOutputExpressionNode {

    public ScriptDefinition() {
    }

    public ScriptDefinition(Expression expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "Script[" + getExpression() + "]";
    }

    @Override
    public String getShortName() {
        return "script";
    }

    @Override
    public String getLabel() {
        return "script[" + getExpression() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Expression expr = getExpression().createExpression(routeContext);
        return new ScriptProcessor(expr);
    }

    /**
     * Expression to return the transformed message body (the new message body to use)
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

}
