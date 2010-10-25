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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.processor.OnCompletionProcessor;
import org.apache.camel.processor.UnitOfWorkProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;onCompletion/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "onCompletion")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnCompletionDefinition extends ProcessorDefinition<OnCompletionDefinition> implements ExecutorServiceAwareDefinition<OnCompletionDefinition> {

    @XmlAttribute(required = false)
    private Boolean onCompleteOnly = Boolean.FALSE;
    @XmlAttribute(required = false)
    private Boolean onFailureOnly = Boolean.FALSE;
    @XmlElement(name = "onWhen", required = false)
    private WhenDefinition onWhen;
    @XmlElementRef
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(name = "useOriginalMessage", required = false)
    private Boolean useOriginalMessagePolicy;

    public OnCompletionDefinition() {
    }

    @Override
    public String toString() {
        return "onCompletion[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "onCompletion";
    }

    @Override
    public String getLabel() {
        return "onCompletion";
    }

    @Override
    public boolean isAbstract() {
        return true;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (onCompleteOnly && onFailureOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }

        Processor childProcessor = this.createChildProcessor(routeContext, true);

        // wrap the on completion route in a unit of work processor
        childProcessor = new UnitOfWorkProcessor(routeContext, childProcessor);

        Predicate when = null;
        if (onWhen != null) {
            when = onWhen.getExpression().createPredicate(routeContext);
        }

        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, "OnCompletion", this);
        if (executorService == null) {
            executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newDefaultThreadPool(this, "OnCompletion");
        }
        // should be false by default
        boolean original = getUseOriginalMessagePolicy() != null ? getUseOriginalMessagePolicy() : false;
        OnCompletionProcessor answer = new OnCompletionProcessor(routeContext.getCamelContext(), childProcessor,
                executorService, onCompleteOnly, onFailureOnly, when, original);
        return answer;
    }

    /**
     * Removes all existing {@link org.apache.camel.model.OnCompletionDefinition} from the definition.
     * <p/>
     * This is used to let route scoped <tt>onCompletion</tt> overrule any global <tt>onCompletion</tt>.
     * Hence we remove all existing as they are global.
     *
     * @param definition the parent definition that is the route
     */
    @SuppressWarnings("unchecked")
    public void removeAllOnCompletionDefinition(ProcessorDefinition definition) {
        for (Iterator<ProcessorDefinition> it = definition.getOutputs().iterator(); it.hasNext();) {
            ProcessorDefinition out = it.next();
            if (out instanceof OnCompletionDefinition) {
                it.remove();
            }
        }
    }

    @Override
    public ProcessorDefinition end() {
        // pop parent block, as we added our self as block to parent when synchronized was defined in the route
        getParent().popBlock();
        return super.end();
    }

    /**
     * Will only synchronize when the {@link org.apache.camel.Exchange} completed successfully (no errors).
     *
     * @return the builder
     */
    public OnCompletionDefinition onCompleteOnly() {
        // must define return type as OutputDefinition and not this type to avoid end user being able
        // to invoke onFailureOnly/onCompleteOnly more than once
        setOnCompleteOnly(Boolean.TRUE);
        setOnFailureOnly(Boolean.FALSE);
        return this;
    }

    /**
     * Will only synchronize when the {@link org.apache.camel.Exchange} ended with failure (exception or FAULT message).
     *
     * @return the builder
     */
    public OnCompletionDefinition onFailureOnly() {
        // must define return type as OutputDefinition and not this type to avoid end user being able
        // to invoke onFailureOnly/onCompleteOnly more than once
        setOnCompleteOnly(Boolean.FALSE);
        setOnFailureOnly(Boolean.TRUE);
        return this;
    }

    /**
     * Sets an additional predicate that should be true before the onCompletion is triggered.
     * <p/>
     * To be used for fine grained controlling whether a completion callback should be invoked or not
     *
     * @param predicate predicate that determines true or false
     * @return the builder
     */
    public OnCompletionDefinition onWhen(Predicate predicate) {
        setOnWhen(new WhenDefinition(predicate));
        return this;
    }

    /**
     * Creates an expression to configure an additional predicate that should be true before the
     * onCompletion is triggered.
     * <p/>
     * To be used for fine grained controlling whether a completion callback should be invoked or not
     *
     * @return the expression clause to configure
     */
    public ExpressionClause<OnCompletionDefinition> onWhen() {
        onWhen = new WhenDefinition();
        ExpressionClause<OnCompletionDefinition> clause = new ExpressionClause<OnCompletionDefinition>(this);
        onWhen.setExpression(clause);
        return clause;
    }

    /**
     * Will use the original input body when an {@link org.apache.camel.Exchange} for this on completion.
     * <p/>
     * By default this feature is off.
     *
     * @return the builder
     */
    public OnCompletionDefinition useOriginalBody() {
        setUseOriginalMessagePolicy(Boolean.TRUE);
        return this;
    }

    public OnCompletionDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    public OnCompletionDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    public List<ProcessorDefinition> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;
    }

    public Boolean getOnCompleteOnly() {
        return onCompleteOnly;
    }

    public void setOnCompleteOnly(Boolean onCompleteOnly) {
        this.onCompleteOnly = onCompleteOnly;
    }

    public Boolean getOnFailureOnly() {
        return onFailureOnly;
    }

    public void setOnFailureOnly(Boolean onFailureOnly) {
        this.onFailureOnly = onFailureOnly;
    }

    public WhenDefinition getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(WhenDefinition onWhen) {
        this.onWhen = onWhen;
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

    public Boolean getUseOriginalMessagePolicy() {
        return useOriginalMessagePolicy != null;
    }

    public void setUseOriginalMessagePolicy(Boolean useOriginalMessagePolicy) {
        this.useOriginalMessagePolicy = useOriginalMessagePolicy;
    }

}
