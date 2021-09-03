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
package org.apache.camel.component.cm.test;

import java.util.Set;
import java.util.UUID;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.apache.camel.component.cm.CMConfiguration;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@CamelSpringTest
@ContextConfiguration(classes = { ValidatorConfiguration.class })
public class CMConfigurationTest {

    @Autowired
    private Validator validator;

    @Test
    public void testNullProductToken() {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom("DefaultFrom");
        configuration.setProductToken(null);
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testNullDefaultFrom() {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom(null);

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testDefaultFromFieldMaxLength() {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom("123456789012");

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testDefaultFromFieldZeroLength() {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom("");

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testMaxNumberOfParts() {

        final CMConfiguration configuration = new CMConfiguration();

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultFrom("DefaultFrom");
        configuration.setDefaultMaxNumberOfParts(9);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testMaxNumberOfPartsZero() {

        final CMConfiguration configuration = new CMConfiguration();

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultFrom("DefaultFrom");
        configuration.setDefaultMaxNumberOfParts(0);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }
}
