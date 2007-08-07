/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.TryProcessor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "try")
@XmlAccessorType(XmlAccessType.FIELD)
public class TryType extends OutputType {
/*
    @XmlElement(required = false)
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
    @XmlElementRef
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();
*/
    @XmlTransient
    private List<CatchType> catchClauses;
    @XmlTransient
    private FinallyType finallyClause;
    @XmlTransient
    private boolean initialized;
    @XmlTransient
    private List<ProcessorType> outputsWithoutCatches;

    @Override
    public String toString() {
        return "Try[ " + getOutputs() + "]";
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
            for (CatchType catchClause : catchClauses) {
                catchProcessors.add(catchClause.createProcessor(routeContext));
            }
        }
        return new TryProcessor(tryProcessor, catchProcessors, finallyProcessor);
    }

    // Fluent API
    //-------------------------------------------------------------------------
    public CatchType handle(Class<?> exceptionType) {
        CatchType answer = new CatchType(exceptionType);
        getOutputs().add(answer);
        return answer;
    }

    public FinallyType handleAll() {
        FinallyType answer = new FinallyType();
        getOutputs().add(answer);
        return answer;
    }

    public TryType process(Processor processor) {
        super.process(processor);
        return this;
    }

    public TryType to(Endpoint endpoint) {
        super.to(endpoint);
        return this;
    }

    public TryType to(Collection<Endpoint> endpoints) {
        super.to(endpoints);
        return this;
    }

    public TryType to(Endpoint... endpoints) {
        super.to(endpoints);
        return this;
    }

    public TryType to(String uri) {
        super.to(uri);
        return this;
    }

    public TryType to(String... uris) {
        super.to(uris);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------

    public List<CatchType> getCatchClauses() {
        if (catchClauses == null) {
            checkInitialized();
        }
        return catchClauses;
    }

    public FinallyType getFinallyClause() {
        if (finallyClause == null) {
            checkInitialized();
        }
        return finallyClause;
    }

    public List<ProcessorType> getOutputsWithoutCatches() {
        if (outputsWithoutCatches == null) {
            checkInitialized();
        }
        return outputsWithoutCatches;
    }

    public void setOutputs(List<ProcessorType> outputs) {
        initialized = false;
        super.setOutputs(outputs);
    }


    public void addOutput(ProcessorType output) {
        initialized = false;
        getOutputs().add(output);
    }

    /**
     * Checks whether or not this object has been initialized
     */
    protected void checkInitialized() {
        if (!initialized) {
            initialized = true;
            outputsWithoutCatches = new ArrayList<ProcessorType>();
            catchClauses = new ArrayList<CatchType>();
            finallyClause = null;

            for (ProcessorType output : outputs) {
                if (output instanceof CatchType) {
                    catchClauses.add((CatchType) output);
                }
                else if (output instanceof FinallyType) {
                    if (finallyClause != null) {
                        throw new IllegalArgumentException("Multiple finally clauses added: " + finallyClause + " and " + output);
                    }
                    else {
                        finallyClause = (FinallyType) output;
                    }
                }
                else {
                    outputsWithoutCatches.add(output);
                }
            }
        }
    }
}