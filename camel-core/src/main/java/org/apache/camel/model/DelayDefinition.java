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
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.Delayer;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;delay/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "delay")
@XmlAccessorType(XmlAccessType.FIELD)
public class DelayDefinition extends ExpressionNode {

    public DelayDefinition() {
    }

    public DelayDefinition(Expression delay) {
        super(delay);
    }

    @Override
    public String getLabel() {
        return "delay";
    }

    @Override
    public String getShortName() {
        return "delay";
    }

    @Override
    public String toString() {
        return "Delay[" + getExpression() + " -> " + getOutputs() + "]";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the delay time in millis to delay
     *
     * @param delay delay time in millis
     * @return the builder
     */
    public DelayDefinition delayTime(Long delay) {
        setExpression(new ExpressionDefinition(ExpressionBuilder.constantExpression(delay)));
        return this;
    }
    
    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        Expression delay = createAbsoluteTimeDelayExpression(routeContext);
        return new Delayer(childProcessor, delay);
    }

    private Expression createAbsoluteTimeDelayExpression(RouteContext routeContext) {
        ExpressionDefinition expr = getExpression();
        if (expr != null) {
            if (ObjectHelper.isNotEmpty(expr.getExpression()) || expr.getExpressionValue() != null) {
                return expr.createExpression(routeContext);
            } 
        } 
        return null;
    }
}
