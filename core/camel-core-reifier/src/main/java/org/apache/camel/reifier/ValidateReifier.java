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
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ValidateDefinition;
import org.apache.camel.support.processor.PredicateValidatingProcessor;

public class ValidateReifier extends ExpressionReifier<ValidateDefinition> {

    public ValidateReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (ValidateDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Predicate pred = createPredicate(definition.getExpression());
        return new PredicateValidatingProcessor(pred);
    }

}
