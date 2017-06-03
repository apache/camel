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
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.LoopProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Processes a message multiple times
 *
 * @version 
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "loop")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoopDefinition extends ExpressionNode {

    @XmlAttribute
    private Boolean copy;
    @XmlAttribute
    private Boolean doWhile;

    public LoopDefinition() {
    }

    public LoopDefinition(Expression expression) {
        super(expression);
    }

    public LoopDefinition(Predicate predicate) {
        super(predicate);
        setDoWhile(true);
    }

    public LoopDefinition(ExpressionDefinition expression) {
        super(expression);
    }

    /**
     * Enables copy mode so a copy of the input Exchange is used for each iteration.
     * @return the builder
     */
    public LoopDefinition copy() {
        setCopy(true);
        return this;
    }

    public Boolean getCopy() {
        return copy;
    }

    public Boolean getDoWhile() {
        return doWhile;
    }

    /**
     * Enables the while loop that loops until the predicate evaluates to false or null.
     */
    public void setDoWhile(Boolean doWhile) {
        this.doWhile = doWhile;
    }

    /**
     * If the copy attribute is true, a copy of the input Exchange is used for each iteration.
     * That means each iteration will start from a copy of the same message.
     * <p/>
     * By default loop will loop the same exchange all over, so each iteration may
     * have different message content.
     */
    public void setCopy(Boolean copy) {
        this.copy = copy;
    }

    @Override
    public String toString() {
        return "Loop[" + getExpression() + " -> " + getOutputs() + "]";
    }
    
    @Override
    public String getLabel() {
        return "loop[" + getExpression() + "]";
    }
    
    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor output = this.createChildProcessor(routeContext, true);
        boolean isCopy = getCopy() != null && getCopy();
        boolean isWhile = getDoWhile() != null && getDoWhile();

        Predicate predicate = null;
        Expression expression = null;
        if (isWhile) {
            predicate = getExpression().createPredicate(routeContext);
        } else {
            expression = getExpression().createExpression(routeContext);
        }
        return new LoopProcessor(output, expression, predicate, isCopy);
    }

    /**
     * Expression to define how many times we should loop. Notice the expression is only evaluated once, and should return
     * a number as how many times to loop. A value of zero or negative means no looping. The loop is like a for-loop fashion,
     * if you want a while loop, then the dynamic router may be a better choice.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }
}
