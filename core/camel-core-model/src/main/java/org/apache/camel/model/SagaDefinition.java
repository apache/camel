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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;

/**
 * Enables Sagas on the route
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "saga")
@XmlAccessorType(XmlAccessType.FIELD)
public class SagaDefinition extends OutputDefinition<SagaDefinition> {

    @XmlTransient
    private CamelSagaService sagaServiceBean;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.saga.CamelSagaService")
    private String sagaService;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.model.SagaPropagation", defaultValue = "REQUIRED",
              enums = "REQUIRED,REQUIRES_NEW,MANDATORY,SUPPORTS,NOT_SUPPORTED,NEVER")
    private String propagation;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.model.SagaCompletionMode", defaultValue = "AUTO",
              enums = "AUTO,MANUAL")
    private String completionMode;
    @XmlAttribute
    @Metadata(javaType = "java.time.Duration")
    private String timeout;
    @XmlElement
    private SagaActionUriDefinition compensation;
    @XmlElement
    private SagaActionUriDefinition completion;
    @XmlElement(name = "option")
    @Metadata(label = "advanced")
    private List<PropertyExpressionDefinition> options;

    public SagaDefinition() {
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

    @Override
    public boolean isAbstract() {
        return true;
    }

    @Override
    public boolean isTopLevelOnly() {
        return true;
    }

    @Override
    public boolean isWrappingEntireOutput() {
        return true;
    }

    @Override
    public String getLabel() {
        String desc = description();
        if (ObjectHelper.isEmpty(desc)) {
            return "saga";
        } else {
            return "saga[" + desc + "]";
        }
    }

    @Override
    public String toString() {
        String desc = description();
        if (ObjectHelper.isEmpty(desc)) {
            return "Saga -> [" + outputs + "]";
        } else {
            return "Saga[" + desc + "] -> [" + outputs + "]";
        }
    }

    // Properties

    public CamelSagaService getSagaServiceBean() {
        return sagaServiceBean;
    }

    public String getSagaService() {
        return sagaService;
    }

    /**
     * Refers to the id to lookup in the registry for the specific CamelSagaService to use.
     */
    public void setSagaService(String sagaService) {
        this.sagaService = sagaService;
    }

    public SagaActionUriDefinition getCompensation() {
        return compensation;
    }

    /**
     * The compensation endpoint URI that must be called to compensate all changes done in the route. The route
     * corresponding to the compensation URI must perform compensation and complete without error. If errors occur
     * during compensation, the saga service may call again the compensation URI to retry.
     */
    public void setCompensation(SagaActionUriDefinition compensation) {
        this.compensation = compensation;
    }

    public SagaActionUriDefinition getCompletion() {
        return completion;
    }

    /**
     * The completion endpoint URI that will be called when the Saga is completed successfully. The route corresponding
     * to the completion URI must perform completion tasks and terminate without error. If errors occur during
     * completion, the saga service may call again the completion URI to retry.
     */
    public void setCompletion(SagaActionUriDefinition completion) {
        this.completion = completion;
    }

    public String getPropagation() {
        return propagation;
    }

    /**
     * Set the Saga propagation mode (REQUIRED, REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER).
     */
    public void setPropagation(String propagation) {
        this.propagation = propagation;
    }

    public String getCompletionMode() {
        return completionMode;
    }

    /**
     * Determine how the saga should be considered complete. When set to AUTO, the saga is completed when the exchange
     * that initiates the saga is processed successfully, or compensated when it completes exceptionally. When set to
     * MANUAL, the user must complete or compensate the saga using the "saga:complete" or "saga:compensate" endpoints.
     */
    public void setCompletionMode(String completionMode) {
        this.completionMode = completionMode;
    }

    public List<PropertyExpressionDefinition> getOptions() {
        return options;
    }

    /**
     * Allows to save properties of the current exchange in order to re-use them in a compensation/completion callback
     * route. Options are usually helpful e.g. to store and retrieve identifiers of objects that should be deleted in
     * compensating actions. Option values will be transformed into input headers of the compensation/completion
     * exchange.
     */
    public void setOptions(List<PropertyExpressionDefinition> options) {
        this.options = options;
    }

    public String getTimeout() {
        return timeout;
    }

    /**
     * Set the maximum amount of time for the Saga. After the timeout is expired, the saga will be compensated
     * automatically (unless a different decision has been taken in the meantime).
     */
    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    private void addOption(String option, Expression expression) {
        if (this.options == null) {
            this.options = new ArrayList<>();
        }
        this.options.add(new PropertyExpressionDefinition(option, expression));
    }

    // Builders

    public SagaDefinition compensation(String compensation) {
        if (this.compensation != null) {
            throw new IllegalStateException("Compensation has already been set");
        }
        this.compensation = new SagaActionUriDefinition(compensation);
        return this;
    }

    public SagaDefinition completion(String completion) {
        if (this.completion != null) {
            throw new IllegalStateException("Completion has already been set");
        }
        this.completion = new SagaActionUriDefinition(completion);
        return this;
    }

    public SagaDefinition propagation(SagaPropagation propagation) {
        setPropagation(propagation.name());
        return this;
    }

    public SagaDefinition sagaService(CamelSagaService sagaService) {
        this.sagaServiceBean = sagaService;
        return this;
    }

    public SagaDefinition sagaService(String sagaService) {
        setSagaService(sagaService);
        return this;
    }

    public SagaDefinition completionMode(SagaCompletionMode completionMode) {
        return completionMode(completionMode.name());
    }

    public SagaDefinition completionMode(String completionMode) {
        setCompletionMode(completionMode);
        return this;
    }

    public SagaDefinition option(String option, Expression expression) {
        addOption(option, expression);
        return this;
    }

    public SagaDefinition timeout(Duration duration) {
        return timeout(TimeUtils.printDuration(duration, true));
    }

    public SagaDefinition timeout(long timeout, TimeUnit unit) {
        return timeout(Duration.ofMillis(unit.toMillis(timeout)));
    }

    public SagaDefinition timeout(String duration) {
        setTimeout(duration);
        return this;
    }

    // Utils

    protected String description() {
        StringBuilder desc = new StringBuilder();
        addField(desc, "compensation", compensation);
        addField(desc, "completion", completion);
        addField(desc, "propagation", propagation);
        return desc.toString();
    }

    private void addField(StringBuilder builder, String key, Object value) {
        if (value == null) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(',');
        }
        builder.append(key).append(':').append(value);
    }

}
