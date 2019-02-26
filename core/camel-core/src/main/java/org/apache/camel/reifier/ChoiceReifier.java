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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.spi.RouteContext;

class ChoiceReifier extends ProcessorReifier<ChoiceDefinition> {

    ChoiceReifier(ProcessorDefinition<?> definition) {
        super(ChoiceDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        List<FilterProcessor> filters = new ArrayList<>();
        for (WhenDefinition whenClause : definition.getWhenClauses()) {
            // also resolve properties and constant fields on embedded expressions in the when clauses
            ExpressionNode exp = whenClause;
            ExpressionDefinition expressionDefinition = exp.getExpression();
            if (expressionDefinition != null) {
                // resolve properties before we create the processor
                ProcessorDefinitionHelper.resolvePropertyPlaceholders(routeContext.getCamelContext(), expressionDefinition);

                // resolve constant fields (eg Exchange.FILE_NAME)
                ProcessorDefinitionHelper.resolveKnownConstantFields(expressionDefinition);
            }

            FilterProcessor filter = (FilterProcessor) createProcessor(routeContext, whenClause);
            filters.add(filter);
        }
        Processor otherwiseProcessor = null;
        if (definition.getOtherwise() != null) {
            otherwiseProcessor = createProcessor(routeContext, definition.getOtherwise());
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }

}
