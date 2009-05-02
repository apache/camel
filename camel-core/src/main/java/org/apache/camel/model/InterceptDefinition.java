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

import java.util.List;
import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;intercept/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "intercept")
@XmlAccessorType(XmlAccessType.FIELD)
public class InterceptDefinition extends OutputDefinition<ProcessorDefinition> {

    // TODO: support stop later (its a bit hard as it needs to break entire processing of route)
    // TODO: add support for when predicate

    @XmlTransient
    protected Processor output;

    public InterceptDefinition() {
    }

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
    public Processor createProcessor(final RouteContext routeContext) throws Exception {
        // create the output processor
        output = createOutputsProcessor(routeContext);

        // add the output as a intercept strategy to the route context so its invoked on each processing step
        routeContext.getInterceptStrategies().add(new InterceptStrategy() {
            public Processor wrapProcessorInInterceptors(ProcessorDefinition processorDefinition, Processor target, Processor nextTarget) throws Exception {
                if (nextTarget != null) {
                    // wrap in a pipeline so we continue routing to the next
                    List<Processor> list = new ArrayList<Processor>(2);
                    list.add(output);
                    list.add(nextTarget);
                    return new Pipeline(list);
                } else {
                    return output;
                }
            }
        });

        // remove me from the route so I am not invoked in a regular route path
        routeContext.getRoute().getOutputs().remove(this);
        // and return no processor to invoke next from me
        return null;
    }

    /**
     * This method is <b>only</b> for handling some post configuration
     * that is needed from the Spring DSL side as JAXB does not invoke the fluent
     * builders, so we need to manually handle this afterwards, and since this is
     * an interceptor it has to do a bit of magic logic to fixup to handle predicates
     * with or without proceed/stop set as well.
     */
    public void afterPropertiesSet() {
        // TODO: is needed when we add support for when predicate
    }


}