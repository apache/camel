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
import javax.validation.ValidatorFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;

/**
 * Bean Validator Component for validating java beans against JSR 303 Validator
 *
 * @version 
 */
public class BeanValidatorComponent extends DefaultComponent {
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanValidator beanValidator = new BeanValidator();
        
        MessageInterpolator messageInterpolator = resolveAndRemoveReferenceParameter(parameters, "messageInterpolator", MessageInterpolator.class);
        TraversableResolver traversableResolver = resolveAndRemoveReferenceParameter(parameters, "traversableResolver", TraversableResolver.class);
        ConstraintValidatorFactory constraintValidatorFactory = resolveAndRemoveReferenceParameter(parameters, "constraintValidatorFactory", ConstraintValidatorFactory.class);
        String group = getAndRemoveParameter(parameters, "group", String.class);
        
        Configuration configuration = Validation.byDefaultProvider().configure();
        
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
}