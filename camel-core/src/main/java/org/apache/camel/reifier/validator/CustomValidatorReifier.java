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

import org.apache.camel.CamelContext;
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.Validator;

class CustomValidatorReifier extends ValidatorReifier<CustomValidatorDefinition> {

    CustomValidatorReifier(ValidatorDefinition definition) {
        super((CustomValidatorDefinition) definition);
    }

    @Override
    protected Validator doCreateValidator(CamelContext context) throws Exception {
        if (definition.getRef() == null && definition.getClassName() == null) {
            throw new IllegalArgumentException("'ref' or 'type' must be specified for customValidator");
        }
        Validator validator;
        if (definition.getRef() != null) {
            validator = context.getRegistry().lookupByNameAndType(definition.getRef(), Validator.class);
            if (validator == null) {
                throw new IllegalArgumentException("Cannot find validator with ref:" + definition.getRef());
            }
            if (validator.getType() != null) {
                throw new IllegalArgumentException(String.format("Validator '%s' is already in use. Please check if duplicate validator exists.", definition.getRef()));
            }
        } else {
            Class<Validator> validatorClass = context.getClassResolver().resolveMandatoryClass(definition.getClassName(), Validator.class);
            if (validatorClass == null) {
                throw new IllegalArgumentException("Cannot find validator class: " + definition.getClassName());
            }
            validator = context.getInjector().newInstance(validatorClass);

        }
        validator.setCamelContext(context);
        return validator.setType(definition.getType());
    }

}
