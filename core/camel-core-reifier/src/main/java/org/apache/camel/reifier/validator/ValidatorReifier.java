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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.camel.CamelContext;
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.EndpointValidatorDefinition;
import org.apache.camel.model.validator.PredicateValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.ReifierStrategy;
import org.apache.camel.spi.Validator;

public abstract class ValidatorReifier<T> extends AbstractReifier {

    // for custom reifiers
    private static final Map<Class<?>, BiFunction<CamelContext, ValidatorDefinition, ValidatorReifier<? extends ValidatorDefinition>>> VALIDATORS
            = new HashMap<>(0);

    protected final T definition;

    public ValidatorReifier(CamelContext camelContext, T definition) {
        super(camelContext);
        this.definition = definition;
    }

    public static void registerReifier(
            Class<?> processorClass,
            BiFunction<CamelContext, ValidatorDefinition, ValidatorReifier<? extends ValidatorDefinition>> creator) {
        if (VALIDATORS.isEmpty()) {
            ReifierStrategy.addReifierClearer(ValidatorReifier::clearReifiers);
        }
        VALIDATORS.put(processorClass, creator);
    }

    public static ValidatorReifier<? extends ValidatorDefinition> reifier(
            CamelContext camelContext, ValidatorDefinition definition) {

        ValidatorReifier<? extends ValidatorDefinition> answer = null;
        if (!VALIDATORS.isEmpty()) {
            // custom take precedence
            BiFunction<CamelContext, ValidatorDefinition, ValidatorReifier<? extends ValidatorDefinition>> reifier
                    = VALIDATORS.get(definition.getClass());
            if (reifier != null) {
                answer = reifier.apply(camelContext, definition);
            }
        }
        if (answer == null) {
            answer = coreReifier(camelContext, definition);
        }
        if (answer == null) {
            throw new IllegalStateException("Unsupported definition: " + definition);
        }
        return answer;
    }

    private static ValidatorReifier<? extends ValidatorDefinition> coreReifier(
            CamelContext camelContext, ValidatorDefinition definition) {
        if (definition instanceof CustomValidatorDefinition) {
            return new CustomValidatorReifier(camelContext, definition);
        } else if (definition instanceof EndpointValidatorDefinition) {
            return new EndpointValidatorReifier(camelContext, definition);
        } else if (definition instanceof PredicateValidatorDefinition) {
            return new PredicateValidatorReifier(camelContext, definition);
        }
        return null;
    }

    public static void clearReifiers() {
        VALIDATORS.clear();
    }

    public Validator createValidator() {
        return doCreateValidator();
    }

    protected abstract Validator doCreateValidator();

}
