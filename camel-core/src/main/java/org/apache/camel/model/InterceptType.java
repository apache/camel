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

    @Override
    public String toString() {
        return "Intercept[" + getOutputs() + "]";
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
        ChoiceType choice = choice().when(PredicateBuilder.not(predicate));
        choice.addOutput(proceed);
        return choice.otherwise();
    }

    public ProceedType getProceed() {
        return proceed;
    }

    public InterceptType createProxy() {
        InterceptType answer = new InterceptType();
        answer.getOutputs().addAll(this.getOutputs());

        // hack: now we need to replace the proceed of the proxy with its own
        // a bit ugly, operating based on the assumption that the proceed is
        // in its outputs (if proceed() was called) and/or in the
        // outputs of the otherwise or last when clause for the predicated version.
        proxifyProceed(this.getProceed(), answer.getProceed(), answer);

        if (answer.getOutputs().size() > 0) {
            // this is for the predicate version
            ProcessorType processor = answer;
            processor = (ProcessorType) answer.getOutputs().get(0);
            if (processor instanceof ChoiceType) {
                ChoiceType choice = (ChoiceType) processor;
                proxifyProceed(this.getProceed(), answer.getProceed(),
                        choice.getWhenClauses().get(choice.getWhenClauses().size() - 1));
                proxifyProceed(this.getProceed(), answer.getProceed(), choice.getOtherwise());
            }
        }
        return answer;
    }

    private void proxifyProceed(ProceedType orig, ProceedType proxy, ProcessorType<?> processor) {
        int index = processor.getOutputs().indexOf(orig);
        if (index >= 0) {
            // replace original proceed with proxy
            processor.addOutput(proxy);

            List<ProcessorType<?>> outs = processor.getOutputs();
            outs.remove(proxy);
            outs.set(index, proxy);
        }
    }
}
