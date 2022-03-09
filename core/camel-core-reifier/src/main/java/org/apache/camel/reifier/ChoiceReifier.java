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

import org.apache.camel.Exchange;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.spi.ExpressionFactoryAware;
import org.apache.camel.support.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChoiceReifier extends ProcessorReifier<ChoiceDefinition> {

    /**
     * The logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ChoiceReifier.class);

    public ChoiceReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, ChoiceDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        final boolean isPrecondition = Boolean.TRUE == parseBoolean(definition.getPrecondition());
        final List<FilterProcessor> filters = isPrecondition ? null : new ArrayList<>();
        for (WhenDefinition whenClause : definition.getWhenClauses()) {
            initBranch(whenClause);
            if (filters != null) {
                filters.add((FilterProcessor) createProcessor(whenClause));
            }
        }
        if (isPrecondition) {
            return getMatchingBranchProcessor();
        }
        Processor otherwiseProcessor = null;
        if (definition.getOtherwise() != null) {
            otherwiseProcessor = createProcessor(definition.getOtherwise());
        }
        return new ChoiceProcessor(filters, otherwiseProcessor);
    }

    /**
     * Initialize the given branch if needed.
     */
    private void initBranch(WhenDefinition whenClause) {
        ExpressionDefinition exp = whenClause.getExpression();
        if (exp.getExpressionType() != null) {
            exp = exp.getExpressionType();
        }
        Predicate pre = exp.getPredicate();
        if (pre instanceof ExpressionFactoryAware) {
            ExpressionFactoryAware aware = (ExpressionFactoryAware) pre;
            if (aware.getExpressionFactory() != null) {
                // if using the Java DSL then the expression may have been
                // set using the
                // ExpressionClause (implements ExpressionFactoryAware)
                // which is a fancy builder to define
                // expressions and predicates
                // using fluent builders in the DSL. However we need
                // afterwards a callback to
                // reset the expression to the expression type the
                // ExpressionClause did build for us
                ExpressionFactory model = aware.getExpressionFactory();
                if (model instanceof ExpressionDefinition) {
                    whenClause.setExpression((ExpressionDefinition) model);
                }
            }
        }
    }

    /**
     * @return the processor corresponding to the matching branch if any, {@code null} otherwise.
     */
    private Processor getMatchingBranchProcessor() throws Exception {
        // evaluate when predicates to optimize
        Exchange dummy = new DefaultExchange(camelContext);
        for (WhenDefinition whenClause : definition.getWhenClauses()) {
            ExpressionDefinition exp = whenClause.getExpression();
            exp.initPredicate(camelContext);

            Predicate predicate = exp.getPredicate();
            predicate.initPredicate(camelContext);

            boolean matches = predicate.matches(dummy);
            if (matches) {
                LOG.debug("doSwitch selected: {}", whenClause.getLabel());
                return createOutputsProcessor(whenClause.getOutputs());
            }
        }

        if (definition.getOtherwise() != null) {
            LOG.debug("doSwitch selected: otherwise");
            return createProcessor(definition.getOtherwise());
        }

        // no cases were selected
        LOG.debug("doSwitch no when or otherwise selected");
        return null;
    }

}
