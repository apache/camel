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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Intercept;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.processor.Interceptor;
import org.apache.camel.spi.RouteContext;


/**
 * Represents an XML &lt;intercept/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "intercept")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptType extends OutputType<ProcessorType> {
  
    @XmlTransient
    private ProceedType proceed = new ProceedType();
    @XmlTransient
    private Boolean stopIntercept = Boolean.FALSE;
    @XmlTransient
    private Boolean usePredicate = Boolean.FALSE;

    @Override
    public String toString() {
        return "Intercept[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "intercept";
    }

    @Override
    public String getLabel() {
        return "intercept";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Interceptor interceptor = new Interceptor();
        routeContext.intercept(interceptor);

        final Processor interceptRoute = createOutputsProcessor(routeContext);
        interceptor.setInterceptorLogic(interceptRoute);

        return interceptor;
    }

    /**
     * Applies this interceptor only if the given predicate is true
     */
    public ChoiceType when(Predicate predicate) {
        usePredicate = Boolean.TRUE;
        ChoiceType choice = choice().when(PredicateBuilder.not(predicate));
        choice.addOutput(proceed);
        return choice.otherwise();
    }

    public ProceedType getProceed() {
        return proceed;
    }

    public void stopIntercept() {
        setStopIntercept(Boolean.TRUE);
    }

    @XmlElement(name = "stop", required = false)
    public void setStop(String elementValue /* not used */) {
        stopIntercept();
    }    
    
    public InterceptType createProxy() {
        InterceptType answer = new InterceptType();
        answer.getOutputs().addAll(this.getOutputs());
        
        answer.setStopIntercept(getStopIntercept());

        // hack: now we need to replace the proceed of the proxy with its own
        // a bit ugly, operating based on the assumption that the proceed is
        // in its outputs (if proceed() was called) and/or in the
        // outputs of the otherwise or last when clause for the predicated version.
        if (answer.getOutputs().size() > 0) {
            // this is for the predicate version or if a choice() is present
            ChoiceType choice = null;
            for (ProcessorType processor : answer.getOutputs()) {
                if (processor instanceof ChoiceType) {
                    // special cases for predicates (choices)
                    choice = (ChoiceType) processor;

                    // for the predicated version we add the proceed() to otherwise()
                    // before knowing if stop() will follow, so let's make a small adjustment
                    if (usePredicate.booleanValue() && getStopIntercept().booleanValue()) {
                        WhenType when = choice.getWhenClauses().get(0);
                        when.getOutputs().remove(this.getProceed());
                    }

                    // add proceed to the when clause
                    addProceedProxy(this.getProceed(), answer.getProceed(),
                        choice.getWhenClauses().get(choice.getWhenClauses().size() - 1), usePredicate.booleanValue() && !getStopIntercept().booleanValue());

                    // force adding a proceed at the end (otherwise) if its not a stop type
                    addProceedProxy(this.getProceed(), answer.getProceed(), choice.getOtherwise(), !getStopIntercept().booleanValue());

                    if (getStopIntercept().booleanValue()) {
                        // must add proceed to when clause if stop is explictiy declared, otherwise when the
                        // predicate test fails then there is no proceed
                        // See example: InterceptorSimpleRouteTest (City Paris is never proceeded)  
                        addProceedProxy(this.getProceed(), answer.getProceed(),
                            choice.getWhenClauses().get(choice.getWhenClauses().size() - 1), usePredicate.booleanValue());
                    }

                    break;
                }
            }
            if (choice == null) {
                // force adding a proceed at the end if its not a stop type
                addProceedProxy(this.getProceed(), answer.getProceed(), answer, !getStopIntercept().booleanValue());
            }
        }

        return answer;
    }

    private void addProceedProxy(ProceedType orig, ProceedType proxy, ProcessorType<?> processor, boolean force) {
        int index = processor.getOutputs().indexOf(orig);
        if (index >= 0) {
            processor.addOutput(proxy);
            // replace original proceed with proxy
            List<ProcessorType<?>> outs = processor.getOutputs();
            outs.remove(proxy);
            outs.set(index, proxy);
        } else if (force) {
            processor.addOutput(proxy);
        }
    }

    public void setStopIntercept(Boolean stop) {
        this.stopIntercept = stop;
    }

    public Boolean getStopIntercept() {
        return stopIntercept;
    }

}
