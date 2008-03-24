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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.Interceptor;

/**
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

/*
    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        Interceptor interceptor = new Interceptor();
        routeContext.intercept(interceptor);

        final Processor interceptRoute = routeContext.createProcessor(this);
        interceptor.setInterceptorLogic(interceptRoute);
    }
*/

    /**
     * Applies this interceptor only if the given predicate is true
     */
    public ChoiceType when(Predicate predicate) {
        ChoiceType choice = choice().when(PredicateBuilder.not(predicate));
        choice.addOutput(proceed);
        return choice.otherwise();
        //return choice.proceed().otherwise();
    }

    public ProceedType getProceed() {
        return proceed;
    }
}