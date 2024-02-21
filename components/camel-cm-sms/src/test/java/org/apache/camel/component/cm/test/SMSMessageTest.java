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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber.CountryCodeSource;
import org.apache.camel.component.cm.client.SMSMessage;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@CamelSpringTest
@ContextConfiguration(classes = { ValidatorConfiguration.class })
public class SMSMessageTest {

    @Autowired
    private Validator validator;

    private final PhoneNumberUtil pnu = PhoneNumberUtil.getInstance();
    private String validNumber;

    @BeforeEach
    public void beforeTest() {

        validNumber = pnu.format(pnu.getExampleNumber("ES"), PhoneNumberFormat.E164);
    }

    @Test
    public void testSMSMessageConstructor() {

        // Coverage ;)
        SMSMessage message = new SMSMessage(null, null);
        assertNull(message.getMessage(), "SMS message should be null");
        assertNull(message.getPhoneNumber(), "Number null have been null");

        message = new SMSMessage("idAsString", null, null, "MySelf");
        assertEquals("idAsString", message.getId(), "Unexpected id");
        assertEquals("MySelf", message.getFrom(), "Unexpected from");
    }

    @Test
    public void testNullMessageField() {

        final SMSMessage m = new SMSMessage(null, validNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testNullPhoneNumber() {

        final SMSMessage m = new SMSMessage("Hello world!", null);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testDynamicFromFieldMaxLength() {

        String dynamicFrom = "messagelengthgreaterthan12";

        final SMSMessage m = new SMSMessage("idAsString", "Hello World", validNumber, dynamicFrom);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testDynamicFromFieldZeroLength() {

        String zeroLengthDynamicFrom = "";

        final SMSMessage m = new SMSMessage("idAsString", "Hello World", validNumber, zeroLengthDynamicFrom);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testIdAsStringMaxLength() {

        String idAsString = "thisistheidastringlengthgreaterthan32";

        final SMSMessage m = new SMSMessage(idAsString, "Hello World", validNumber, "MySelf");

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testIdAsStringFieldZeroLength() {

        String zeroLengthIdAsString = "";

        final SMSMessage m = new SMSMessage(zeroLengthIdAsString, "Hello World", validNumber, "MySelf");

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testE164IsValid() {

        final SMSMessage m = new SMSMessage("Hello world!", validNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(0, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @ParameterizedTest
    @ValueSource(strings = { "34600000000", "+34 600 00 00 00", "" })
    @NullSource
    public void testIsInvalidNumbers(String phoneNumber) {
        final SMSMessage m = new SMSMessage("Hello world!", phoneNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }

    @Test
    public void testE164NoPlusSignedNumberBut00IsInvalid() {

        final String phoneNumber = new PhoneNumber().setCountryCodeSource(CountryCodeSource.FROM_NUMBER_WITHOUT_PLUS_SIGN)
                .setNationalNumber(0034600000000).toString();
        final SMSMessage m = new SMSMessage("Hello world!", phoneNumber);

        final Set<ConstraintViolation<SMSMessage>> constraintViolations = validator.validate(m);
        assertEquals(1, constraintViolations.size(), "Unexpected number of constraint violations");
    }
}
