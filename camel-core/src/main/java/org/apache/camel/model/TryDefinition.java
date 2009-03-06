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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.TryProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;try/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "try")
@XmlAccessorType(XmlAccessType.FIELD)
public class TryDefinition extends OutputDefinition<TryDefinition> {
    @XmlTransient
    private List<CatchDefinition> catchClauses;
    @XmlTransient
    private FinallyDefinition finallyClause;
    @XmlTransient
    private boolean initialized;
    @XmlTransient
    private List<ProcessorDefinition> outputsWithoutCatches;

    @Override
    public String toString() {
        return "Try[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "try";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor tryProcessor = createOutputsProcessor(routeContext, getOutputsWithoutCatches());

        Processor finallyProcessor = null;
        if (finallyClause != null) {
            finallyProcessor = finallyClause.createProcessor(routeContext);
        }
        List<CatchProcessor> catchProcessors = new ArrayList<CatchProcessor>();
        if (catchClauses != null) {
            for (CatchDefinition catchClause : catchClauses) {
                catchProcessors.add(catchClause.createProcessor(routeContext));
            }
        }
        return new TryProcessor(tryProcessor, catchProcessors, finallyProcessor);
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Handles the given exception
     *
     * @param exceptionType  the exception
     * @return the try builder
     */
    public TryDefinition handle(Class<?> exceptionType) {
        popBlock();
        CatchDefinition answer = new CatchDefinition(exceptionType);
        addOutput(answer);
        pushBlock(answer);
        return this;
    }

    /**
     * The finally block for a given handle
     *
     * @return  the try builder
     */
    public TryDefinition finallyBlock() {
        popBlock();
        FinallyDefinition answer = new FinallyDefinition();
        addOutput(answer);
        pushBlock(answer);
        return this;
    }

    @Override
    public ProcessorDefinition<? extends ProcessorDefinition> end() {
        popBlock();
        return super.end();
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

    public List<ProcessorDefinition> getOutputsWithoutCatches() {
        if (outputsWithoutCatches == null) {
            checkInitialized();
        }
        return outputsWithoutCatches;
    }

    public void setOutputs(List<ProcessorDefinition> outputs) {
        initialized = false;
        super.setOutputs(outputs);
    }

    @Override
    public void addOutput(ProcessorDefinition output) {
        initialized = false;
        super.addOutput(output);
    }

    /**
     * Checks whether or not this object has been initialized
     */
    protected void checkInitialized() {
        if (!initialized) {
            initialized = true;
            outputsWithoutCatches = new ArrayList<ProcessorDefinition>();
            catchClauses = new ArrayList<CatchDefinition>();
            finallyClause = null;

            for (ProcessorDefinition output : outputs) {
                if (output instanceof CatchDefinition) {
                    catchClauses.add((CatchDefinition)output);
                } else if (output instanceof FinallyDefinition) {
                    if (finallyClause != null) {
                        throw new IllegalArgumentException("Multiple finally clauses added: " + finallyClause
                                                           + " and " + output);
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
