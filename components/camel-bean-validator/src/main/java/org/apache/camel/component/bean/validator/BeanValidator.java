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

import java.util.Set;

import javax.validation.ConstraintValidatorFactory;
import javax.validation.ConstraintViolation;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Bean validator that uses the JSR 303 reference implementation (Hibernate Validator).
 * Throws {@link BeanValidationException} if constrain violations are detected.
 */
public class BeanValidator implements Processor {
    
    private ValidatorFactory validatorFactory;
    private Validator validator;
    private Class<?> group;
    
    public void process(Exchange exchange) throws Exception {
        Object bean = exchange.getIn().getBody();
        Set<ConstraintViolation<Object>> constraintViolations = null;
        
        if (this.group != null) {
            constraintViolations = validator.validate(bean, group);
        } else {
            constraintViolations = validator.validate(bean);
        }
        
        if (!constraintViolations.isEmpty()) {
            throw new BeanValidationException(exchange, constraintViolations, exchange.getIn().getBody());
        }
    }
    
    public ValidatorFactory getValidatorFactory() {
        return validatorFactory;
    }

    public void setValidatorFactory(ValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
        this.validator = this.validatorFactory.getValidator();
    }

    public Validator getValidator() {
        return validator;
    }
    
    public Class<?> getGroup() {
        return group;
    }
   
    public void setGroup(Class<?> group) {
        this.group = group;
    }

    public MessageInterpolator getMessageInterpolator() {
        return this.validatorFactory.getMessageInterpolator();
    }

    public TraversableResolver getTraversableResolver() {
        return this.validatorFactory.getTraversableResolver();
    }

    public ConstraintValidatorFactory getConstraintValidatorFactory() {
        return this.validatorFactory.getConstraintValidatorFactory();
    }
}