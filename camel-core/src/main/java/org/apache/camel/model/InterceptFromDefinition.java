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
import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;interceptFrom/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "interceptFrom")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptFromDefinition extends OutputDefinition<ProcessorDefinition> {

    // TODO: we need a new interceptDefinition to uses the InterceptStrategy so its applied for each route step
    // and a more intelligent Channel so we can stop or proceed on-the-fly

    // TODO: Filter by from endpoint uri
    // TODO: Support lookup endpoint by ref (requires a bit more work)
    // TODO: Support wildcards for endpoints so you can match by scheme, eg jms:*

    @XmlAttribute(required = false)
    private String uri;
    @XmlTransient
    protected ProceedDefinition proceed = new ProceedDefinition();
    @XmlTransient
    protected Boolean stopIntercept = Boolean.FALSE;
    @XmlTransient
    protected Boolean usePredicate = Boolean.FALSE;

    public InterceptFromDefinition() {
    }

    public InterceptFromDefinition(String uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return "InterceptFrom[" + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "interceptFrom";
    }

    @Override
    public String getLabel() {
        return "interceptFrom";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return createOutputsProcessor(routeContext);
    }

    /**
     * Applies this interceptor only if the given predicate is true
     *
     * @param predicate  the predicate
     * @return the builder
     */
    public ChoiceDefinition when(Predicate predicate) {
        usePredicate = Boolean.TRUE;
        ChoiceDefinition choice = choice().when(PredicateBuilder.not(predicate));
        choice.addOutput(proceed);
        return choice.otherwise();
    }

    public ProceedDefinition getProceed() {
        return proceed;
    }

    public void stopIntercept() {
        setStopIntercept(Boolean.TRUE);
    }

    /**
     * This method is <b>only</b> for handling some post configuration
     * that is needed from the Spring DSL side as JAXB does not invoke the fluent
     * builders, so we need to manually handle this afterwards, and since this is
     * an interceptor it has to do a bit of magic logic to fixup to handle predicates
     * with or without proceed/stop set as well.
     */
    public void afterPropertiesSet() {
        List<ProcessorDefinition> list = new ArrayList<ProcessorDefinition>();
        for (ProcessorDefinition out : outputs) {
            if (out instanceof WhenDefinition) {
                // JAXB does not invoke the when() fluent builder so we need to wrap the when in
                // a choice with the proceed as the when for the Java DSL does
                WhenDefinition when = (WhenDefinition) out;
                usePredicate = Boolean.TRUE;
                ChoiceDefinition choice = new ChoiceDefinition();
                choice.when(PredicateBuilder.not(when.getExpression()));
                choice.addOutput(proceed);
                list.add(choice);

                ChoiceDefinition otherwise = choice.otherwise();
                // add the children to the otherwise
                for (ProcessorDefinition child : when.getOutputs()) {
                    if (child instanceof StopDefinition) {
                        // notify we should stop
                        stopIntercept();
                    } else {
                        otherwise.addOutput(child);
                    }
                }
            } else if (out instanceof StopDefinition) {
                // notify we shuld stop
                stopIntercept();
            } else {
                list.add(out);
            }
        }

        // replace old output with this redone output list
        outputs.clear();
        for (ProcessorDefinition out : list) {
            addOutput(out);
        }
    }

    // TODO: reduce complexity of this code

    public InterceptFromDefinition createProxy() {
        InterceptFromDefinition answer = new InterceptFromDefinition();
        answer.getOutputs().addAll(this.getOutputs());
        
        answer.setStopIntercept(getStopIntercept());

        // hack: now we need to replace the proceed of the proxy with its own
        // a bit ugly, operating based on the assumption that the proceed is
        // in its outputs (if proceed() was called) and/or in the
        // outputs of the otherwise or last when clause for the predicated version.
        if (answer.getOutputs().size() > 0) {
            // this is for the predicate version or if a choice() is present
            ChoiceDefinition choice = null;
            for (ProcessorDefinition processor : answer.getOutputs()) {
                if (processor instanceof ChoiceDefinition) {
                    // special cases for predicates (choices)
                    choice = (ChoiceDefinition) processor;

                    // for the predicated version we add the proceed() to otherwise()
                    // before knowing if stop() will follow, so let's make a small adjustment
                    if (usePredicate && getStopIntercept()) {
                        WhenDefinition when = choice.getWhenClauses().get(0);
                        when.getOutputs().remove(this.getProceed());
                    }

                    // add proceed to the when clause
                    addProceedProxy(this.getProceed(), answer.getProceed(),
                        choice.getWhenClauses().get(choice.getWhenClauses().size() - 1), usePredicate && !getStopIntercept());

                    // force adding a proceed at the end (otherwise) if its not a stop type
                    addProceedProxy(this.getProceed(), answer.getProceed(), choice.getOtherwise(), !getStopIntercept());

                    if (getStopIntercept()) {
                        // must add proceed to when clause if stop is explictiy declared, otherwise when the
                        // predicate test fails then there is no proceed
                        // See example: InterceptFromSimpleRouteTest (City Paris is never proceeded)
                        addProceedProxy(this.getProceed(), answer.getProceed(),
                            choice.getWhenClauses().get(choice.getWhenClauses().size() - 1), usePredicate);
                    }

                    break;
                }
            }
            if (choice == null) {
                // force adding a proceed at the end if its not a stop type
                addProceedProxy(this.getProceed(), answer.getProceed(), answer, !getStopIntercept());
            }
        }

        return answer;
    }

    private void addProceedProxy(ProceedDefinition orig, ProceedDefinition proxy, ProcessorDefinition<?> processor, boolean force) {
        int index = processor.getOutputs().indexOf(orig);
        if (index >= 0) {
            processor.addOutput(proxy);
            // replace original proceed with proxy
            List<ProcessorDefinition> outs = processor.getOutputs();
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

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
