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

import java.util.Map;

import javax.validation.Configuration;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.Validation;
import javax.validation.ValidationProviderResolver;
import javax.validation.ValidatorFactory;
import javax.validation.bootstrap.GenericBootstrap;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;

/**
 * Bean Validator Component for validating Java beans against reference implementation of JSR 303 Validator (Hibernate
 * Validator).
 */
public class BeanValidatorComponent extends DefaultComponent {

    /**
     * Prefix of the OSGi-aware implementations of {@code org.apache.camel.CamelContext} interface (like
     * {@code org.apache.camel.core.osgi.OsgiDefaultCamelContext} or
     * {@code org.apache.camel.osgi.OsgiSpringCamelContext} ).
     */
    private static final String OSGI_CONTEXT_CLASS_PREFIX = "Osgi";

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanValidator beanValidator = new BeanValidator();

        ValidationProviderResolver validationProviderResolver = resolveValidationProviderResolver(parameters);
        MessageInterpolator messageInterpolator = resolveAndRemoveReferenceParameter(parameters, "messageInterpolator", MessageInterpolator.class);
        TraversableResolver traversableResolver = resolveAndRemoveReferenceParameter(parameters, "traversableResolver", TraversableResolver.class);
        ConstraintValidatorFactory constraintValidatorFactory = resolveAndRemoveReferenceParameter(parameters, "constraintValidatorFactory", ConstraintValidatorFactory.class);
        String group = getAndRemoveParameter(parameters, "group", String.class);
        
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
        
        ValidatorFactory validatorFactory = configuration.buildValidatorFactory();
        beanValidator.setValidatorFactory(validatorFactory);
        
        if (group != null) {
            beanValidator.setGroup(getCamelContext().getClassResolver().resolveMandatoryClass(group));
        }

        return new ProcessorEndpoint(uri, this, beanValidator);
    }

    /**
     * Resolves optional custom {@code javax.validation.ValidationProviderResolver} to be used by the component. By
     * default component tries to use resolver instance bound to the Camel registry under name
     * {@code validationProviderResolver} . If there is no such resolver instance in the registry and component is
     * running in the OSGi environment, {@link HibernateValidationProviderResolver} will be used. In all the other
     * cases this method will return null.
     *
     * @param parameters endpoint parameters
     * @return {@code javax.validation.ValidationProviderResolver} instance or null if no custom resolver should
     * be used by the component
     */
    protected ValidationProviderResolver resolveValidationProviderResolver(Map<String, Object> parameters) {
        ValidationProviderResolver validationProviderResolver = resolveAndRemoveReferenceParameter(parameters, "validationProviderResolver", ValidationProviderResolver.class);
        if (validationProviderResolver != null) {
            return validationProviderResolver;
        }
        if (isOsgiContext()) {
            return new HibernateValidationProviderResolver();
        }
        return null;
    }

    /**
     * Recognizes if component is executed in the OSGi environment. This implementation assumes that component is
     * deployed into OSGi environment if it uses implementation of {@code org.apache.camel.CamelContext} with class
     * name starting with the "Osgi" prefix.
     *
     * @return true if component is executed in the OSGi environment. False otherwise.
     */
    protected boolean isOsgiContext() {
        return getCamelContext().getClass().getSimpleName().startsWith(OSGI_CONTEXT_CLASS_PREFIX);
    }

}