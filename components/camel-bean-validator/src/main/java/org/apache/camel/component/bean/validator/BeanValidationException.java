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

import javax.validation.ConstraintViolation;

import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;

/**
 * A bean validation exception occurred
 */
public class BeanValidationException extends ValidationException {

    private static final long serialVersionUID = 5767438583860347105L;

    private final Set<ConstraintViolation<Object>> constraintViolations;

    public BeanValidationException(Exchange exchange, Set<ConstraintViolation<Object>> constraintViolations, Object bean) {
        super(exchange, buildMessage(constraintViolations, bean));
        this.constraintViolations = constraintViolations;
    }

    protected static String buildMessage(Set<ConstraintViolation<Object>> constraintViolations, Object bean) {
        StringBuilder buffer = new StringBuilder("Validation failed for: ");
        buffer.append(bean);

        buffer.append(" errors: [");
        for (ConstraintViolation<Object> constraintViolation : constraintViolations) {
            buffer.append("property: " + constraintViolation.getPropertyPath() + "; value: " + constraintViolation.getInvalidValue() + "; constraint: " + constraintViolation.getMessage() + "; ");
        }
        buffer.append("]");

        return buffer.toString();
    }
    
    public Set<ConstraintViolation<Object>> getConstraintViolations() {
        return constraintViolations;
    }
}