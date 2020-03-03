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
package org.apache.camel.reifier.validator;

import org.apache.camel.CamelContext;
import org.apache.camel.Predicate;
import org.apache.camel.impl.validator.ProcessorValidator;
import org.apache.camel.model.validator.PredicateValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.Validator;
import org.apache.camel.support.processor.PredicateValidatingProcessor;

public class PredicateValidatorReifier extends ValidatorReifier<PredicateValidatorDefinition> {

    public PredicateValidatorReifier(CamelContext camelContext, ValidatorDefinition definition) {
        super(camelContext, (PredicateValidatorDefinition)definition);
    }

    @Override
    protected Validator doCreateValidator() {
        Predicate pred = createPredicate(definition.getExpression());
        PredicateValidatingProcessor processor = new PredicateValidatingProcessor(pred);
        return new ProcessorValidator(camelContext).setProcessor(processor).setType(definition.getType());
    }

}
