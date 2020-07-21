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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ExpressionFactory;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;

public class ChoiceReifier extends ProcessorReifier<ChoiceDefinition> {

    public ChoiceReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, ChoiceDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        List<FilterProcessor> filters = new ArrayList<>();
        for (WhenDefinition whenClause : definition.getWhenClauses()) {
            ExpressionDefinition exp = whenClause.getExpression();
            if (exp.getExpressionType() != null) {
                exp = exp.getExpressionType();
            }
            Predicate pre = exp.getPredicate();
            if (pre instanceof ExpressionClause) {
                ExpressionClause<?> clause = (ExpressionClause<?>)pre;
                if (clause.getExpressionType() != null) {
                    // if using the Java DSL then the expression may have been
                    // set using the
                    // ExpressionClause which is a fancy builder to define
                    // expressions and predicates
                    // using fluent builders in the DSL. However we need
                    // afterwards a callback to
                    // reset the expression to the expression type the
                    // ExpressionClause did build for us
                    ExpressionFactory model = clause.getExpressionType();
                    if (model instanceof ExpressionDefinition) {
                        whenClause.setExpression((ExpressionDefinition)model);
                    }
                }
                exp = whenClause.getExpression();
            }

            FilterProcessor filter = (FilterProcessor)createProcessor(whenClause);
            filters.add(filter);
        }
        Processor otherwiseProcessor = null;
        if (definition.getOtherwise() != null) {
            otherwiseProcessor = createProcessor(definition.getOtherwise());
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }

}
