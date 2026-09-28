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
package org.apache.camel.http.common;

import java.util.Date;
import java.util.Locale;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultHttpBindingTest extends CamelTestSupport {

    @Test
    public void testConvertDate() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        Date date = new Date();
        Exchange exchange = new DefaultExchange(context);

        String value = binding.convertHeaderValueToString(exchange, date);
        assertNotEquals(value, date.toString());
        assertEquals(value, DefaultHttpBinding.getHttpDateFormat().format(date));
    }

    @Test
    public void testConvertDateTypeConverter() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        Date date = new Date();
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(DefaultHttpBinding.DATE_LOCALE_CONVERSION, false);
        String value = binding.convertHeaderValueToString(exchange, date);
        assertEquals(value, date.toString());
    }

    @Test
    public void testConvertLocale() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        Locale l = Locale.SIMPLIFIED_CHINESE;
        Exchange exchange = new DefaultExchange(context);

        String value = binding.convertHeaderValueToString(exchange, l);
        assertNotEquals(value, l.toString());
        assertEquals("zh-CN", value);
    }

    @Test
    public void testConvertLocaleTypeConverter() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        Locale l = Locale.SIMPLIFIED_CHINESE;
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(DefaultHttpBinding.DATE_LOCALE_CONVERSION, false);
        String value = binding.convertHeaderValueToString(exchange, l);
        assertEquals(value, l.toString());
    }

    @Test
    public void testFileNameAcceptedWithoutWhitelist() {
        DefaultHttpBinding binding = new DefaultHttpBinding();

        assertTrue(binding.isFileNameAccepted("report.pdf"));
    }

    @Test
    public void testFileNameAcceptedWithoutExtension() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        binding.setFileNameExtWhitelist("txt");

        assertTrue(binding.isFileNameAccepted("README"));
        assertTrue(binding.isFileNameAccepted(null));
    }

    @Test
    public void testFileNameAcceptedWithWildcardWhitelist() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        binding.setFileNameExtWhitelist("*");

        assertTrue(binding.isFileNameAccepted("report.pdf"));
        assertTrue(binding.isFileNameAccepted("script.sh"));
    }

    @Test
    public void testFileNameAcceptedMatchesEachExtension() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        binding.setFileNameExtWhitelist("txt, pdf");

        assertTrue(binding.isFileNameAccepted("notes.txt"));
        assertTrue(binding.isFileNameAccepted("report.pdf"));
        assertFalse(binding.isFileNameAccepted("image.png"));
    }

    @Test
    public void testFileNameAcceptedIsCaseInsensitive() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        binding.setFileNameExtWhitelist("TXT,pdf");

        assertTrue(binding.isFileNameAccepted("notes.txt"));
        assertTrue(binding.isFileNameAccepted("REPORT.PDF"));
    }

    @Test
    public void testFileNameAcceptedDoesNotMatchSubstring() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        binding.setFileNameExtWhitelist("txt");

        // "txt".contains("x") must not let an upload named evil.x through
        assertFalse(binding.isFileNameAccepted("evil.x"));

        binding.setFileNameExtWhitelist("txtdoc");
        assertFalse(binding.isFileNameAccepted("notes.txt"));
    }

    @Test
    public void testFileNameAcceptedDoesNotChangeConfiguredWhitelist() {
        DefaultHttpBinding binding = new DefaultHttpBinding();
        binding.setFileNameExtWhitelist("TXT");

        binding.isFileNameAccepted("notes.txt");
        assertEquals("TXT", binding.getFileNameExtWhitelist());
    }
}
