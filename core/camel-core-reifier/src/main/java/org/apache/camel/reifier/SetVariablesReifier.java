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

import java.util.List;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.model.SetVariablesDefinition;
import org.apache.camel.processor.SetVariablesProcessor;
import org.apache.camel.support.LanguageSupport;

public class SetVariablesReifier extends ProcessorReifier<SetVariablesDefinition> {

    public SetVariablesReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (SetVariablesDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        if (definition.getVariables().isEmpty()) {
            throw new IllegalArgumentException("There must be at least one variable specified");
        }
        List<Expression> nameExprs =
                new java.util.ArrayList<>(definition.getVariables().size());
        List<Expression> valueExprs =
                new java.util.ArrayList<>(definition.getVariables().size());
        for (SetVariableDefinition varDef : definition.getVariables()) {
            valueExprs.add(createExpression(varDef.getExpression()));
            Expression nameExpr;
            String key = parseString(varDef.getName());
            if (LanguageSupport.hasSimpleFunction(key)) {
                nameExpr = camelContext.resolveLanguage("simple").createExpression(key);
            } else {
                nameExpr = camelContext.resolveLanguage("constant").createExpression(key);
            }
            nameExpr.init(camelContext);
            nameExprs.add(nameExpr);
        }

        SetVariablesProcessor answer = new SetVariablesProcessor(nameExprs, valueExprs);
        answer.setDisabled(isDisabled(camelContext, definition));
        return answer;
    }
}
