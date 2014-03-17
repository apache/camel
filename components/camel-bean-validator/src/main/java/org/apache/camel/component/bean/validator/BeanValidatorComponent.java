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

import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.ValidationProviderResolver;
import javax.validation.ValidatorFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.util.PlatformHelper;

import static org.apache.camel.component.bean.validator.ValidatorFactories.buildValidatorFactory;

/**
 * Bean Validator Component for validating Java beans against reference implementation of JSR 303 Validator (Hibernate
 * Validator).
 */
public class BeanValidatorComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanValidator beanValidator = new BeanValidator();

        ValidatorFactory validatorFactory = buildValidatorFactory(
                isOsgiContext(),
                resolveAndRemoveReferenceParameter(parameters, "validationProviderResolver", ValidationProviderResolver.class),
                resolveAndRemoveReferenceParameter(parameters, "messageInterpolator", MessageInterpolator.class),
                resolveAndRemoveReferenceParameter(parameters, "traversableResolver", TraversableResolver.class),
                resolveAndRemoveReferenceParameter(parameters, "constraintValidatorFactory", ConstraintValidatorFactory.class));
        beanValidator.setValidatorFactory(validatorFactory);

        String group = getAndRemoveParameter(parameters, "group", String.class);
        if (group != null) {
            beanValidator.setGroup(getCamelContext().getClassResolver().resolveMandatoryClass(group));
        }

        return new ProcessorEndpoint(uri, this, beanValidator);
    }

    /**
     * Recognizes if component is executed in the OSGi environment.
     *
     * @return true if component is executed in the OSGi environment. False otherwise.
     */
    protected boolean isOsgiContext() {
        return PlatformHelper.isOsgiContext(getCamelContext());
    }

}