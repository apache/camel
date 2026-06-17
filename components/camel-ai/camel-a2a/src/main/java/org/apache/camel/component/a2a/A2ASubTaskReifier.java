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
package org.apache.camel.component.a2a;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.A2ASubTaskDefinition;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.util.ObjectHelper;

public class A2ASubTaskReifier extends ProcessorReifier<A2ASubTaskDefinition> {

    public A2ASubTaskReifier(Route route, A2ASubTaskDefinition definition) {
        super(route, definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Processor child = createChildProcessor(true);
        Expression emitBefore = createSimpleExpression(definition.getEmitBefore());
        Expression emitAfter = createSimpleExpression(definition.getEmitAfter());
        Expression emitOnError = createSimpleExpression(definition.getEmitOnError());
        boolean failIfNoTaskContext = parseBoolean(definition.getFailIfNoTaskContext(), false);

        A2ASubTaskProcessor answer = new A2ASubTaskProcessor(
                child,
                emitBefore,
                emitAfter,
                emitOnError,
                failIfNoTaskContext);
        answer.setDisabled(isDisabled(camelContext, definition));
        return answer;
    }

    private Expression createSimpleExpression(String template) {
        String text = parseString(template);
        if (ObjectHelper.isEmpty(text)) {
            return null;
        }
        Expression expression = camelContext.resolveLanguage("simple").createExpression(text);
        expression.init(camelContext);
        return expression;
    }
}
