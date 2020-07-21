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
import org.apache.camel.Predicate;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Processes a message multiple times
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "loop")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoopDefinition extends OutputExpressionNode {

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String copy;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String doWhile;

    public LoopDefinition() {
    }

    public LoopDefinition(Expression expression) {
        super(expression);
    }

    public LoopDefinition(Predicate predicate) {
        super(predicate);
        setDoWhile(Boolean.toString(true));
    }

    public LoopDefinition(ExpressionDefinition expression) {
        super(expression);
    }

    /**
     * Enables copy mode so a copy of the input Exchange is used for each
     * iteration.
     * 
     * @return the builder
     */
    public LoopDefinition copy() {
        setCopy(Boolean.toString(true));
        return this;
    }

    public String getCopy() {
        return copy;
    }

    public String getDoWhile() {
        return doWhile;
    }

    /**
     * Enables the while loop that loops until the predicate evaluates to false
     * or null.
     */
    public void setDoWhile(String doWhile) {
        this.doWhile = doWhile;
    }

    /**
     * If the copy attribute is true, a copy of the input Exchange is used for
     * each iteration. That means each iteration will start from a copy of the
     * same message.
     * <p/>
     * By default loop will loop the same exchange all over, so each iteration
     * may have different message content.
     */
    public void setCopy(String copy) {
        this.copy = copy;
    }

    @Override
    public String toString() {
        return "Loop[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "loop";
    }

    @Override
    public String getLabel() {
        return "loop[" + getExpression() + "]";
    }

    /**
     * Expression to define how many times we should loop. Notice the expression
     * is only evaluated once, and should return a number as how many times to
     * loop. A value of zero or negative means no looping. The loop is like a
     * for-loop fashion, if you want a while loop, then the dynamic router may
     * be a better choice.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }
}
