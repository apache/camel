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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;wireTap/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "wireTap")
@XmlAccessorType(XmlAccessType.FIELD)
public class WireTapDefinition extends SendDefinition<WireTapDefinition> implements ExecutorServiceAwareDefinition<ProcessorDefinition> {

    @XmlTransient
    private Processor newExchangeProcessor;
    @XmlAttribute(name = "processorRef")
    private String newExchangeProcessorRef;
    @XmlElement(name = "body")
    private ExpressionSubElementDefinition newExchangeExpression;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private Boolean copy;

    public WireTapDefinition() {
    }

    public WireTapDefinition(String uri) {
        setUri(uri);
    }

    public WireTapDefinition(Endpoint endpoint) {
        setEndpoint(endpoint);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Endpoint endpoint = resolveEndpoint(routeContext);

        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, "WireTap", this);
        if (executorService == null) {
            executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newDefaultThreadPool(this, "WireTap");
        }
        WireTapProcessor answer = new WireTapProcessor(endpoint, getPattern(), executorService);
        if (isCopy() == null) {
            // should default be true
            answer.setCopy(true);
        } else {
            answer.setCopy(isCopy());
        }

        if (newExchangeProcessorRef != null) {
            newExchangeProcessor = routeContext.lookup(newExchangeProcessorRef, Processor.class);
        }
        answer.setNewExchangeProcessor(newExchangeProcessor);
        if (newExchangeExpression != null) {
            answer.setNewExchangeExpression(newExchangeExpression.createExpression(routeContext));
        }

        return answer;
    }

    public ExchangePattern getPattern() {
        return ExchangePattern.InOnly;
    }

    @Override
    public String toString() {
        return "WireTap[" + getLabel() + "]";
    }

    @Override
    public String getShortName() {
        return "wireTap";
    }
    
    public ProcessorDefinition executorService(ExecutorService executorService) {
        // wiretap has no outputs and therefore we cannot use custom wiretap builder methods in Java DSL
        // as the Java DSL is stretched so far we can using regular Java
        throw new UnsupportedOperationException("wireTap does not support these builder methods");
    }
    
    public ProcessorDefinition executorServiceRef(String executorServiceRef) {
        // wiretap has no outputs and therefore we cannot use custom wiretap builder methods in Java DSL
        // as the Java DSL is stretched so far we can using regular Java
        throw new UnsupportedOperationException("wireTap does not support these builder methods");
    }

    public Processor getNewExchangeProcessor() {
        return newExchangeProcessor;
    }

    public void setNewExchangeProcessor(Processor processor) {
        this.newExchangeProcessor = processor;
    }

    public String getNewExchangeProcessorRef() {
        return newExchangeProcessorRef;
    }

    public void setNewExchangeProcessorRef(String ref) {
        this.newExchangeProcessorRef = ref;
    }

    public ExpressionSubElementDefinition getNewExchangeExpression() {
        return newExchangeExpression;
    }

    public void setNewExchangeExpression(ExpressionSubElementDefinition expression) {
        this.newExchangeExpression = expression;
    }

    public void setNewExchangeExpression(Expression expression) {
        this.newExchangeExpression = new ExpressionSubElementDefinition(expression);
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

    public Boolean isCopy() {
        return copy;
    }

    public void setCopy(Boolean copy) {
        this.copy = copy;
    }
}
