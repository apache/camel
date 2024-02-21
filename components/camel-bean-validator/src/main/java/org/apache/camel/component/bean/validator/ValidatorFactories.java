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
package org.apache.camel.component.bean.validator;

import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validation;
import jakarta.validation.ValidationProviderResolver;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.bootstrap.GenericBootstrap;

import org.apache.camel.CamelContext;
import org.apache.camel.support.CamelContextHelper;

/**
 * Utility class dedicated to create new {@code jakarta.validation.ValidatorFactory} instances.
 */
public final class ValidatorFactories {

    private ValidatorFactories() {
    }

    public static ValidatorFactory buildValidatorFactory(
            CamelContext camelContext, boolean ignoreXml,
            ValidationProviderResolver validationProviderResolver,
            MessageInterpolator messageInterpolator,
            TraversableResolver traversableResolver,
            ConstraintValidatorFactory constraintValidatorFactory) {

        if (validationProviderResolver == null) {
            ValidationProviderResolverFactory factory
                    = CamelContextHelper.findSingleByType(camelContext, ValidationProviderResolverFactory.class);
            if (factory != null) {
                validationProviderResolver = factory.createValidationProviderResolver(camelContext);
            }
        }
        GenericBootstrap bootstrap = Validation.byDefaultProvider();
        if (validationProviderResolver != null) {
            bootstrap.providerResolver(validationProviderResolver);
        }
        Configuration<?> configuration = bootstrap.configure();
        if (messageInterpolator != null) {
            configuration.messageInterpolator(messageInterpolator);
        }
        if (traversableResolver != null) {
            configuration.traversableResolver(traversableResolver);
        }
        if (constraintValidatorFactory != null) {
            configuration.constraintValidatorFactory(constraintValidatorFactory);
        }
        if (ignoreXml) {
            configuration.ignoreXmlConfiguration();
        }

        return configuration.buildValidatorFactory();
    }

}
