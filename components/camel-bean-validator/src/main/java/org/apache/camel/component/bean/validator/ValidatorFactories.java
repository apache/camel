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
package org.apache.camel.component.bean.validator;

import javax.validation.Configuration;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.Validation;
import javax.validation.ValidationProviderResolver;
import javax.validation.ValidatorFactory;
import javax.validation.bootstrap.GenericBootstrap;

/**
 * Utility class dedicated to create new {@code javax.validation.ValidatorFactory} instances.
 */
public final class ValidatorFactories {

    private ValidatorFactories() {
    }

    public static ValidatorFactory buildValidatorFactory(boolean osgi,
                                                         ValidationProviderResolver validationProviderResolver,
                                                         MessageInterpolator messageInterpolator,
                                                         TraversableResolver traversableResolver,
                                                         ConstraintValidatorFactory constraintValidatorFactory) {

        ValidationProviderResolver resolvedValidationProviderResolver =
                resolveValidationProviderResolver(osgi, validationProviderResolver);

        GenericBootstrap bootstrap = Validation.byDefaultProvider();
        if (resolvedValidationProviderResolver != null) {
            bootstrap.providerResolver(resolvedValidationProviderResolver);
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

        return configuration.buildValidatorFactory();
    }

    /**
     * Resolves optional custom {@code javax.validation.ValidationProviderResolver} to be used by the component. By
     * default component tries to use resolver instance bound to the Camel registry under name
     * {@code validationProviderResolver} . If there is no such resolver instance in the registry and component is
     * running in the OSGi environment, {@link HibernateValidationProviderResolver} will be used. In all the other
     * cases this method will return null.
     *
     * @param osgi specifies if validator factory should be OSGi-aware
     * @param validationProviderResolver predefined provider resolver. This parameter overrides the results of the
     *                                   resolution.
     * @return {@code javax.validation.ValidationProviderResolver} instance or null if no custom resolver should
     * be used by the component
     */
    private static ValidationProviderResolver resolveValidationProviderResolver(
            boolean osgi,
            ValidationProviderResolver validationProviderResolver) {
        if (validationProviderResolver != null) {
            return validationProviderResolver;
        }
        if (osgi) {
            return new HibernateValidationProviderResolver();
        }
        return null;
    }

}
