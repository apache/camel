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

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.Predicate;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;

/**
 * Route to be executed when normal route processing completes
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "onCompletion")
@XmlType(propOrder = {"onWhen", "outputs"})
@XmlAccessorType(XmlAccessType.FIELD)
public class OnCompletionDefinition extends OutputDefinition<OnCompletionDefinition> implements ExecutorServiceAwareDefinition<OnCompletionDefinition> {
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.model.OnCompletionMode", defaultValue = "AfterConsumer",
              enums = "AfterConsumer,BeforeConsumer")
    private String mode;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String onCompleteOnly;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String onFailureOnly;
    @XmlElement(name = "onWhen")
    @AsPredicate
    private WhenDefinition onWhen;
    @XmlAttribute
    private String parallelProcessing;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String executorServiceRef;
    @XmlAttribute(name = "useOriginalMessage")
    @Metadata(javaType = "java.lang.Boolean")
    private String useOriginalMessage;
    @XmlTransient
    private ExecutorService executorService;
    @XmlTransient
    private boolean routeScoped = true;

    public OnCompletionDefinition() {
    }

    public void setRouteScoped(boolean routeScoped) {
        this.routeScoped = routeScoped;
    }

    public boolean isRouteScoped() {
        return routeScoped;
    }

    @Override
    public void setParent(ProcessorDefinition<?> parent) {
        if (routeScoped) {
            super.setParent(parent);
        }
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
    public boolean isTopLevelOnly() {
        return true;
    }

    /**
     * Removes all existing
     * {@link org.apache.camel.model.OnCompletionDefinition} from the
     * definition.
     * <p/>
     * This is used to let route scoped <tt>onCompletion</tt> overrule any
     * global <tt>onCompletion</tt>. Hence we remove all existing as they are
     * global.
     *
     * @param definition the parent definition that is the route
     */
    public void removeAllOnCompletionDefinition(ProcessorDefinition<?> definition) {
        definition.getOutputs().removeIf(out -> out instanceof OnCompletionDefinition);
    }

    @Override
    public ProcessorDefinition<?> end() {
        // pop parent block, as we added our self as block to parent when
        // synchronized was defined in the route
        getParent().popBlock();
        return super.end();
    }

    /**
     * Sets the mode to be after route is done (default due backwards
     * compatible).
     * <p/>
     * This executes the on completion work <i>after</i> the route consumer have
     * written response back to the callee (if its InOut mode).
     *
     * @return the builder
     */
    public OnCompletionDefinition modeAfterConsumer() {
        setMode(OnCompletionMode.AfterConsumer.name());
        return this;
    }

    /**
     * Sets the mode to be before consumer is done.
     * <p/>
     * This allows the on completion work to execute <i>before</i> the route
     * consumer, writes any response back to the callee (if its InOut mode).
     *
     * @return the builder
     */
    public OnCompletionDefinition modeBeforeConsumer() {
        setMode(OnCompletionMode.BeforeConsumer.name());
        return this;
    }

    /**
     * Will only synchronize when the {@link org.apache.camel.Exchange}
     * completed successfully (no errors).
     *
     * @return the builder
     */
    public OnCompletionDefinition onCompleteOnly() {
        boolean isOnFailureOnly = Boolean.toString(true).equals(onFailureOnly);
        if (isOnFailureOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }
        // must define return type as OutputDefinition and not this type to
        // avoid end user being able
        // to invoke onFailureOnly/onCompleteOnly more than once
        setOnCompleteOnly(Boolean.toString(true));
        setOnFailureOnly(Boolean.toString(false));
        return this;
    }

    /**
     * Will only synchronize when the {@link org.apache.camel.Exchange} ended
     * with failure (exception or FAULT message).
     *
     * @return the builder
     */
    public OnCompletionDefinition onFailureOnly() {
        boolean isOnCompleteOnly = Boolean.toString(true).equals(onCompleteOnly);
        if (isOnCompleteOnly) {
            throw new IllegalArgumentException("Both onCompleteOnly and onFailureOnly cannot be true. Only one of them can be true. On node: " + this);
        }
        // must define return type as OutputDefinition and not this type to
        // avoid end user being able
        // to invoke onFailureOnly/onCompleteOnly more than once
        setOnCompleteOnly(Boolean.toString(false));
        setOnFailureOnly(Boolean.toString(true));
        return this;
    }

    /**
     * Sets an additional predicate that should be true before the onCompletion
     * is triggered.
     * <p/>
     * To be used for fine grained controlling whether a completion callback
     * should be invoked or not
     *
     * @param predicate predicate that determines true or false
     * @return the builder
     */
    public OnCompletionDefinition onWhen(@AsPredicate Predicate predicate) {
        setOnWhen(new WhenDefinition(predicate));
        return this;
    }

    /**
     * Will use the original input message body when an
     * {@link org.apache.camel.Exchange} for this on completion.
     * <p/>
     * By default this feature is off.
     *
     * @return the builder
     */
    public OnCompletionDefinition useOriginalBody() {
        setUseOriginalMessage(Boolean.toString(true));
        return this;
    }

    /**
     * To use a custom Thread Pool to be used for parallel processing. Notice if
     * you set this option, then parallel processing is automatic implied, and
     * you do not have to enable that option as well.
     */
    @Override
    public OnCompletionDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Refers to a custom Thread Pool to be used for parallel processing. Notice
     * if you set this option, then parallel processing is automatic implied,
     * and you do not have to enable that option as well.
     */
    @Override
    public OnCompletionDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * If enabled then the on completion process will run asynchronously by a
     * separate thread from a thread pool. By default this is false, meaning the
     * on completion process will run synchronously using the same caller thread
     * as from the route.
     *
     * @return the builder
     */
    public OnCompletionDefinition parallelProcessing() {
        setParallelProcessing(Boolean.toString(true));
        return this;
    }

    /**
     * If enabled then the on completion process will run asynchronously by a
     * separate thread from a thread pool. By default this is false, meaning the
     * on completion process will run synchronously using the same caller thread
     * as from the route.
     *
     * @return the builder
     */
    public OnCompletionDefinition parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(Boolean.toString(parallelProcessing));
        return this;
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

    public String getMode() {
        return mode;
    }

    /**
     * Sets the on completion mode.
     * <p/>
     * The default value is AfterConsumer
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOnCompleteOnly() {
        return onCompleteOnly;
    }

    public void setOnCompleteOnly(String onCompleteOnly) {
        this.onCompleteOnly = onCompleteOnly;
    }

    public String getOnFailureOnly() {
        return onFailureOnly;
    }

    public void setOnFailureOnly(String onFailureOnly) {
        this.onFailureOnly = onFailureOnly;
    }

    public WhenDefinition getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(WhenDefinition onWhen) {
        this.onWhen = onWhen;
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

    public String getUseOriginalMessage() {
        return useOriginalMessage;
    }

    /**
     * Will use the original input message body when an
     * {@link org.apache.camel.Exchange} for this on completion.
     * <p/>
     * By default this feature is off.
     */
    public void setUseOriginalMessage(String useOriginalMessage) {
        this.useOriginalMessage = useOriginalMessage;
    }

    public String getParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(String parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

}
