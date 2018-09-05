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
package org.apache.camel.reifier;

import org.apache.camel.Processor;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.spi.RouteContext;

class FilterReifier extends ExpressionReifier<FilterDefinition> {

    FilterReifier(ProcessorDefinition<?> definition) {
        super(FilterDefinition.class.cast(definition));
    }

    @Override
    public FilterProcessor createProcessor(RouteContext routeContext) throws Exception {
        return createFilterProcessor(routeContext);
    }

    @Override
    protected FilterProcessor createFilterProcessor(RouteContext routeContext) throws Exception {
        // filter EIP should have child outputs
        Processor childProcessor = this.createChildProcessor(routeContext, true);
        return new FilterProcessor(createPredicate(routeContext), childProcessor);
    }


}
