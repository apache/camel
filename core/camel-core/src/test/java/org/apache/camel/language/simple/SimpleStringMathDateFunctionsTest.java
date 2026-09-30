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
package org.apache.camel.language.simple;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24968: edge cases of the string, math, date and file functions.
 */
public class SimpleStringMathDateFunctionsTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testCapitalizeEmpty() {
        exchange.getMessage().setBody("");
        assertExpression("${capitalize()}", "");
    }

    @Test
    public void testMathWithoutArgumentsUsesTheBody() {
        exchange.getMessage().setBody(List.of(10, 20, 30));
        assertExpression("${sum()}", 60L);
        assertExpression("${max()}", 30L);
        assertExpression("${min()}", 10L);
        assertExpression("${average()}", 20L);
    }

    @Test
    public void testDateHeaderWithDashOrDot() {
        Date date = new Date(0);
        exchange.getMessage().setHeader("Last-Modified", date);
        exchange.getMessage().setHeader("X-Date", date);
        exchange.getMessage().setHeader("my.date", date);
        assertExpression("${date:header.Last-Modified}", date);
        assertExpression("${date:header.X-Date}", date);
        assertExpression("${date:header.my.date}", date);
        // an offset still works after a name with a dash
        assertExpression("${date:header.Last-Modified+1s}", new Date(1000));
        assertExpression("${date:header.Last-Modified - 1s}", new Date(-1000));
    }

    @Test
    public void testDateWithTimezoneOffset() {
        exchange.getMessage().setHeader("d", new Date(0));
        assertExpression("${date-with-timezone:header.d:GMT+02:00:HH:mm}", "02:00");
        assertExpression("${date-with-timezone:header.d:UTC:HH:mm}", "00:00");
    }

    @Test
    public void testSafeQuoteIsValidJson() {
        exchange.getMessage().setBody("He said \"hi\"");
        assertExpression("${safeQuote()}", "\"He said \\\"hi\\\"\"");
        exchange.getMessage().setBody("C:\\temp");
        assertExpression("${safeQuote()}", "\"C:\\\\temp\"");
        // already quoted once is not quoted twice
        exchange.getMessage().setBody("\"Hello\"");
        assertExpression("${safeQuote()}", "\"Hello\"");
        // numbers are not quoted, also BigDecimal as from a JSON document
        exchange.getMessage().setBody(new BigDecimal("12.50"));
        assertExpression("${safeQuote()}", new BigDecimal("12.50"));
    }

    @Test
    public void testKindOfType() {
        exchange.getMessage().setBody(new BigDecimal("1.5"));
        assertExpression("${kindOfType()}", "number");
        exchange.getMessage().setBody(Map.of("a", 1));
        assertExpression("${kindOfType()}", "object");
        exchange.getMessage().setBody(new String[] { "a" });
        assertExpression("${kindOfType()}", "array");
    }

    @Test
    public void testSubstringWithNullArguments() {
        exchange.getMessage().setBody("abc");
        Exception e = assertThrows(Exception.class, () -> evaluate("${substring(${header.none})}"));
        assertTrue(e.getMessage().contains("substring number expression evaluated to null"), e.getMessage());
        // no delimiter then nothing comes before/after/between
        assertExpression("${substringBefore(${header.none})}", null);
        assertExpression("${substringAfter(${header.none})}", null);
        assertExpression("${substringBetween(${header.none},'c')}", null);
    }

    @Test
    public void testPadWithSeveralCharacters() {
        assertExpression("${pad('foo',6,'ab')}", "fooaba");
        assertExpression("${pad('foo',-6,'ab')}", "abafoo");
        assertExpression("${pad('42',-5,'0')}", "00042");
        assertExpression("${pad('Hi',4)}", "Hi  ");
    }

    @Test
    public void testSizeAndLengthOfAnyArray() {
        exchange.getMessage().setBody(new Integer[] { 1, 2, 3 });
        assertExpression("${size()}", 3);
        assertExpression("${length()}", 3);
        exchange.getMessage().setBody(new Object[] { "a", "b" });
        assertExpression("${size()}", 2);
        exchange.getMessage().setBody(new boolean[] { true });
        assertExpression("${size()}", 1);
    }

    @Test
    public void testHiddenFileName() {
        exchange.getMessage().setHeader(Exchange.FILE_NAME, ".bashrc");
        assertExpression("${file:name.noext}", ".bashrc");
        assertExpression("${file:ext}", null);
        exchange.getMessage().setHeader(Exchange.FILE_NAME, ".route.yaml");
        assertExpression("${file:name.noext}", ".route");
        assertExpression("${file:ext}", "yaml");
    }

    private Object evaluate(String text) {
        return context.resolveLanguage("simple").createExpression(text).evaluate(exchange, Object.class);
    }
}
