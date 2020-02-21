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
package org.apache.camel.reifier;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.spi.InterceptStrategy;

public class InterceptReifier<T extends InterceptDefinition> extends ProcessorReifier<T> {

    public InterceptReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (T) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // create the output processor
        Processor output = this.createChildProcessor(true);

        // add the output as a intercept strategy to the route context so its
        // invoked on each processing step
        route.getInterceptStrategies().add(new InterceptStrategy() {
            private Processor interceptedTarget;

            public Processor wrapProcessorInInterceptors(CamelContext context, NamedNode definition, Processor target, Processor nextTarget) throws Exception {
                // store the target we are intercepting
                this.interceptedTarget = target;

                // remember the target that was intercepted
                InterceptReifier.this.definition.getIntercepted().add(interceptedTarget);

                if (interceptedTarget != null) {
                    // wrap in a pipeline so we continue routing to the next
                    return Pipeline.newInstance(context, output, interceptedTarget);
                } else {
                    return output;
                }
            }

            @Override
            public String toString() {
                return "intercept[" + (interceptedTarget != null ? interceptedTarget : output) + "]";
            }
        });

        // remove me from the route so I am not invoked in a regular route path
        ((RouteDefinition) route.getRoute()).getOutputs().remove(definition);
        // and return no processor to invoke next from me
        return null;
    }

}
