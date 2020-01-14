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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;

/**
 * Marks the beginning of a try, catch, finally block
 */
@Metadata(label = "error")
@XmlRootElement(name = "doTry")
@XmlAccessorType(XmlAccessType.FIELD)
public class TryDefinition extends OutputDefinition<TryDefinition> {
    @XmlTransient
    private List<CatchDefinition> catchClauses;
    @XmlTransient
    private FinallyDefinition finallyClause;
    @XmlTransient
    private boolean initialized;
    @XmlTransient
    private List<ProcessorDefinition<?>> outputsWithoutCatches;

    public TryDefinition() {
    }

    @Override
    public String toString() {
        return "DoTry[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "doTry";
    }

    @Override
    public String getLabel() {
        return "doTry";
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Handles the given exception
     *
     * @param exceptionType the exception
     * @return the try builder
     */
    @SuppressWarnings("unchecked")
    public TryDefinition doCatch(Class<? extends Throwable> exceptionType) {
        // this method is introduced to avoid compiler warnings about the
        // generic Class arrays in the case we've got only one single Class
        // to build a TryDefinition for
        return doCatch(new Class[] {exceptionType});
    }

    /**
     * Handles the given exception(s)
     *
     * @param exceptionType the exception(s)
     * @return the try builder
     */
    public TryDefinition doCatch(Class<? extends Throwable>... exceptionType) {
        popBlock();
        List<Class<? extends Throwable>> list = Arrays.asList(exceptionType);
        CatchDefinition answer = new CatchDefinition(list);
        addOutput(answer);
        pushBlock(answer);
        return this;
    }

    /**
     * The finally block for a given handle
     *
     * @return the try builder
     */
    public TryDefinition doFinally() {
        popBlock();
        FinallyDefinition answer = new FinallyDefinition();
        addOutput(answer);
        pushBlock(answer);
        return this;
    }

    /**
     * Sets an additional predicate that should be true before the onCatch is
     * triggered.
     * <p/>
     * To be used for fine grained controlling whether a thrown exception should
     * be intercepted by this exception type or not.
     *
     * @param predicate predicate that determines true or false
     * @return the builder
     */
    public TryDefinition onWhen(@AsPredicate Predicate predicate) {
        // we must use a delegate so we can use the fluent builder based on
        // TryDefinition
        // to configure all with try .. catch .. finally
        // set the onWhen predicate on all the catch definitions
        Iterator<CatchDefinition> it = ProcessorDefinitionHelper.filterTypeInOutputs(getOutputs(), CatchDefinition.class);
        while (it.hasNext()) {
            CatchDefinition doCatch = it.next();
            doCatch.setOnWhen(new WhenDefinition(predicate));
        }
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    public List<CatchDefinition> getCatchClauses() {
        if (catchClauses == null) {
            checkInitialized();
        }
        return catchClauses;
    }

    public FinallyDefinition getFinallyClause() {
        if (finallyClause == null) {
            checkInitialized();
        }
        return finallyClause;
    }

    public List<ProcessorDefinition<?>> getOutputsWithoutCatches() {
        if (outputsWithoutCatches == null) {
            checkInitialized();
        }
        return outputsWithoutCatches;
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return super.getOutputs();
    }

    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        initialized = false;
        super.setOutputs(outputs);
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        initialized = false;
        super.addOutput(output);
    }

    @Override
    public void preCreateProcessor() {
        // force re-creating initialization to ensure its up-to-date
        initialized = false;
        checkInitialized();
    }

    /**
     * Checks whether or not this object has been initialized
     */
    protected void checkInitialized() {
        if (!initialized) {
            initialized = true;
            outputsWithoutCatches = new ArrayList<>();
            catchClauses = new ArrayList<>();
            finallyClause = null;

            for (ProcessorDefinition<?> output : outputs) {
                if (output instanceof CatchDefinition) {
                    catchClauses.add((CatchDefinition)output);
                } else if (output instanceof FinallyDefinition) {
                    if (finallyClause != null) {
                        throw new IllegalArgumentException("Multiple finally clauses added: " + finallyClause + " and " + output);
                    } else {
                        finallyClause = (FinallyDefinition)output;
                    }
                } else {
                    outputsWithoutCatches.add(output);
                }
            }
        }
    }
}
