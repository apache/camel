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

import java.util.Locale;
import java.util.Set;

import javax.validation.ConstraintViolation;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.condition.OS.AIX;

public class BeanValidatorRouteTest extends CamelTestSupport {
    private Locale origLocale;

    @BeforeEach
    public void setLanguage() {
        origLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterEach
    public void restoreLanguage() {
        Locale.setDefault(origLocale);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldSuccessWithImpliciteDefaultGroup() {

        Exchange exchange = template.request("bean-validator://x", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(createCar("BMW", "DD-AB-123"));
            }
        });

        assertNotNull(exchange);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldSuccessWithExpliciteDefaultGroup() {

        Exchange exchange = template.request("bean-validator://x?group=javax.validation.groups.Default", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(createCar("BMW", "DD-AB-123"));
            }
        });

        assertNotNull(exchange);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldFailWithImpliciteDefaultGroup() {

        final String url = "bean-validator://x";
        final Car car = createCar("BMW", null);

        try {
            template.requestBody(url, car);
            fail("should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(BeanValidationException.class, e.getCause());

            BeanValidationException exception = (BeanValidationException) e.getCause();
            Set<ConstraintViolation<Object>> constraintViolations = exception.getConstraintViolations();

            assertEquals(1, constraintViolations.size());
            ConstraintViolation<Object> constraintViolation = constraintViolations.iterator().next();
            assertEquals("licensePlate", constraintViolation.getPropertyPath().toString());
            assertEquals(null, constraintViolation.getInvalidValue());
            assertEquals("must not be null", constraintViolation.getMessage());
        }

        car.setLicensePlate("D-A");

        Exchange exchange = template.request(url, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(car);
            }
        });

        assertNotNull(exchange);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldFailWithExpliciteDefaultGroup() {

        final String url = "bean-validator://x?group=javax.validation.groups.Default";
        final Car car = createCar("BMW", null);

        try {
            template.requestBody(url, car);
            fail("should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(BeanValidationException.class, e.getCause());

            BeanValidationException exception = (BeanValidationException) e.getCause();
            Set<ConstraintViolation<Object>> constraintViolations = exception.getConstraintViolations();

            assertEquals(1, constraintViolations.size());
            ConstraintViolation<Object> constraintViolation = constraintViolations.iterator().next();
            assertEquals("licensePlate", constraintViolation.getPropertyPath().toString());
            assertEquals(null, constraintViolation.getInvalidValue());
            assertEquals("must not be null", constraintViolation.getMessage());
        }

        car.setLicensePlate("D-A");

        Exchange exchange = template.request(url, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(car);
            }
        });

        assertNotNull(exchange);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldFailWithOptionalChecksGroup() {

        final String url = "bean-validator://x?group=org.apache.camel.component.bean.validator.OptionalChecks";
        final Car car = createCar("BMW", "D-A");

        try {
            template.requestBody(url, car);
            fail("should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(BeanValidationException.class, e.getCause());

            BeanValidationException exception = (BeanValidationException) e.getCause();
            Set<ConstraintViolation<Object>> constraintViolations = exception.getConstraintViolations();

            assertEquals(1, constraintViolations.size());
            ConstraintViolation<Object> constraintViolation = constraintViolations.iterator().next();
            assertEquals("licensePlate", constraintViolation.getPropertyPath().toString());
            assertEquals("D-A", constraintViolation.getInvalidValue());
            assertEquals("size must be between 5 and 14", constraintViolation.getMessage());
        }

        car.setLicensePlate("DD-AB-123");

        Exchange exchange = template.request(url, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(car);
            }
        });

        assertNotNull(exchange);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldFailWithOrderedChecksGroup() {

        final String url = "bean-validator://x?group=org.apache.camel.component.bean.validator.OrderedChecks";
        final Car car = createCar(null, "D-A");

        try {
            template.requestBody(url, car);
            fail("should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(BeanValidationException.class, e.getCause());

            BeanValidationException exception = (BeanValidationException) e.getCause();
            Set<ConstraintViolation<Object>> constraintViolations = exception.getConstraintViolations();

            assertEquals(1, constraintViolations.size());
            ConstraintViolation<Object> constraintViolation = constraintViolations.iterator().next();
            assertEquals("manufacturer", constraintViolation.getPropertyPath().toString());
            assertEquals(null, constraintViolation.getInvalidValue());
            assertEquals("must not be null", constraintViolation.getMessage());
        }

        car.setManufacturer("BMW");

        try {
            template.requestBody(url, car);
            fail("should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(BeanValidationException.class, e.getCause());

            BeanValidationException exception = (BeanValidationException) e.getCause();
            Set<ConstraintViolation<Object>> constraintViolations = exception.getConstraintViolations();

            assertEquals(1, constraintViolations.size());
            ConstraintViolation<Object> constraintViolation = constraintViolations.iterator().next();
            assertEquals("licensePlate", constraintViolation.getPropertyPath().toString());
            assertEquals("D-A", constraintViolation.getInvalidValue());
            assertEquals("size must be between 5 and 14", constraintViolation.getMessage());
        }

        car.setLicensePlate("DD-AB-123");

        Exchange exchange = template.request(url, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(car);
            }
        });

        assertNotNull(exchange);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldSuccessWithRedefinedDefaultGroup() {

        final String url = "bean-validator://x";
        final Car car = new CarWithRedefinedDefaultGroup(null, "DD-AB-123");

        Exchange exchange = template.request(url, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(car);
            }
        });

        assertNotNull(exchange);
    }

    @DisabledOnOs(AIX)
    @Test
    void validateShouldFailWithRedefinedDefaultGroup() {

        final String url = "bean-validator://x";
        final Car car = new CarWithRedefinedDefaultGroup(null, "D-A");

        try {
            template.requestBody(url, car);
            fail("should throw exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(BeanValidationException.class, e.getCause());

            BeanValidationException exception = (BeanValidationException) e.getCause();
            Set<ConstraintViolation<Object>> constraintViolations = exception.getConstraintViolations();

            assertEquals(1, constraintViolations.size());
            ConstraintViolation<Object> constraintViolation = constraintViolations.iterator().next();
            assertEquals("licensePlate", constraintViolation.getPropertyPath().toString());
            assertEquals("D-A", constraintViolation.getInvalidValue());
            assertEquals("size must be between 5 and 14", constraintViolation.getMessage());
        }
    }

    Car createCar(String manufacturer, String licencePlate) {
        return new CarWithAnnotations(manufacturer, licencePlate);
    }
}
