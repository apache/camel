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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.saga.SagaProcessorBuilder;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enables sagas on the route
 *
 * @version
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "saga")
@XmlAccessorType(XmlAccessType.FIELD)
public class SagaDefinition extends OutputDefinition<SagaDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(SagaDefinition.class);

    @XmlAttribute
    @Metadata(defaultValue = "REQUIRED")
    private SagaPropagation propagation;

    @XmlAttribute
    @Metadata(defaultValue = "AUTO")
    private SagaCompletionMode completionMode;

    @XmlAttribute
    private Long timeoutInMilliseconds;

    @XmlElement
    private SagaActionUriDefinition compensation;

    @XmlElement
    private SagaActionUriDefinition completion;

    @XmlElement(name = "option")
    private List<SagaOptionDefinition> options;

    @XmlTransient
    private CamelSagaService sagaService; // TODO add ref for xml configuration

    public SagaDefinition() {
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Optional<Endpoint> compensationEndpoint = Optional.ofNullable(this.compensation)
                .map(SagaActionUriDefinition::getUri)
                .map(routeContext::resolveEndpoint);

        Optional<Endpoint> completionEndpoint = Optional.ofNullable(this.completion)
                .map(SagaActionUriDefinition::getUri)
                .map(routeContext::resolveEndpoint);

        Map<String, Expression> optionsMap = new TreeMap<>();
        if (this.options != null) {
            for (SagaOptionDefinition optionDef : this.options) {
                String optionName = optionDef.getOptionName();
                Expression expr = optionDef.getExpression();
                optionsMap.put(optionName, expr);
            }
        }

        CamelSagaStep step = new CamelSagaStep(compensationEndpoint, completionEndpoint, optionsMap, Optional.ofNullable(timeoutInMilliseconds));

        SagaPropagation propagation = this.propagation;
        if (propagation == null) {
            // default propagation mode
            propagation = SagaPropagation.REQUIRED;
        }

        SagaCompletionMode completionMode = this.completionMode;
        if (completionMode == null) {
            // default completion mode
            completionMode = SagaCompletionMode.defaultCompletionMode();
        }

        Processor childProcessor = this.createChildProcessor(routeContext, true);
        CamelSagaService camelSagaService = findSagaService(routeContext.getCamelContext());

        camelSagaService.registerStep(step);

        return new SagaProcessorBuilder()
                .camelContext(routeContext.getCamelContext())
                .childProcessor(childProcessor)
                .sagaService(camelSagaService)
                .step(step)
                .propagation(propagation)
                .completionMode(completionMode)
                .build();
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


    public SagaActionUriDefinition getCompensation() {
        return compensation;
    }

    /**
     * The compensation endpoint URI that must be called to compensate all changes done in the route.
     * The route corresponding to the compensation URI must perform compensation and complete without error.
     *
     * If errors occur during compensation, the saga service may call again the compensation URI to retry.
     */
    public void setCompensation(SagaActionUriDefinition compensation) {
        this.compensation = compensation;
    }

    public SagaActionUriDefinition getCompletion() {
        return completion;
    }

    /**
     * The completion endpoint URI that will be called when the Saga is completed successfully.
     * The route corresponding to the completion URI must perform completion tasks and terminate without error.
     *
     * If errors occur during completion, the saga service may call again the completion URI to retry.
     */
    public void setCompletion(SagaActionUriDefinition completion) {
        this.completion = completion;
    }

    public SagaPropagation getPropagation() {
        return propagation;
    }

    /**
     * Set the Saga propagation mode (REQUIRED, REQUIRES_NEW, MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER).
     */
    public void setPropagation(SagaPropagation propagation) {
        this.propagation = propagation;
    }

    public SagaCompletionMode getCompletionMode() {
        return completionMode;
    }

    /**
     * Determine how the saga should be considered complete. When set to AUTO, the saga is completed when the exchange that
     * initiates the saga is processed successfully, or compensated when it completes exceptionally.
     *
     * When set to MANUAL, the user must complete or compensate the saga using the "saga:complete" or "saga:compensate" endpoints.
     */
    public void setCompletionMode(SagaCompletionMode completionMode) {
        this.completionMode = completionMode;
    }

    public CamelSagaService getSagaService() {
        return sagaService;
    }

    public void setSagaService(CamelSagaService sagaService) {
        this.sagaService = sagaService;
    }

    public List<SagaOptionDefinition> getOptions() {
        return options;
    }

    /**
     * Allows to save properties of the current exchange in order to re-use them in a compensation/completion callback route.
     * Options are usually helpful e.g. to store and retrieve identifiers of objects that should be deleted in compensating actions.
     *
     * Option values will be transformed into input headers of the compensation/completion exchange.
     */
    public void setOptions(List<SagaOptionDefinition> options) {
        this.options = options;
    }

    public Long getTimeoutInMilliseconds() {
        return timeoutInMilliseconds;
    }

    /**
     * Set the maximum amount of time for the Saga. After the timeout is expired, the saga will be compensated
     * automatically (unless a different decision has been taken in the meantime).
     */
    public void setTimeoutInMilliseconds(Long timeoutInMilliseconds) {
        this.timeoutInMilliseconds = timeoutInMilliseconds;
    }

    private void addOption(String option, Expression expression) {
        if (this.options == null) {
            this.options = new ArrayList<>();
        }
        this.options.add(new SagaOptionDefinition(option, expression));
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
        setPropagation(propagation);
        return this;
    }

    public SagaDefinition sagaService(CamelSagaService sagaService) {
        setSagaService(sagaService);
        return this;
    }

    public SagaDefinition completionMode(SagaCompletionMode completionMode) {
        setCompletionMode(completionMode);
        return this;
    }

    public SagaDefinition option(String option, Expression expression) {
        addOption(option, expression);
        return this;
    }

    public SagaDefinition timeout(long timeout, TimeUnit unit) {
        setTimeoutInMilliseconds(unit.toMillis(timeout));
        return this;
    }

    // Utils

    protected CamelSagaService findSagaService(CamelContext context) {
        CamelSagaService sagaService = getSagaService();
        if (sagaService != null) {
            return sagaService;
        }

        sagaService = context.hasService(CamelSagaService.class);
        if (sagaService != null) {
            return sagaService;
        }

        sagaService = CamelContextHelper.findByType(context, CamelSagaService.class);
        if (sagaService != null) {
            return sagaService;
        }

        throw new RuntimeCamelException("Cannot find a CamelSagaService");
    }

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
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(key).append(':').append(value);
    }

}
