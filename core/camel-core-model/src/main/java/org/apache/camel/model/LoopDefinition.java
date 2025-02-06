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
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Processes a message multiple times
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "loop")
@XmlAccessorType(XmlAccessType.FIELD)
public class LoopDefinition extends OutputExpressionNode {

    @XmlTransient
    private Processor onPrepareProcessor;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String copy;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String doWhile;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String breakOnShutdown;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.Processor")
    private String onPrepare;

    public LoopDefinition() {
    }

    protected LoopDefinition(LoopDefinition source) {
        super(source);
        this.copy = source.copy;
        this.doWhile = source.doWhile;
        this.breakOnShutdown = source.breakOnShutdown;
        this.onPrepareProcessor = source.onPrepareProcessor;
        this.onPrepare = source.onPrepare;
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

    @Override
    public LoopDefinition copyDefinition() {
        return new LoopDefinition(this);
    }

    public Processor getOnPrepareProcessor() {
        return onPrepareProcessor;
    }

    /**
     * Enables copy mode so a copy of the input Exchange is used for each iteration.
     */
    public LoopDefinition copy() {
        setCopy(Boolean.toString(true));
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} for each loop iteration. This can
     * be used to deep-clone messages, or any custom logic needed before the looping executes..
     *
     * @param  onPrepare reference to the processor to lookup in the {@link org.apache.camel.spi.Registry}
     * @return           the builder
     */
    public LoopDefinition onPrepare(Processor onPrepare) {
        this.onPrepareProcessor = onPrepare;
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} for each loop iteration. This can
     * be used to deep-clone messages, or any custom logic needed before the looping executes..
     *
     * @param  onPrepare reference to the processor to lookup in the {@link org.apache.camel.spi.Registry}
     * @return           the builder
     */
    public LoopDefinition onPrepare(String onPrepare) {
        setOnPrepare(onPrepare);
        return this;
    }

    public String getCopy() {
        return copy;
    }

    public String getDoWhile() {
        return doWhile;
    }

    /**
     * Enables the while loop that loops until the predicate evaluates to false or null.
     */
    public void setDoWhile(String doWhile) {
        this.doWhile = doWhile;
    }

    /**
     * If the copy attribute is true, a copy of the input Exchange is used for each iteration. That means each iteration
     * will start from a copy of the same message.
     * <p/>
     * By default loop will loop the same exchange all over, so each iteration may have different message content.
     */
    public void setCopy(String copy) {
        this.copy = copy;
    }

    public LoopDefinition breakOnShutdown() {
        setBreakOnShutdown(Boolean.toString(true));
        return this;
    }

    /**
     * If the breakOnShutdown attribute is true, then the loop will not iterate until it reaches the end when Camel is
     * shut down.
     */
    public void setBreakOnShutdown(String breakOnShutdown) {
        this.breakOnShutdown = breakOnShutdown;
    }

    public String getBreakOnShutdown() {
        return breakOnShutdown;
    }

    public String getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(String onPrepare) {
        this.onPrepare = onPrepare;
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
     * Expression to define how many times we should loop. Notice the expression is only evaluated once, and should
     * return a number as how many times to loop. A value of zero or negative means no looping. The loop is like a
     * for-loop fashion, if you want a while loop, then the dynamic router may be a better choice.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }
}
