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
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.Validator;

public class CustomValidatorReifier extends ValidatorReifier<CustomValidatorDefinition> {

    public CustomValidatorReifier(CamelContext camelContext, ValidatorDefinition definition) {
        super(camelContext, (CustomValidatorDefinition)definition);
    }

    @Override
    protected Validator doCreateValidator() {
        if (definition.getRef() == null && definition.getClassName() == null) {
            throw new IllegalArgumentException("'ref' or 'type' must be specified for customValidator");
        }
        Validator validator;
        if (definition.getRef() != null) {
            validator = lookup(definition.getRef(), Validator.class);
            if (validator == null) {
                throw new IllegalArgumentException("Cannot find validator with ref:" + definition.getRef());
            }
            if (validator.getType() != null) {
                throw new IllegalArgumentException(String.format("Validator '%s' is already in use. Please check if duplicate validator exists.", definition.getRef()));
            }
        } else {
            Class<Validator> validatorClass = camelContext.getClassResolver().resolveClass(definition.getClassName(), Validator.class);
            if (validatorClass == null) {
                throw new IllegalArgumentException("Cannot find validator class: " + definition.getClassName());
            }
            validator = camelContext.getInjector().newInstance(validatorClass, false);
        }
        validator.setCamelContext(camelContext);
        return validator.setType(definition.getType());
    }

}
