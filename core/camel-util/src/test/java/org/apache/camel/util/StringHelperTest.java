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
package org.apache.camel.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.StringHelper.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class StringHelperTest {

    @Test
    public void testCamelCashToDash() throws Exception {
        assertEquals(null, camelCaseToDash(null));
        assertEquals("", camelCaseToDash(""));

        assertEquals("hello-world", camelCaseToDash("HelloWorld"));
        assertEquals("hello-big-world", camelCaseToDash("HelloBigWorld"));
        assertEquals("hello-big-world", camelCaseToDash("Hello-bigWorld"));
        assertEquals("my-id", camelCaseToDash("MyId"));
        assertEquals("my-id", camelCaseToDash("MyID"));
        assertEquals("my-url", camelCaseToDash("MyUrl"));
        assertEquals("my-url", camelCaseToDash("MyURL"));
        assertEquals("my-big-id", camelCaseToDash("MyBigId"));
        assertEquals("my-big-id", camelCaseToDash("MyBigID"));
        assertEquals("my-big-url", camelCaseToDash("MyBigUrl"));
        assertEquals("my-big-url", camelCaseToDash("MyBigURL"));
        assertEquals("my-big-id-again", camelCaseToDash("MyBigIdAgain"));
        assertEquals("my-big-id-again", camelCaseToDash("MyBigIDAgain"));
        assertEquals("my-big-url-again", camelCaseToDash("MyBigUrlAgain"));
        assertEquals("my-big-url-again", camelCaseToDash("MyBigURLAgain"));

        assertEquals("use-mdc-logging", camelCaseToDash("UseMDCLogging"));
        assertEquals("mdc-logging-keys-pattern", camelCaseToDash("MDCLoggingKeysPattern"));
        assertEquals("available-phone-number-country", camelCaseToDash("AVAILABLE_PHONE_NUMBER_COUNTRY"));
        assertEquals("available-phone-number-country", camelCaseToDash("AVAILABLE-PHONE_NUMBER-COUNTRY"));
        assertEquals("available-phone-number-country", camelCaseToDash("Available-Phone-Number-Country"));
        assertEquals("available-phone-number-country", camelCaseToDash("Available_Phone_Number_Country"));
        assertEquals("available-phone-number-country", camelCaseToDash("available_phone_number_country"));
        assertEquals("available-phone-number-country", camelCaseToDash("availablePhoneNumberCountry"));
        assertEquals("available-phone-number-country", camelCaseToDash("AvailablePhoneNumberCountry"));
    }

    @Nested
    class DashToCamelCase {

        @Test
        void testDashToCamelCaseWithNull() throws Exception {
            assertThat(dashToCamelCase(null)).isNull();
        }

        @Test
        void testDashToCamelCaseWithEmptyValue() throws Exception {
            assertThat(dashToCamelCase("")).isEmpty();
        }

        @Test
        void testDashToCamelCaseWithNoDash() throws Exception {
            assertThat(dashToCamelCase("a")).isEqualTo("a");
        }

        @Test
        void testDashToCamelCaseWithOneDash() throws Exception {
            assertThat(dashToCamelCase("a-b")).isEqualTo("aB");
        }

        @Test
        void testDashToCamelCaseWithSeveralDashes() throws Exception {
            assertThat(dashToCamelCase("a-bb-cc-dd")).isEqualTo("aBbCcDd");
        }

        @Test
        void testDashToCamelCaseWithEndDash() throws Exception {
            assertThat(dashToCamelCase("a-")).isEqualTo("a");
        }

        @Test
        void testDashToCamelCaseWithEndDashes() throws Exception {
            assertThat(dashToCamelCase("a----")).isEqualTo("a");
        }

        @Test
        void testDashToCamelCaseWithSeceralDashesGrouped() throws Exception {
            assertThat(dashToCamelCase("a--b")).isEqualTo("aB");
        }
    }

    @Test
    public void testSplitWords() throws Exception {
        String[] arr = splitWords("apiName/methodName");
        assertEquals(2, arr.length);
        assertEquals("apiName", arr[0]);
        assertEquals("methodName", arr[1]);

        arr = splitWords("hello");
        assertEquals(1, arr.length);
        assertEquals("hello", arr[0]);
    }

    @Test
    public void testReplaceFirst() throws Exception {
        assertEquals("jms:queue:bar", replaceFirst("jms:queue:bar", "foo", "bar"));
        assertEquals("jms:queue:bar", replaceFirst("jms:queue:foo", "foo", "bar"));
        assertEquals("jms:queue:bar?blah=123", replaceFirst("jms:queue:foo?blah=123", "foo", "bar"));
        assertEquals("jms:queue:bar?blah=foo", replaceFirst("jms:queue:foo?blah=foo", "foo", "bar"));
    }
}
