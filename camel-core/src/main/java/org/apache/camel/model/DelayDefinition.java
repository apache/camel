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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.Delayer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Delays processing for a specified length of time
 *
 * @version 
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "delay")
@XmlAccessorType(XmlAccessType.FIELD)
public class DelayDefinition extends ExpressionNode implements ExecutorServiceAwareDefinition<DelayDefinition> {

    // TODO: Camel 3.0 Should extend NoOutputExpressionNode

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute @Metadata(defaultValue = "false")
    private Boolean asyncDelayed;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean callerRunsWhenRejected;

    public DelayDefinition() {
    }

    public DelayDefinition(Expression delay) {
        super(delay);
    }

    @Override
    public String getLabel() {
        return "delay[" + getExpression() + "]";
    }

    @Override
    public String toString() {
        return "Delay[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, false);
        Expression delay = createAbsoluteTimeDelayExpression(routeContext);

        boolean async = getAsyncDelayed() != null && getAsyncDelayed();
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, async);
        ScheduledExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredScheduledExecutorService(routeContext, "Delay", this, async);

        Delayer answer = new Delayer(routeContext.getCamelContext(), childProcessor, delay, threadPool, shutdownThreadPool);
        if (getAsyncDelayed() != null) {
            answer.setAsyncDelayed(getAsyncDelayed());
        }
        if (getCallerRunsWhenRejected() == null) {
            // should be default true
            answer.setCallerRunsWhenRejected(true);
        } else {
            answer.setCallerRunsWhenRejected(getCallerRunsWhenRejected());
        }
        return answer;
    }

    private Expression createAbsoluteTimeDelayExpression(RouteContext routeContext) {
        ExpressionDefinition expr = getExpression();
        if (expr != null) {
            return expr.createExpression(routeContext);
        }
        return null;
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
     * Whether or not the caller should run the task when it was rejected by the thread pool.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param callerRunsWhenRejected whether or not the caller should run
     * @return the builder
     */
    public DelayDefinition callerRunsWhenRejected(boolean callerRunsWhenRejected) {
        setCallerRunsWhenRejected(callerRunsWhenRejected);
        return this;
    }

    /**
     * Enables asynchronous delay which means the thread will <b>noy</b> block while delaying.
     */
    public DelayDefinition asyncDelayed() {
        setAsyncDelayed(true);
        return this;
    }

    /**
     * To use a custom Thread Pool if asyncDelay has been enabled.
     */
    public DelayDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool if asyncDelay has been enabled.
     */
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

    public Boolean getAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(Boolean asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    public Boolean getCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(Boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }
}
