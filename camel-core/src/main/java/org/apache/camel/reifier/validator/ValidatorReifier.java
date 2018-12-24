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
package org.apache.camel.reifier.validator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.EndpointValidatorDefinition;
import org.apache.camel.model.validator.PredicateValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.Validator;

public abstract class ValidatorReifier<T> {

    private static final Map<Class<?>, Function<ValidatorDefinition, ValidatorReifier<? extends ValidatorDefinition>>> VALIDATORS;
    static {
        Map<Class<?>, Function<ValidatorDefinition, ValidatorReifier<? extends ValidatorDefinition>>> map = new HashMap<>();
        map.put(CustomValidatorDefinition.class, CustomValidatorReifier::new);
        map.put(EndpointValidatorDefinition.class, EndpointValidatorReifier::new);
        map.put(PredicateValidatorDefinition.class, PredicateValidatorReifier::new);
        VALIDATORS = map;
    }
    
    protected final T definition;

    ValidatorReifier(T definition) {
        this.definition = definition;
    }

    public static ValidatorReifier<? extends ValidatorDefinition> reifier(ValidatorDefinition definition) {
        Function<ValidatorDefinition, ValidatorReifier<? extends ValidatorDefinition>> reifier = VALIDATORS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
    }

    public Validator createValidator(CamelContext context) throws Exception {
        return doCreateValidator(context);
    };

    protected abstract Validator doCreateValidator(CamelContext context) throws Exception;

}
