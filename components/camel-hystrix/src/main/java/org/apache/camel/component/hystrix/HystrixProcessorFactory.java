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
package org.apache.camel.component.hystrix;

import org.apache.camel.Processor;
import org.apache.camel.model.HystrixDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;

/**
 * To integrate camel-hystrix with the Camel routes using the Hystrix EIP.
 */
public class HystrixProcessorFactory implements ProcessorFactory {

    @Override
    public Processor createChildProcessor(RouteContext routeContext, ProcessorDefinition<?> definition, boolean mandatory) throws Exception {
        // not in use
        return null;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext, ProcessorDefinition<?> definition) throws Exception {
        if (definition instanceof HystrixDefinition) {
            HystrixDefinition cb = (HystrixDefinition) definition;
            String id = cb.idOrCreate(routeContext.getCamelContext().getNodeIdFactory());

            // create the regular processor
            Processor processor = cb.createChildProcessor(routeContext, true);
            Processor fallback = null;
            if (cb.getFallback() != null) {
                fallback = cb.getFallback().createProcessor(routeContext);
            }

            return new HystrixProcessor(id, processor, fallback);
        } else {
            return null;
        }
    }
}
