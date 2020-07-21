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

import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.processor.FilterProcessor;

abstract class ExpressionReifier<T extends ExpressionNode> extends ProcessorReifier<T> {

    protected ExpressionReifier(Route route, T definition) {
        super(route, definition);
    }

    /**
     * Creates the {@link FilterProcessor} from the expression node.
     *
     * @return the created {@link FilterProcessor}
     * @throws Exception is thrown if error creating the processor
     */
    protected FilterProcessor createFilterProcessor() throws Exception {
        Processor childProcessor = createOutputsProcessor();
        return new FilterProcessor(camelContext, createPredicate(), childProcessor);
    }

    /**
     * Creates the {@link Predicate} from the expression node.
     *
     * @return the created predicate
     */
    protected Predicate createPredicate() {
        return createPredicate(definition.getExpression());
    }

}
