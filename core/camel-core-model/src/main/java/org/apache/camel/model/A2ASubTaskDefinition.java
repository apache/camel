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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Groups route steps and emits A2A progress events before, after, or when the grouped work fails.
 */
@Metadata(firstVersion = "4.21.0", label = "eip,routing,ai", title = "A2A Sub Task")
@XmlRootElement(name = "a2aSubTask")
@XmlAccessorType(XmlAccessType.FIELD)
public class A2ASubTaskDefinition extends OutputDefinition<A2ASubTaskDefinition> {

    @XmlAttribute
    @Metadata(description = "Simple expression template to emit before the nested steps run")
    private String emitBefore;

    @XmlAttribute
    @Metadata(description = "Simple expression template to emit after the nested steps complete successfully")
    private String emitAfter;

    @XmlAttribute
    @Metadata(description = "Simple expression template to emit when the nested steps fail")
    private String emitOnError;

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether to fail if the current Exchange does not have an active A2A task context")
    private String failIfNoTaskContext;

    public A2ASubTaskDefinition() {
    }

    protected A2ASubTaskDefinition(A2ASubTaskDefinition source) {
        super(source);
        this.emitBefore = source.emitBefore;
        this.emitAfter = source.emitAfter;
        this.emitOnError = source.emitOnError;
        this.failIfNoTaskContext = source.failIfNoTaskContext;
    }

    @Override
    public A2ASubTaskDefinition copyDefinition() {
        return new A2ASubTaskDefinition(this);
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
    public String getShortName() {
        return "a2aSubTask";
    }

    @Override
    public String getLabel() {
        return "a2aSubTask";
    }

    public String getEmitBefore() {
        return emitBefore;
    }

    public void setEmitBefore(String emitBefore) {
        this.emitBefore = emitBefore;
    }

    public String getEmitAfter() {
        return emitAfter;
    }

    public void setEmitAfter(String emitAfter) {
        this.emitAfter = emitAfter;
    }

    public String getEmitOnError() {
        return emitOnError;
    }

    public void setEmitOnError(String emitOnError) {
        this.emitOnError = emitOnError;
    }

    public String getFailIfNoTaskContext() {
        return failIfNoTaskContext;
    }

    public void setFailIfNoTaskContext(String failIfNoTaskContext) {
        this.failIfNoTaskContext = failIfNoTaskContext;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Simple expression template to emit before the nested steps run.
     */
    public A2ASubTaskDefinition emitBefore(String emitBefore) {
        setEmitBefore(emitBefore);
        return this;
    }

    /**
     * Simple expression template to emit after the nested steps complete successfully.
     */
    public A2ASubTaskDefinition emitAfter(String emitAfter) {
        setEmitAfter(emitAfter);
        return this;
    }

    /**
     * Simple expression template to emit when the nested steps fail.
     */
    public A2ASubTaskDefinition emitOnError(String emitOnError) {
        setEmitOnError(emitOnError);
        return this;
    }

    /**
     * Whether to fail if the current Exchange does not have an active A2A task context.
     */
    public A2ASubTaskDefinition failIfNoTaskContext(boolean failIfNoTaskContext) {
        setFailIfNoTaskContext(Boolean.toString(failIfNoTaskContext));
        return this;
    }

    /**
     * Whether to fail if the current Exchange does not have an active A2A task context.
     */
    public A2ASubTaskDefinition failIfNoTaskContext(String failIfNoTaskContext) {
        setFailIfNoTaskContext(failIfNoTaskContext);
        return this;
    }
}
