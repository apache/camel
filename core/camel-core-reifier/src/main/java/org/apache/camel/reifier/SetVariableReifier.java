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

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.processor.SetVariableProcessor;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.ObjectHelper;

public class SetVariableReifier extends ExpressionReifier<SetVariableDefinition> {

    public SetVariableReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (SetVariableDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        ObjectHelper.notNull(definition.getName(), "variableName");
        Expression expr = createExpression(definition.getExpression());
        Expression nameExpr;
        String key = parseString(definition.getName());
        if (LanguageSupport.hasSimpleFunction(key)) {
            nameExpr = camelContext.resolveLanguage("simple").createExpression(key);
        } else {
            nameExpr = camelContext.resolveLanguage("constant").createExpression(key);
        }
        nameExpr.init(camelContext);
        return new SetVariableProcessor(nameExpr, expr);
    }
}
