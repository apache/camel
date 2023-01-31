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
import java.util.Collection;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslProperty;

/**
 * Marks the beginning of a try, catch, finally block
 */
@Metadata(label = "error")
@XmlRootElement(name = "doTry")
@XmlAccessorType(XmlAccessType.FIELD)
public class TryDefinition extends OutputDefinition<TryDefinition> {
    @DslProperty
    @XmlTransient
    private List<CatchDefinition> catchClauses;
    @DslProperty
    @XmlTransient
    private FinallyDefinition finallyClause;
    @XmlTransient
    private boolean initialized;
    @XmlTransient
    private List<ProcessorDefinition<?>> outputsWithoutCatches;
    @XmlTransient
    private int endCounter; // used for detecting multiple nested doTry blocks

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
     * @param  exceptionType the exception
     * @return               the try builder
     */
    @SuppressWarnings("unchecked")
    public TryDefinition doCatch(Class<? extends Throwable> exceptionType) {
        // this method is introduced to avoid compiler warnings about the
        // generic Class arrays in the case we've got only one single Class
        // to build a TryDefinition for
        return doCatch(new Class[] { exceptionType });
    }

    /**
     * Handles the given exception(s)
     *
     * @param  exceptionType the exception(s)
     * @return               the try builder
     */
    @SafeVarargs
    public final TryDefinition doCatch(Class<? extends Throwable>... exceptionType) {
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
     * Sets an additional predicate that should be true before the onCatch is triggered.
     * <p/>
     * To be used for fine grained controlling whether a thrown exception should be intercepted by this exception type
     * or not.
     *
     * @param  predicate predicate that determines true or false
     * @return           the builder
     */
    public TryDefinition onWhen(@AsPredicate Predicate predicate) {
        // we must use a delegate so we can use the fluent builder based on
        // TryDefinition
        // to configure all with try .. catch .. finally
        // set the onWhen predicate on all the catch definitions
        Collection<CatchDefinition> col = ProcessorDefinitionHelper.filterTypeInOutputs(getOutputs(), CatchDefinition.class);
        for (CatchDefinition doCatch : col) {
            doCatch.setOnWhen(new WhenDefinition(predicate));
        }
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    @XmlTransient
    public void setCatchClauses(List<CatchDefinition> catchClauses) {
        this.catchClauses = catchClauses;
    }

    public List<CatchDefinition> getCatchClauses() {
        if (catchClauses == null) {
            checkInitialized();
        }
        return catchClauses;
    }

    @XmlTransient
    public void setFinallyClause(FinallyDefinition finallyClause) {
        this.finallyClause = finallyClause;
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
        // reset end counter as we are adding some outputs
        endCounter = 0;
        super.addOutput(output);
    }

    protected ProcessorDefinition<?> onEndDoTry() {
        if (endCounter > 0) {
            return end();
        } else {
            endCounter++;
        }
        return this;
    }

    @Override
    public void preCreateProcessor() {
        // force re-creating initialization to ensure its up-to-date (yaml-dsl creates this EIP specially via @DslProperty)
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
            if (catchClauses == null) {
                catchClauses = new ArrayList<>();
            }
            for (ProcessorDefinition<?> output : outputs) {
                if (output instanceof CatchDefinition) {
                    if (!catchClauses.contains(output)) {
                        catchClauses.add((CatchDefinition) output);
                    }
                } else if (output instanceof FinallyDefinition) {
                    if (finallyClause != null && output != finallyClause) {
                        throw new IllegalArgumentException(
                                "Multiple finally clauses added: " + finallyClause + " and " + output);
                    } else {
                        finallyClause = (FinallyDefinition) output;
                    }
                } else {
                    outputsWithoutCatches.add(output);
                }
            }
            // initialize parent
            for (CatchDefinition cd : catchClauses) {
                cd.setParent(this);
            }
            if (finallyClause != null) {
                finallyClause.setParent(this);
            }
        }
    }
}
