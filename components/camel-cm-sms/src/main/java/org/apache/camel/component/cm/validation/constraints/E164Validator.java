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
package org.apache.camel.component.cm.validation.constraints;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Checks that a given character sequence (e.g. string) is a valid E164 formatted phonenumber. https://www.cmtelecom.com/newsroom/how-to-format-international-telephone- numbers
 * https://github.com/googlei18n/libphonenumber
 */
public class E164Validator implements ConstraintValidator<E164, String> {

    private final PhoneNumberUtil pnu = PhoneNumberUtil.getInstance();

    @Override
    public void initialize(final E164 constraintAnnotation) {
    }

    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {

        if (value == null) {
            return false;
        }
        try {

            final PhoneNumber parsingResult = pnu.parse(value, null);
            if (!pnu.format(parsingResult, PhoneNumberFormat.E164).equals(value)) {
                return false;
            }
            return true;
        } catch (final NumberParseException t) {
            // Errors when parsing phonenumber
            return false;
        }

        // CountryCodeSource.FROM_NUMBER_WITH_PLUS_SIGN
        // log.debug("Phone Number: {}", value);
        // log.debug("Country code: {}", numberProto.getCountryCode());
        // log.debug("National Number: {}", numberProto.getNationalNumber());
        // log.debug("E164 format: {}", pnu.format(numberProto,
        // PhoneNumberFormat.E164));

    }

}
