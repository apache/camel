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

import java.util.concurrent.ExecutorService;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Delays processing for a specified length of time
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "delay")
@XmlAccessorType(XmlAccessType.FIELD)
public class DelayDefinition extends ExpressionNode implements ExecutorServiceAwareDefinition<DelayDefinition> {

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String asyncDelayed;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    private String callerRunsWhenRejected;

    public DelayDefinition() {
    }

    public DelayDefinition(Expression delay) {
        super(delay);
    }

    @Override
    public String getShortName() {
        return "delay";
    }

    @Override
    public String getLabel() {
        return "delay[" + getExpression() + "]";
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
        setExpression(ExpressionNodeHelper.toExpressionDefinition(ExpressionBuilder.constantExpression(delay)));
        return this;
    }

    /**
     * Whether or not the caller should run the task when it was rejected by the
     * thread pool.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param callerRunsWhenRejected whether or not the caller should run
     * @return the builder
     */
    public DelayDefinition callerRunsWhenRejected(boolean callerRunsWhenRejected) {
        setCallerRunsWhenRejected(Boolean.toString(callerRunsWhenRejected));
        return this;
    }

    /**
     * Enables asynchronous delay which means the thread will <b>not</b> block
     * while delaying.
     */
    public DelayDefinition asyncDelayed() {
        setAsyncDelayed(Boolean.toString(true));
        return this;
    }

    /**
     * Enables asynchronous delay which means the thread will <b>not</b> block
     * while delaying.
     */
    public DelayDefinition syncDelayed() {
        setAsyncDelayed(Boolean.toString(false));
        return this;
    }

    /**
     * To use a custom Thread Pool if asyncDelay has been enabled.
     */
    @Override
    public DelayDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool if asyncDelay has been enabled.
     */
    @Override
    public DelayDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Expression to define how long time to wait (in millis)
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public String getAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(String asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    public String getCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(String callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    @Override
    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }
}
