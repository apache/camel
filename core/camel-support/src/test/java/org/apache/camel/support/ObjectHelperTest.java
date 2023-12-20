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

package org.apache.camel.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectHelperTest {

    @Test
    @DisplayName("Tests that isNumber returns false for empty, space or null")
    void isNumberEmpty() {
        assertFalse(ObjectHelper.isNumber(""));
        assertFalse(ObjectHelper.isNumber(" "));
        assertFalse(ObjectHelper.isNumber(null));
    }

    @Test
    @DisplayName("Tests that isNumber returns true for integers")
    void isNumberIntegers() {
        assertTrue(ObjectHelper.isNumber("1234"));
        assertTrue(ObjectHelper.isNumber("-1234"));
        assertTrue(ObjectHelper.isNumber("1"));
        assertTrue(ObjectHelper.isNumber("0"));
    }

    @Test
    @DisplayName("Tests that isNumber returns false for non-numeric chars")
    void isNumberNonNumeric() {
        assertFalse(ObjectHelper.isNumber("ABC"));
        assertFalse(ObjectHelper.isNumber("-ABC"));
        assertFalse(ObjectHelper.isNumber("ABC.0"));
        assertFalse(ObjectHelper.isNumber("-ABC.0"));
        assertFalse(ObjectHelper.isNumber("!@#$#$%@#$%"));
        assertFalse(ObjectHelper.isNumber("."));
        assertFalse(ObjectHelper.isNumber("-"));
    }

    @Test
    @DisplayName("Tests that isNumber returns false for floats")
    void isNumberFloats() {
        assertFalse(ObjectHelper.isNumber("12.34"));
        assertFalse(ObjectHelper.isNumber("-12.34"));
        assertFalse(ObjectHelper.isNumber("1.0"));
        assertFalse(ObjectHelper.isNumber("0.0"));
    }

    @Test
    @DisplayName("Tests that isFloatingNumber returns true for empty, space or null")
    void isFloatingNumberEmpty() {
        assertFalse(ObjectHelper.isFloatingNumber(""));
        assertFalse(ObjectHelper.isFloatingNumber(" "));
        assertFalse(ObjectHelper.isFloatingNumber(null));
    }

    @Test
    @DisplayName("Tests that isFloatingNumber returns false for non-numeric chars")
    void isFloatingNumberNonNumeric() {
        assertFalse(ObjectHelper.isFloatingNumber("ABC"));
        assertFalse(ObjectHelper.isFloatingNumber("-ABC"));
        assertFalse(ObjectHelper.isFloatingNumber("ABC.0"));
        assertFalse(ObjectHelper.isFloatingNumber("-ABC.0"));
        assertFalse(ObjectHelper.isFloatingNumber("!@#$#$%@#$%"));
        // TODO: fix ... currently it returns true for this
        //assertFalse(ObjectHelper.isFloatingNumber("."));
        assertFalse(ObjectHelper.isFloatingNumber("-"));
    }

    @Test
    @DisplayName("Tests that isFloatingNumber returns true for integers")
    void isFloatingNumberIntegers() {
        assertTrue(ObjectHelper.isFloatingNumber("1234"));
        assertTrue(ObjectHelper.isFloatingNumber("-1234"));
        assertTrue(ObjectHelper.isFloatingNumber("1"));
        assertTrue(ObjectHelper.isFloatingNumber("0"));
    }

    @Test
    @DisplayName("Tests that isFloatingNumber returns true for floats")
    void isFloatingNumberFloats() {
        assertTrue(ObjectHelper.isFloatingNumber("12.34"));
        assertTrue(ObjectHelper.isFloatingNumber("-12.34"));
        assertTrue(ObjectHelper.isFloatingNumber("1.0"));
        assertTrue(ObjectHelper.isFloatingNumber("0.0"));
    }

    @Test
    @DisplayName("Tests that isFloatingNumber returns true for invalid floats")
    void isFloatingNumberInvalidFloats() {
        assertFalse(ObjectHelper.isFloatingNumber("12..34"));
        assertFalse(ObjectHelper.isFloatingNumber("-12..34"));
        assertFalse(ObjectHelper.isFloatingNumber("1..0"));
        assertFalse(ObjectHelper.isFloatingNumber("0..0"));
        assertFalse(ObjectHelper.isFloatingNumber(".."));
    }

    @Test
    @DisplayName("Tests that isFloatingNumber returns false for valid floats using comma instead of dot")
    void isFloatingNumberFloatsWithComma() {
        assertFalse(ObjectHelper.isFloatingNumber("12,34"));
        assertFalse(ObjectHelper.isFloatingNumber("-12,34"));
        assertFalse(ObjectHelper.isFloatingNumber("1,0"));
        assertFalse(ObjectHelper.isFloatingNumber("0,0"));
    }
}
