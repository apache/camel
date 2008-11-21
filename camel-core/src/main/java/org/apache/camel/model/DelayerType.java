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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Delayer;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;delayer/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "delayer")
@XmlAccessorType(XmlAccessType.FIELD)
public class DelayerType extends ExpressionNode {
    @XmlElement
    private Long delay = 0L;

    public DelayerType() {
    }

    public DelayerType(Expression processAtExpression) {
        super(processAtExpression);
    }

    public DelayerType(ExpressionType processAtExpression) {
        super(processAtExpression);
    }

    public DelayerType(Expression processAtExpression, long delay) {
        super(processAtExpression);
        this.delay = delay;
    }

    @Override
    public String toString() {
        return "Delayer[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "delayer";
    }

    public Long getDelay() {
        return delay;
    }

    public void setDelay(Long delay) {
        this.delay = delay;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        Expression processAtExpression = createAbsoluteTimeDelayExpression(routeContext);
        return new Delayer(childProcessor, processAtExpression, delay);
    }

    private Expression createAbsoluteTimeDelayExpression(RouteContext routeContext) {
        ExpressionType expr = getExpression();
        if (expr != null) {
            if (ObjectHelper.isNotNullAndNonEmpty(expr.getExpression()) 
                || expr.getExpressionValue() != null) {
                return expr.createExpression(routeContext);
            } 
        } 
        return null;
    }
}
