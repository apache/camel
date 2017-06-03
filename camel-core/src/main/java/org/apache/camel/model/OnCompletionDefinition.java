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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.OnCompletionProcessor;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * Route to be executed when normal route processing completes
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "onCompletion")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnCompletionDefinition extends ProcessorDefinition<OnCompletionDefinition> implements ExecutorServiceAwareDefinition<OnCompletionDefinition> {
    @XmlAttribute @Metadata(defaultValue = "AfterConsumer")
    private OnCompletionMode mode;
    @XmlAttribute
    private Boolean onCompleteOnly;
    @XmlAttribute
    private Boolean onFailureOnly;
    @XmlElement(name = "onWhen") @AsPredicate
    private WhenDefinition onWhen;
    @XmlAttribute
    private Boolean parallelProcessing;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute(name = "useOriginalMessage")
    private Boolean useOriginalMessagePolicy;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();
    @XmlTransient
    private ExecutorService executorService;
    @XmlTransient
    private Boolean routeScoped;
    // TODO: in Camel 3.0 the OnCompletionDefinition should not contain state and OnCompletion processors
    @XmlTransient
    private final Map<String, Processor> onCompletions = new HashMap<String, Processor>();

    public OnCompletionDefinition() {
    }

    public boolean isRouteScoped() {
        // is context scoped by default
        return routeScoped != null ? routeScoped : false;
    }

    public Processor getOnCompletion(String routeId) {
        return onCompletions.get(routeId);
    }

    public Collection<Processor> getOnCompletions() {
        return onCompletions.values();
    }

    @Override
    public String toString() {
        return "onCompletion[" + getOutputs() + "]";
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
    public boolean isTopLevelOnly() {
        return true;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // assign whether this was a route scoped onCompletion or not
        // we need to know this later when setting the parent, as only route scoped should have parent
        // Note: this logic can possible be removed when the Camel routing engine decides at runtime
        // to apply onCompletion in a more dynamic fashion than current code base
        // and therefore is in a better position to decide among context/route scoped OnCompletion at runtime
        if (routeScoped == null) {
            routeScoped = super.getParent() != null;
        }

        boolean isOnCompleteOnly = getOnCompleteOnly() != null && getOnCompleteOnly();
        boolean isOnFailureOnly = getOnFailureOnly() != null && getOnFailureOnly();
        boolean isParallelProcessing = getParallelProcessing() != null && getParallelProcessing();
        boolean original = getUseOriginalMessagePolicy() != null && getUseOriginalMessagePolicy();

        if (isOnCompleteOnly && isOnFailureOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }
        if (original) {
            // ensure allow original is turned on
            routeContext.setAllowUseOriginalMessage(true);
        }

        String routeId = routeContext.getRoute().idOrCreate(routeContext.getCamelContext().getNodeIdFactory());

        Processor childProcessor = this.createChildProcessor(routeContext, true);

        // wrap the on completion route in a unit of work processor
        CamelInternalProcessor internal = new CamelInternalProcessor(childProcessor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));

        onCompletions.put(routeId, internal);

        Predicate when = null;
        if (onWhen != null) {
            when = onWhen.getExpression().createPredicate(routeContext);
        }

        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, isParallelProcessing);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "OnCompletion", this, isParallelProcessing);

        // should be after consumer by default
        boolean afterConsumer = mode == null || mode == OnCompletionMode.AfterConsumer;

        OnCompletionProcessor answer = new OnCompletionProcessor(routeContext.getCamelContext(), internal,
                threadPool, shutdownThreadPool, isOnCompleteOnly, isOnFailureOnly, when, original, afterConsumer);
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
    public void removeAllOnCompletionDefinition(ProcessorDefinition<?> definition) {
        for (Iterator<ProcessorDefinition<?>> it = definition.getOutputs().iterator(); it.hasNext();) {
            ProcessorDefinition<?> out = it.next();
            if (out instanceof OnCompletionDefinition) {
                it.remove();
            }
        }
    }

    @Override
    public ProcessorDefinition<?> end() {
        // pop parent block, as we added our self as block to parent when synchronized was defined in the route
        getParent().popBlock();
        return super.end();
    }

    /**
     * Sets the mode to be after route is done (default due backwards compatible).
     * <p/>
     * This executes the on completion work <i>after</i> the route consumer have written response
     * back to the callee (if its InOut mode).
     *
     * @return the builder
     */
    public OnCompletionDefinition modeAfterConsumer() {
        setMode(OnCompletionMode.AfterConsumer);
        return this;
    }

    /**
     * Sets the mode to be before consumer is done.
     * <p/>
     * This allows the on completion work to execute <i>before</i> the route consumer, writes any response
     * back to the callee (if its InOut mode).
     *
     * @return the builder
     */
    public OnCompletionDefinition modeBeforeConsumer() {
        setMode(OnCompletionMode.BeforeConsumer);
        return this;
    }

    /**
     * Will only synchronize when the {@link org.apache.camel.Exchange} completed successfully (no errors).
     *
     * @return the builder
     */
    public OnCompletionDefinition onCompleteOnly() {
        boolean isOnFailureOnly = getOnFailureOnly() != null && getOnFailureOnly();
        if (isOnFailureOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }
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
        boolean isOnCompleteOnly = getOnCompleteOnly() != null && getOnCompleteOnly();
        if (isOnCompleteOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }
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
    public OnCompletionDefinition onWhen(@AsPredicate Predicate predicate) {
        setOnWhen(new WhenDefinition(predicate));
        return this;
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

    /**
     * To use a custom Thread Pool to be used for parallel processing.
     * Notice if you set this option, then parallel processing is automatic implied, and you do not have to enable that option as well.
     */
    public OnCompletionDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing.
     * Notice if you set this option, then parallel processing is automatic implied, and you do not have to enable that option as well.
     */
    public OnCompletionDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * If enabled then the on completion process will run asynchronously by a separate thread from a thread pool.
     * By default this is false, meaning the on completion process will run synchronously using the same caller thread as from the route.
     *
     * @return the builder
     */
    public OnCompletionDefinition parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }

    /**
     * If enabled then the on completion process will run asynchronously by a separate thread from a thread pool.
     * By default this is false, meaning the on completion process will run synchronously using the same caller thread as from the route.
     *
     * @return the builder
     */
    public OnCompletionDefinition parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(parallelProcessing);
        return this;
    }

    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
    }

    public boolean isOutputSupported() {
        return true;
    }

    public OnCompletionMode getMode() {
        return mode;
    }

    /**
     * Sets the on completion mode.
     * <p/>
     * The default value is AfterConsumer
     */
    public void setMode(OnCompletionMode mode) {
        this.mode = mode;
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
        return useOriginalMessagePolicy;
    }

    /**
     * Will use the original input body when an {@link org.apache.camel.Exchange} for this on completion.
     * <p/>
     * By default this feature is off.
     */
    public void setUseOriginalMessagePolicy(Boolean useOriginalMessagePolicy) {
        this.useOriginalMessagePolicy = useOriginalMessagePolicy;
    }

    public Boolean getParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(Boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

}
