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
package org.apache.camel.language.datasonnet;

import com.datasonnet.document.MediaTypes;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CmlFunctionsTest extends CamelTestSupport {

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Null handling routes
                from("direct:defaultVal-null")
                        .transform(datasonnet("cml.defaultVal(null, 'fallback')", String.class))
                        .to("mock:result");
                from("direct:defaultVal-present")
                        .transform(datasonnet("cml.defaultVal('hello', 'fallback')", String.class))
                        .to("mock:result");
                from("direct:isEmpty-null")
                        .transform(datasonnet("cml.isEmpty(null)", Boolean.class))
                        .to("mock:result");
                from("direct:isEmpty-emptyString")
                        .transform(datasonnet("cml.isEmpty('')", Boolean.class))
                        .to("mock:result");
                from("direct:isEmpty-nonEmpty")
                        .transform(datasonnet("cml.isEmpty('hello')", Boolean.class))
                        .to("mock:result");
                from("direct:isEmpty-emptyArray")
                        .transform(datasonnet("cml.isEmpty([])", Boolean.class))
                        .to("mock:result");

                // Type coercion routes
                from("direct:toInteger-string")
                        .transform(datasonnet("cml.toInteger('42')", Integer.class))
                        .to("mock:result");
                from("direct:toInteger-num")
                        .transform(datasonnet("cml.toInteger(3.7)", Integer.class))
                        .to("mock:result");
                from("direct:toInteger-null")
                        .transform(datasonnet("cml.toInteger(null)", String.class))
                        .to("mock:result");
                from("direct:toDecimal-string")
                        .transform(datasonnet("cml.toDecimal('3.14')", Double.class))
                        .to("mock:result");
                from("direct:toBoolean-true")
                        .transform(datasonnet("cml.toBoolean('true')", Boolean.class))
                        .to("mock:result");
                from("direct:toBoolean-yes")
                        .transform(datasonnet("cml.toBoolean('yes')", Boolean.class))
                        .to("mock:result");
                from("direct:toBoolean-false")
                        .transform(datasonnet("cml.toBoolean('false')", Boolean.class))
                        .to("mock:result");
                from("direct:toBoolean-zero")
                        .transform(datasonnet("cml.toBoolean('0')", Boolean.class))
                        .to("mock:result");
                from("direct:toBoolean-num")
                        .transform(datasonnet("cml.toBoolean(1)", Boolean.class))
                        .to("mock:result");

                // Date/time routes
                from("direct:now")
                        .transform(datasonnet("cml.now()", String.class))
                        .to("mock:result");
                from("direct:nowFmt")
                        .transform(datasonnet("cml.nowFmt('yyyy-MM-dd')", String.class))
                        .to("mock:result");
                from("direct:formatDate")
                        .transform(datasonnet("cml.formatDate('2026-03-20T10:30:00Z', 'dd/MM/yyyy')", String.class))
                        .to("mock:result");
                from("direct:parseDate")
                        .transform(datasonnet("cml.parseDate('20/03/2026', 'dd/MM/yyyy')", Double.class))
                        .to("mock:result");
                from("direct:formatDate-null")
                        .transform(datasonnet("cml.formatDate(null, 'dd/MM/yyyy')", String.class))
                        .to("mock:result");

                // Utility routes
                from("direct:uuid")
                        .transform(datasonnet("cml.uuid()", String.class))
                        .to("mock:result");
                from("direct:typeOf-string")
                        .transform(datasonnet("cml.typeOf('hello')", String.class))
                        .to("mock:result");
                from("direct:typeOf-number")
                        .transform(datasonnet("cml.typeOf(42)", String.class))
                        .to("mock:result");
                from("direct:typeOf-boolean")
                        .transform(datasonnet("cml.typeOf(true)", String.class))
                        .to("mock:result");
                from("direct:typeOf-null")
                        .transform(datasonnet("cml.typeOf(null)", String.class))
                        .to("mock:result");
                from("direct:typeOf-array")
                        .transform(datasonnet("cml.typeOf([1,2,3])", String.class))
                        .to("mock:result");
                from("direct:typeOf-object")
                        .transform(datasonnet("cml.typeOf({a: 1})", String.class))
                        .to("mock:result");

                // Combined test: use body data with cml functions
                from("direct:combined")
                        .transform(datasonnet(
                                "{ name: cml.defaultVal(body.name, 'unknown'), active: cml.toBoolean(body.active) }",
                                String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:result");
            }
        };
    }

    // ---- Null handling tests ----

    @Test
    public void testDefaultValNull() throws Exception {
        Object result = sendAndGetResult("direct:defaultVal-null", "");
        assertEquals("fallback", result);
    }

    @Test
    public void testDefaultValPresent() throws Exception {
        Object result = sendAndGetResult("direct:defaultVal-present", "");
        assertEquals("hello", result);
    }

    @Test
    public void testIsEmptyNull() throws Exception {
        Object result = sendAndGetResult("direct:isEmpty-null", "");
        assertEquals(true, result);
    }

    @Test
    public void testIsEmptyEmptyString() throws Exception {
        Object result = sendAndGetResult("direct:isEmpty-emptyString", "");
        assertEquals(true, result);
    }

    @Test
    public void testIsEmptyNonEmpty() throws Exception {
        Object result = sendAndGetResult("direct:isEmpty-nonEmpty", "");
        assertEquals(false, result);
    }

    @Test
    public void testIsEmptyEmptyArray() throws Exception {
        Object result = sendAndGetResult("direct:isEmpty-emptyArray", "");
        assertEquals(true, result);
    }

    // ---- Type coercion tests ----

    @Test
    public void testToIntegerString() throws Exception {
        Object result = sendAndGetResult("direct:toInteger-string", "");
        assertEquals(42, result);
    }

    @Test
    public void testToIntegerNum() throws Exception {
        Object result = sendAndGetResult("direct:toInteger-num", "");
        assertEquals(3, result);
    }

    @Test
    public void testToIntegerNull() throws Exception {
        Object result = sendAndGetResult("direct:toInteger-null", "");
        assertNull(result);
    }

    @Test
    public void testToDecimalString() throws Exception {
        Object result = sendAndGetResult("direct:toDecimal-string", "");
        assertEquals(3.14, result);
    }

    @Test
    public void testToBooleanTrue() throws Exception {
        Object result = sendAndGetResult("direct:toBoolean-true", "");
        assertEquals(true, result);
    }

    @Test
    public void testToBooleanYes() throws Exception {
        Object result = sendAndGetResult("direct:toBoolean-yes", "");
        assertEquals(true, result);
    }

    @Test
    public void testToBooleanFalse() throws Exception {
        Object result = sendAndGetResult("direct:toBoolean-false", "");
        assertEquals(false, result);
    }

    @Test
    public void testToBooleanZero() throws Exception {
        Object result = sendAndGetResult("direct:toBoolean-zero", "");
        assertEquals(false, result);
    }

    @Test
    public void testToBooleanNum() throws Exception {
        Object result = sendAndGetResult("direct:toBoolean-num", "");
        assertEquals(true, result);
    }

    // ---- Date/time tests ----

    @Test
    public void testNow() throws Exception {
        Object result = sendAndGetResult("direct:now", "");
        assertNotNull(result);
        String str = result.toString();
        // Should be ISO-8601 format
        assertTrue(str.contains("T"), "Expected ISO-8601 format but got: " + str);
    }

    @Test
    public void testNowFmt() throws Exception {
        Object result = sendAndGetResult("direct:nowFmt", "");
        assertNotNull(result);
        String str = result.toString();
        // Should match yyyy-MM-dd format
        assertTrue(str.matches("\\d{4}-\\d{2}-\\d{2}"), "Expected date format but got: " + str);
    }

    @Test
    public void testFormatDate() throws Exception {
        Object result = sendAndGetResult("direct:formatDate", "");
        assertEquals("20/03/2026", result);
    }

    @Test
    public void testParseDate() throws Exception {
        Object result = sendAndGetResult("direct:parseDate", "");
        assertNotNull(result);
        // 2026-03-20 00:00:00 UTC in epoch millis
        assertTrue(((Number) result).longValue() > 0);
    }

    @Test
    public void testFormatDateNull() throws Exception {
        Object result = sendAndGetResult("direct:formatDate-null", "");
        assertNull(result);
    }

    // ---- Utility tests ----

    @Test
    public void testUuid() throws Exception {
        Object result = sendAndGetResult("direct:uuid", "");
        assertNotNull(result);
        String str = result.toString();
        // UUID format: 8-4-4-4-12 hex digits
        assertTrue(str.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Expected UUID format but got: " + str);
    }

    @Test
    public void testTypeOfString() throws Exception {
        assertEquals("string", sendAndGetResult("direct:typeOf-string", ""));
    }

    @Test
    public void testTypeOfNumber() throws Exception {
        assertEquals("number", sendAndGetResult("direct:typeOf-number", ""));
    }

    @Test
    public void testTypeOfBoolean() throws Exception {
        assertEquals("boolean", sendAndGetResult("direct:typeOf-boolean", ""));
    }

    @Test
    public void testTypeOfNull() throws Exception {
        assertEquals("null", sendAndGetResult("direct:typeOf-null", ""));
    }

    @Test
    public void testTypeOfArray() throws Exception {
        assertEquals("array", sendAndGetResult("direct:typeOf-array", ""));
    }

    @Test
    public void testTypeOfObject() throws Exception {
        assertEquals("object", sendAndGetResult("direct:typeOf-object", ""));
    }

    // ---- Combined tests ----

    @Test
    public void testCombined() throws Exception {
        template.sendBody("direct:combined", "{\"name\": \"John\", \"active\": \"true\"}");
        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange exchange = mock.assertExchangeReceived(mock.getReceivedCounter() - 1);
        String body = exchange.getMessage().getBody(String.class);
        assertTrue(body.contains("\"name\":\"John\"") || body.contains("\"name\": \"John\""));
        assertTrue(body.contains("\"active\":true") || body.contains("\"active\": true"));
    }

    private Object sendAndGetResult(String uri, Object body) {
        template.sendBody(uri, body);
        MockEndpoint mock = getMockEndpoint("mock:result");
        Exchange exchange = mock.assertExchangeReceived(mock.getReceivedCounter() - 1);
        return exchange.getMessage().getBody();
    }
}
