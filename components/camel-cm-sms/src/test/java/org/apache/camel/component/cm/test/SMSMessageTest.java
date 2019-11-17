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

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber.CountryCodeSource;
import org.apache.camel.component.cm.client.SMSMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {ValidatorConfiguration.class })
// @DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
// @DisableJmx(false)
// @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SMSMessageTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    private Validator validator;

    private final PhoneNumberUtil pnu = PhoneNumberUtil.getInstance();
    private String validNumber;

    @Before
    public void beforeTest() throws Exception {

        validNumber = pnu.format(pnu.getExampleNumber("ES"), PhoneNumberFormat.E164);
    }

    // @After
    // public void afterTest() {

    @Test
    public void testSMSMessageConstructor() throws Throwable {

        // Coverage ;)
        SMSMessage message = new SMSMessage(null, null);
        Assert.isNull(message.getMessage());
        Assert.isNull(message.getPhoneNumber());

        message = new SMSMessage("idAsString", null, null, "MySelf");
        Assert.isTrue(message.getId().equals("idAsString"));
        Assert.isTrue(message.getFrom().equals("MySelf"));
    }

    @Test
    public void testNullMessageField() throws Exception {

        final SMSMessage m = new SMSMessage(null, validNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testNullPhoneNumber() throws Exception {

        final SMSMessage m = new SMSMessage("Hello world!", null);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testDynamicFromFieldMaxLength() throws Exception {

        String dynamicFrom = "messagelengthgreaterthan12";

        final SMSMessage m = new SMSMessage("idAsString", "Hello World", validNumber, dynamicFrom);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testDynamicFromFieldZeroLength() throws Exception {

        String zeroLengthDynamicFrom = "";

        final SMSMessage m = new SMSMessage("idAsString", "Hello World", validNumber, zeroLengthDynamicFrom);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testIdAsStringMaxLength() throws Exception {

        String idAsString = "thisistheidastringlengthgreaterthan32";

        final SMSMessage m = new SMSMessage(idAsString, "Hello World", validNumber, "MySelf");

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testIdAsStringFieldZeroLength() throws Exception {

        String zeroLengthIdAsString = "";

        final SMSMessage m = new SMSMessage(zeroLengthIdAsString, "Hello World", validNumber, "MySelf");

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testE164NullNumberIsInValid() throws Exception {

        final String phoneNumber = null;
        final SMSMessage m = new SMSMessage("Hello world!", phoneNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testE164IsValid() throws Exception {

        final SMSMessage m = new SMSMessage("Hello world!", validNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(0 == constraintViolations.size());
    }

    @Test
    public void testE164NoPlusSignedNumberIsInvalid() throws Exception {

        final String phoneNumber = "34600000000";
        final SMSMessage m = new SMSMessage("Hello world!", phoneNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testE164NoPlusSignedNumberBut00IsInvalid() throws Exception {

        final String phoneNumber = new PhoneNumber().setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN).setNationalNumber(0034600000000).toString();
        final SMSMessage m = new SMSMessage("Hello world!", phoneNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }

    @Test
    public void testE164NumberWithPlusSignIsInvalid() throws Exception {

        final String phoneNumber = "+34 600 00 00 00";
        final SMSMessage m = new SMSMessage("Hello world!", phoneNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        Assert.isTrue(1 == constraintViolations.size());
    }
}
