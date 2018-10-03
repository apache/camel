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
package org.apache.camel.impl;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.validator.ValidatorKey;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.ValidatorRegistry}.
 */
public class DefaultValidatorRegistry extends AbstractDynamicRegistry<ValidatorKey, Validator> implements ValidatorRegistry<ValidatorKey> {

    public DefaultValidatorRegistry(CamelContext context) throws Exception {
        super(context, CamelContextHelper.getMaximumValidatorCacheSize(context));
    }

    public DefaultValidatorRegistry(CamelContext context, List<ValidatorDefinition> definitions) throws Exception {
        this(context);
        for (ValidatorDefinition def : definitions) {
            Validator validator = def.createValidator(context);
            context.addService(validator);
            put(createKey(def), validator);
        }
    }

    public Validator resolveValidator(ValidatorKey key) {
        Validator answer = get(key);
        if (answer == null && ObjectHelper.isNotEmpty(key.getType().getName())) {
            answer = get(new ValidatorKey(new DataType(key.getType().getModel())));
        }
        return answer;
    }

    @Override
    public boolean isStatic(DataType type) {
        return isStatic(new ValidatorKey(type));
    }

    @Override
    public boolean isDynamic(DataType type) {
        return isDynamic(new ValidatorKey(type));
    }

    @Override
    public String toString() {
        return "ValidatorRegistry for " + context.getName() + ", capacity: " + maxCacheSize;
    }

    private ValidatorKey createKey(ValidatorDefinition def) {
        return new ValidatorKey(new DataType(def.getType()));
    }

}
