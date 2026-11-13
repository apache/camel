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
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslProperty;

/**
 * Wraps route steps in a try-catch-finally block for fine-grained exception handling within the route
 */
@Metadata(label = "eip,error,errorhandling,routing",
          description = "Wraps route steps in a try-catch-finally block for fine-grained exception handling within the route")
@XmlRootElement(name = "doTry")
@XmlAccessorType(XmlAccessType.FIELD)
@BodyTypes(eipOut = @BodyType(from = "steps",
                              description = "What its steps end with, or what the doCatch that handled the exception ends"
                                            + " with"),
           eipSteps = @BodyType(Carrier.UNCHANGED))
public class TryDefinition extends OutputDefinition<TryDefinition> {

    // the doCatch and doFinally clauses are kept in outputs (the single source of truth, CAMEL-25410): these fields
    // are only the YAML DSL properties (doCatch and doFinally written as properties of doTry), and a cache derived
    // from outputs by checkInitialized
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

    protected TryDefinition(TryDefinition source) {
        // the clauses are in the outputs, which super copies: the cache fields are derived again from the copied outputs
        // (copying them too would give the copy two instances of each clause)
        super(source);
    }

    @Override
    public TryDefinition copyDefinition() {
        return new TryDefinition(this);
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
        // set the onWhen predicate on the last doCatch (the one being configured) of this doTry
        List<ProcessorDefinition<?>> list = getOutputs();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i) instanceof CatchDefinition doCatch) {
                doCatch.setOnWhen(new OnWhenDefinition(predicate));
                break;
            }
        }
        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Adds the doCatch clauses to the outputs (before the doFinally, if any), where the clauses of a doTry are kept.
     * Used by the YAML DSL for doCatch written as a property of doTry.
     */
    @XmlTransient
    public void setCatchClauses(List<CatchDefinition> catchClauses) {
        if (catchClauses != null) {
            for (CatchDefinition catchClause : catchClauses) {
                if (!outputs.contains(catchClause)) {
                    catchClause.setParent(this);
                    configureChild(catchClause);
                    outputs.add(indexOfFinally(), catchClause);
                }
            }
        }
        initialized = false;
    }

    /**
     * The doCatch clauses, in the order of the outputs where they are kept.
     */
    public List<CatchDefinition> getCatchClauses() {
        refresh();
        return catchClauses;
    }

    /**
     * Sets the doFinally clause in the outputs, where the clauses of a doTry are kept, replacing any other. Used by the
     * YAML DSL for doFinally written as a property of doTry.
     */
    @XmlTransient
    public void setFinallyClause(FinallyDefinition finallyClause) {
        outputs.removeIf(o -> o instanceof FinallyDefinition && o != finallyClause);
        if (finallyClause != null && !outputs.contains(finallyClause)) {
            finallyClause.setParent(this);
            configureChild(finallyClause);
            outputs.add(finallyClause);
        }
        initialized = false;
    }

    /**
     * The doFinally clause from the outputs, or null if there is none.
     */
    public FinallyDefinition getFinallyClause() {
        refresh();
        return finallyClause;
    }

    /**
     * The outputs that are the steps of the doTry block: all outputs except the doCatch and doFinally clauses.
     */
    public List<ProcessorDefinition<?>> getOutputsWithoutCatches() {
        refresh();
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
        // a step added to this doTry itself (not to the doCatch or doFinally the Java DSL is building, which takes it
        // as its own output) after a clause, such as YAML steps after doCatch written as a property, goes before the
        // clauses: the runtime takes the steps from anywhere in the outputs, and the outputs then read (and are dumped)
        // as steps, doCatch and doFinally in this order
        if (!(output instanceof CatchDefinition) && !(output instanceof FinallyDefinition)
                && !outputs.isEmpty() && outputs.get(outputs.size() - 1) == output) {
            int index = indexOfFirstClause();
            if (index < outputs.size() - 1) {
                outputs.remove(outputs.size() - 1);
                outputs.add(index, output);
            }
        }
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
     * Derives the clauses and the steps from the outputs again: the outputs can be changed directly (for example by
     * advice-with), so a clause that is no longer in the outputs must not stay in the cache.
     */
    private void refresh() {
        initialized = false;
        checkInitialized();
    }

    private int indexOfFinally() {
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i) instanceof FinallyDefinition) {
                return i;
            }
        }
        return outputs.size();
    }

    private int indexOfFirstClause() {
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i) instanceof CatchDefinition || outputs.get(i) instanceof FinallyDefinition) {
                return i;
            }
        }
        return outputs.size();
    }

    /**
     * Checks whether or not this object has been initialized, and derives the doCatch and doFinally clauses and the
     * steps from the outputs if not.
     */
    protected void checkInitialized() {
        if (!initialized) {
            initialized = true;
            List<ProcessorDefinition<?>> steps = new ArrayList<>();
            List<CatchDefinition> catches = new ArrayList<>();
            FinallyDefinition doFinally = null;
            int doFinallyCounter = 0;
            for (ProcessorDefinition<?> output : outputs) {
                if (output instanceof CatchDefinition catchDefinition) {
                    catches.add(catchDefinition);
                } else if (output instanceof FinallyDefinition finallyDefinition) {
                    ++doFinallyCounter;
                    doFinally = finallyDefinition;
                } else {
                    steps.add(output);
                }
            }
            outputsWithoutCatches = steps;
            catchClauses = catches;
            finallyClause = doFinally;
            if (doFinallyCounter > 1) {
                throw new IllegalArgumentException(
                        "Multiple finally clauses added: " + doFinallyCounter);
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
