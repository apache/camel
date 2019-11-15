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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { ValidatorConfiguration.class })
// @DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
// @DisableJmx(false)
// @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CMConfigurationTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    private Validator validator;

    @Before
    public void beforeTest() throws Exception {
    }

    // @After
    // public void afterTest() {

    @Test
    public void testNullProductToken() throws Exception {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom("DefaultFrom");
        configuration.setProductToken(null);
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testNullDefaultFrom() throws Exception {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom(null);

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testDefaultFromFieldMaxLength() throws Exception {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom("123456789012");

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testDefaultFromFieldZeroLength() throws Exception {

        final CMConfiguration configuration = new CMConfiguration();

        // length: 1-11
        configuration.setDefaultFrom("");

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultMaxNumberOfParts(8);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testMaxNumberOfParts() throws Exception {

        final CMConfiguration configuration = new CMConfiguration();

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultFrom("DefaultFrom");
        configuration.setDefaultMaxNumberOfParts(9);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testMaxNumberOfPartsZero() throws Exception {

        final CMConfiguration configuration = new CMConfiguration();

        configuration.setProductToken(UUID.randomUUID().toString());
        configuration.setDefaultFrom("DefaultFrom");
        configuration.setDefaultMaxNumberOfParts(0);
        configuration.setTestConnectionOnStartup(false);

        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = validator.validate(configuration);
        Assert.isTrue(1 == constraintViolations.size());
    }
}
