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
package org.apache.camel.language.csimple.joor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Predicate;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.language.bean.RuntimeBeanExpressionException;
import org.apache.camel.language.csimple.CSimpleLanguage;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.apache.camel.spi.Language;
import org.apache.camel.test.junit5.LanguageTestSupport;
import org.apache.camel.util.InetAddressUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class OriginalSimpleTest extends LanguageTestSupport {

    private static final String JAVA8_INDEX_OUT_OF_BOUNDS_ERROR_MSG = "Index: 2, Size: 2";
    private static final String INDEX_OUT_OF_BOUNDS_ERROR_MSG = "Index 2 out of bounds for length 2";

    @BindToRegistry
    private Animal myAnimal = new Animal("Donkey", 17);

    @BindToRegistry
    private Greeter greeter = new Greeter();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        CSimpleLanguage cs = (CSimpleLanguage) context.resolveLanguage("csimple");
        cs.addImport("org.apache.camel.language.csimple.joor.OriginalSimpleTest.*");
        return context;
    }

    @Test
    public void testSimpleExpressionOrPredicate() {
        Predicate predicate = context.resolveLanguage("csimple").createPredicate("${header.bar} == 123");
        assertTrue(predicate.matches(exchange));

        predicate = context.resolveLanguage("csimple").createPredicate("${header.bar} == 124");
        assertFalse(predicate.matches(exchange));

        Expression expression = context.resolveLanguage("csimple").createExpression("${body}");
        assertEquals("<hello id='m123'>world!</hello>", expression.evaluate(exchange, String.class));

        expression = context.resolveLanguage("csimple").createExpression("${body}");
        assertEquals("<hello id='m123'>world!</hello>", expression.evaluate(exchange, String.class));
        expression = context.resolveLanguage("csimple").createExpression("${body}");
        assertEquals("<hello id='m123'>world!</hello>", expression.evaluate(exchange, String.class));

        predicate = context.resolveLanguage("csimple").createPredicate("${header.bar} == 123");
        assertEquals(Boolean.TRUE, predicate.matches(exchange));
        predicate = context.resolveLanguage("csimple").createPredicate("${header.bar} == 124");
        assertEquals(Boolean.FALSE, predicate.matches(exchange));
        predicate = context.resolveLanguage("csimple").createPredicate("${header.bar} == 123");
        assertEquals(Boolean.TRUE, predicate.matches(exchange));
        predicate = context.resolveLanguage("csimple").createPredicate("${header.bar} == 124");
        assertEquals(Boolean.FALSE, predicate.matches(exchange));
    }

    @Test
    public void testResultType() {
        assertEquals(123, context.resolveLanguage("csimple").createExpression("${header.bar}").evaluate(exchange, int.class));
        assertEquals("123",
                context.resolveLanguage("csimple").createExpression("${header.bar}").evaluate(exchange, String.class));
        // should not be possible
        assertEquals(null, context.resolveLanguage("csimple").createExpression("${header.bar}").evaluate(exchange, Date.class));
        assertEquals(null,
                context.resolveLanguage("csimple").createExpression("${header.unknown}").evaluate(exchange, String.class));
    }

    @Test
    public void testRefExpression() {
        assertExpressionResultInstanceOf("${ref:myAnimal}", Animal.class);

        assertExpression("${ref:myAnimal}", "Donkey");
        assertExpression("${ref:unknown}", null);
        assertExpression("Hello ${ref:myAnimal}", "Hello Donkey");
        assertExpression("Hello ${ref:unknown}", "Hello null");
    }

    @Test
    public void testConstantExpression() {
        assertExpression("Hello World", "Hello World");
    }

    @Test
    public void testNull() {
        assertNull(context.resolveLanguage("csimple").createExpression("${null}").evaluate(exchange, Object.class));
    }

    @Test
    public void testSimpleFileDir() {
        assertExpression("file:mydir", "file:mydir");
    }

    @Test
    public void testEmptyExpression() {
        assertExpression("", "");
        try {
            assertExpression(null, null);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("expression must be specified", e.getMessage());
        }

        assertPredicate("", false);
        assertPredicate(" ", false);
        try {
            assertPredicate(null, false);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("expression must be specified", e.getMessage());
        }
    }

    @Test
    public void testExchangeExpression() {
        Expression exp = context.resolveLanguage("csimple").createExpression("${exchange}");
        assertNotNull(exp);
        assertEquals(exchange, exp.evaluate(exchange, Object.class));

        assertExpression("${exchange}", exchange);
    }

    @Test
    public void testExchangeOgnlExpression() {
        Expression exp = context.resolveLanguage("csimple").createExpression("${exchange.exchangeId}");
        assertNotNull(exp);
        assertEquals(exchange.getExchangeId(), exp.evaluate(exchange, Object.class));

        assertExpression("${exchange.exchangeId}", exchange.getExchangeId());
        assertExpression("${exchange.class.name}", "org.apache.camel.support.DefaultExchange");
    }

    @Test
    public void testBodyExpression() {
        Expression exp = context.resolveLanguage("csimple").createExpression("${body}");
        assertNotNull(exp);
    }

    @Test
    public void testBodyOgnlExpression() {
        Expression exp = context.resolveLanguage("csimple").createExpression("${body.toString()}");
        assertNotNull(exp);

        Language language = context.resolveLanguage("csimple");
        assertThrows(JoorCSimpleCompilationException.class, () -> language.createExpression("${body.xxx}"),
                "Should throw exception");

        assertThrows(SimpleIllegalSyntaxException.class, () -> language.createExpression("${bodyxxx}"),
                "Should throw exception");
    }

    @Test
    public void testBodyExpressionUsingAlternativeStartToken() {
        Expression exp = context.resolveLanguage("csimple").createExpression("$simple{body}");
        assertNotNull(exp);
    }

    @Test
    public void testBodyExpressionNotStringType() {
        exchange.getIn().setBody(123);
        Expression exp = context.resolveLanguage("csimple").createExpression("${body}");
        assertNotNull(exp);
        Object val = exp.evaluate(exchange, Object.class);
        assertIsInstanceOf(Integer.class, val);
        assertEquals(123, val);
    }

    @Test
    public void testBodyExpressionWithArray() {
        exchange.getIn().setBody(new MyClass());
        Expression exp = context.resolveLanguage("csimple").createExpression("${bodyAs(MyClass).myArray}");
        assertNotNull(exp);
        Object val = exp.evaluate(exchange, Object.class);
        assertIsInstanceOf(Object[].class, val);
    }

    @Test
    public void testSimpleExpressions() {
        assertExpression("${exchangeId}", exchange.getExchangeId());
        assertExpression("${id}", exchange.getIn().getMessageId());
        assertExpression("${body}", "<hello id='m123'>world!</hello>");
        assertExpression("${in.body}", "<hello id='m123'>world!</hello>");
        assertExpression("${in.header.foo}", "abc");
        assertExpression("${in.headers.foo}", "abc");
        assertExpression("${header.foo}", "abc");
        assertExpression("${headers.foo}", "abc");
        assertExpression("${routeId}", exchange.getFromRouteId());
        exchange.getExchangeExtension().setFromRouteId("myRouteId");
        assertExpression("${routeId}", "myRouteId");
    }

    @Test
    public void testTrimSimpleExpressions() {
        assertExpression(" \t${exchangeId}\n".trim(), exchange.getExchangeId());
        assertExpression("\n${id}\r".trim(), exchange.getIn().getMessageId());
        assertExpression("\t\r ${body}".trim(), "<hello id='m123'>world!</hello>");
        assertExpression("\n${in.body}\r".trim(), "<hello id='m123'>world!</hello>");
    }

    @Test
    public void testSimpleThreadId() {
        long id = Thread.currentThread().getId();
        assertExpression("${threadId}", id);
        assertExpression("The id is ${threadId}", "The id is " + id);
    }

    @Test
    public void testSimpleThreadName() {
        String name = Thread.currentThread().getName();
        assertExpression("${threadName}", name);
        assertExpression("The name is ${threadName}", "The name is " + name);
    }

    @Test
    public void testSimpleHostname() {
        String name = InetAddressUtil.getLocalHostNameSafe();
        assertExpression("${hostname}", name);
        assertExpression("The host is ${hostname}", "The host is " + name);
    }

    @Test
    public void testSimpleStepId() {
        assertExpression("${stepId}", null);
        exchange.setProperty(Exchange.STEP_ID, "foo");
        assertExpression("${stepId}", "foo");
    }

    @Test
    public void testSimpleExchangePropertyExpressions() {
        exchange.setProperty("medal", "gold");
        assertExpression("${exchangeProperty.medal}", "gold");
    }

    @Test
    public void testSimpleSystemPropertyExpressions() {
        System.setProperty("who", "I was here");
        assertExpression("${sys.who}", "I was here");
    }

    @Test
    public void testSimpleSystemEnvironmentExpressions() {
        String path = System.getenv("PATH");
        if (path != null) {
            assertExpression("${sysenv.PATH}", path);
            assertExpression("${sysenv:PATH}", path);
            assertExpression("${env.PATH}", path);
            assertExpression("${env:PATH}", path);
        }
    }

    @Test
    public void testSimpleSystemEnvironmentExpressionsIfDash() {
        String foo = System.getenv("FOO_SERVICE_HOST");
        if (foo != null) {
            assertExpression("${sysenv.FOO-SERVICE-HOST}", foo);
            assertExpression("${sysenv:FOO-SERVICE-HOST}", foo);
            assertExpression("${env.FOO-SERVICE-HOST}", foo);
            assertExpression("${env:FOO-SERVICE-HOST}", foo);
        }
    }

    @Test
    public void testSimpleSystemEnvironmentExpressionsIfLowercase() {
        String path = System.getenv("PATH");
        if (path != null) {
            assertExpression("${sysenv.path}", path);
            assertExpression("${sysenv:path}", path);
            assertExpression("${env.path}", path);
            assertExpression("${env:path}", path);
        }
    }

    @Test
    public void testSimpleCamelId() {
        assertExpression("${camelId}", context.getName());
    }

    @Test
    public void testOGNLBodyListAndMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("cool", "Camel rocks");
        map.put("dude", "Hey dude");
        map.put("code", 4321);

        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(map);

        exchange.getIn().setBody(lines);

        // TODO: helper to get value from map/list/collection

        assertExpression("${in.body[0][cool]}", "Camel rocks");
        assertExpression("${body[0][cool]}", "Camel rocks");
        assertExpression("${in.body[0][code]}", 4321);
        assertExpression("${body[0][code]}", 4321);
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testOGNLBodyEmptyList() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("list", new ArrayList<String>());

        exchange.getIn().setBody(map);

        assertExpression("${BodyAs(Map)?.get(\"list\")[0].toString}", null);
    }

    @Test
    public void testOGNLBodyExpression() {
        exchange.getIn().setBody("hello world");
        assertPredicate("${body} == \"hello world\"", true);
        assertPredicate("${bodyAs(String).toUpperCase()} == \"HELLO WORLD\"", true);
    }

    @Test
    public void testOGNLBodyAsExpression() {
        byte[] body = "hello world".getBytes();
        exchange.getIn().setBody(body);

        // there is no upper case method on byte array, but we can convert to
        // String as below
        try {
            assertPredicate("${bodyAs(byte[]).toUpperCase()} == \"HELLO WORLD\"", true);
            fail("Should throw exception");
        } catch (JoorCSimpleCompilationException e) {
            assertTrue(e.getCause().getMessage().contains("method toUpperCase()"));
        }

        assertPredicate("${bodyAs(String)} == \"hello world\"", true);
        assertPredicate("${bodyAs(String).toUpperCase()} == \"HELLO WORLD\"", true);

        // and body on exchange should not be changed
        assertSame(body, exchange.getIn().getBody());
    }

    @Test
    public void testOGNLMandatoryBodyAsExpression() {
        byte[] body = "hello world".getBytes();
        exchange.getIn().setBody(body);

        // there is no upper case method on byte array, but we can convert to
        // String as below
        try {
            assertPredicate("${bodyAs(byte[]).toUpperCase()} == \"HELLO WORLD\"", true);
            fail("Should throw exception");
        } catch (JoorCSimpleCompilationException e) {
            assertTrue(e.getCause().getMessage().contains("method toUpperCase()"));
        }

        assertPredicate("${mandatoryBodyAs(String)} == \"hello world\"", true);
        assertPredicate("${mandatoryBodyAs(String).toUpperCase()} == \"HELLO WORLD\"", true);

        // and body on exchange should not be changed
        assertSame(body, exchange.getIn().getBody());
    }

    @Test
    public void testOGNLCallReplace() {
        Map<String, Object> map = new HashMap<>();
        map.put("cool", "Camel rocks");
        map.put("dude", "Hey dude");
        exchange.getIn().setHeaders(map);

        assertExpression("${headerAs(cool, String).replaceAll(\"rocks\", \"is so cool\")}", "Camel is so cool");
    }

    @Test
    public void testOGNLBodyListAndMapAndMethod() {
        Map<String, OrderLine> map = new HashMap<>();
        map.put("camel", new OrderLine(123, "Camel in Action"));
        map.put("amq", new OrderLine(456, "ActiveMQ in Action"));

        List<Map<String, OrderLine>> lines = new ArrayList<>();
        lines.add(map);

        exchange.getIn().setBody(lines);

        assertExpression("${bodyAsIndex(OrderLine, '[0][camel]').id}", 123);
        assertExpression("${bodyAsIndex(OrderLine, '[0][camel]').name}", "Camel in Action");
        assertExpression("${bodyAsIndex(OrderLine, '[0][camel]').getId}", 123);
        assertExpression("${bodyAsIndex(OrderLine, '[0][camel]').getName}", "Camel in Action");

        assertExpression("${bodyAs(OrderLine)[0][camel].id}", 123);
        assertExpression("${bodyAs(OrderLine)[0][camel].name}", "Camel in Action");
        assertExpression("${bodyAs(OrderLine)[0][camel].getId}", 123);
        assertExpression("${bodyAs(OrderLine)[0][camel].getName}", "Camel in Action");
    }

    @Test
    public void testOGNLMandatoryBodyListAndMapAndMethod() {
        Map<String, OrderLine> map = new HashMap<>();
        map.put("camel", new OrderLine(123, "Camel in Action"));
        map.put("amq", new OrderLine(456, "ActiveMQ in Action"));
        map.put("noname", new OrderLine(789, null));

        List<Map<String, OrderLine>> lines = new ArrayList<>();
        lines.add(map);

        exchange.getIn().setBody(lines);

        assertExpression("${mandatoryBodyAsIndex(OrderLine, '[0][camel]').id}", 123);
        assertExpression("${mandatoryBodyAsIndex(OrderLine, '[0][camel]').name}", "Camel in Action");
        assertExpression("${mandatoryBodyAsIndex(OrderLine, '[0][camel]').getId}", 123);
        assertExpression("${mandatoryBodyAsIndex(OrderLine, '[0][camel]').getName}", "Camel in Action");

        assertExpression("${mandatoryBodyAsIndex(OrderLine, '[0][noname]').getId}", 789);
        assertExpression("${mandatoryBodyAsIndex(OrderLine, '[0][noname]').getName}", null);
        try {
            assertExpression("${mandatoryBodyAsIndex(OrderLine, '[0][doesnotexists]').getName}", null);
            fail("Should throw exception");
        } catch (Exception e) {
            assertIsInstanceOf(InvalidPayloadException.class, e.getCause());
        }

        assertExpression("${mandatoryBodyAs(OrderLine)[0][camel].id}", 123);
        assertExpression("${mandatoryBodyAs(OrderLine)[0][camel].name}", "Camel in Action");
        assertExpression("${mandatoryBodyAs(OrderLine)[0][camel].getId}", 123);
        assertExpression("${mandatoryBodyAs(OrderLine)[0][camel].getName}", "Camel in Action");
    }

    @Test
    public void testOGNLPropertyList() {
        List<String> lines = new ArrayList<>();
        lines.add("Camel in Action");
        lines.add("ActiveMQ in Action");
        exchange.setProperty("wicket", lines);

        assertExpression("${exchangeProperty.wicket[0]}", "Camel in Action");
        assertExpression("${exchangeProperty.wicket[1]}", "ActiveMQ in Action");
        try {
            assertExpression("${exchangeProperty.wicket[2]}", "");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertEquals(INDEX_OUT_OF_BOUNDS_ERROR_MSG, cause.getMessage());
        }
        assertExpression("${exchangeProperty.unknown[cool]}", null);
    }

    @Test
    public void testOGNLPropertyLinesList() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        exchange.setProperty("wicket", lines);

        assertExpression("${exchangePropertyAsIndex(wicket, OrderLine, '0').getId}", 123);
        assertExpression("${exchangePropertyAsIndex(wicket, OrderLine, '1').getName}", "ActiveMQ in Action");

        try {
            assertExpression("${exchangePropertyAsIndex(wicket, OrderLine, '2')}", "");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertEquals(INDEX_OUT_OF_BOUNDS_ERROR_MSG, cause.getMessage());
        }
        assertExpression("${exchangeProperty.unknown[cool]}", null);
        assertExpression("${exchangePropertyAsIndex(unknown, OrderLine, 'cool')}", null);
    }

    @Test
    public void testOGNLPropertyMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("cool", "Camel rocks");
        map.put("dude", "Hey dude");
        map.put("code", 4321);
        exchange.setProperty("wicket", map);

        assertExpression("${exchangeProperty.wicket[cool]}", "Camel rocks");
        assertExpression("${exchangeProperty.wicket[dude]}", "Hey dude");
        assertExpression("${exchangeProperty.wicket[unknown]}", null);
        assertExpression("${exchangeProperty.wicket[code]}", 4321);
        // no header named unknown
        assertExpression("${exchangeProperty?.unknown[cool]}", null);
        assertExpression("${exchangeProperty.unknown[cool]}", null);
    }

    @Test
    public void testOGNLExchangePropertyMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("cool", "Camel rocks");
        map.put("dude", "Hey dude");
        map.put("code", 4321);
        exchange.setProperty("wicket", map);

        assertExpression("${exchangeProperty.wicket[cool]}", "Camel rocks");
        assertExpression("${exchangeProperty.wicket[dude]}", "Hey dude");
        assertExpression("${exchangeProperty.wicket[unknown]}", null);
        assertExpression("${exchangeProperty.wicket[code]}", 4321);
        // no header named unknown
        assertExpression("${exchangeProperty?.unknown[cool]}", null);
        assertExpression("${exchangeProperty.unknown[cool]}", null);
    }

    @Test
    public void testOGNLPropertyMapWithDot() {
        Map<String, Object> map = new HashMap<>();
        map.put("this.code", "This code");
        exchange.setProperty("wicket", map);

        assertExpression("${exchangeProperty.wicket[this.code]}", "This code");
    }

    @Test
    public void testOGNLPropertyMapNotMap() {
        assertExpression("${exchangeProperty.foobar[bar]}", null);
    }

    @Test
    public void testOGNLPropertyMapIllegalSyntax() {
        try {
            assertExpression("${exchangeProperty.foobar[bar}", null);
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage()
                    .startsWith(
                            "Valid syntax: ${exchangeProperty.name[key]} was: exchangeProperty.foobar[bar at location 0"));
        }
    }

    @Test
    public void testOGNLExchangePropertyMapIllegalSyntax() {
        try {
            assertExpression("${exchangeProperty.foobar[bar}", null);
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertTrue(e.getMessage()
                    .startsWith(
                            "Valid syntax: ${exchangeProperty.name[key]} was: exchangeProperty.foobar[bar at location 0"));
        }
    }

    @Test
    public void testOGNLHeaderEmptyTest() {
        exchange.getIn().setHeader("beer", "");
        assertPredicate("${header.beer} == \"\"", true);
        assertPredicate("${header.beer} == \"\"", true);
        assertPredicate("${header.beer} == \" \"", false);
        assertPredicate("${header.beer} == \" \"", false);

        exchange.getIn().setHeader("beer", " ");
        assertPredicate("${header.beer} == \"\"", false);
        assertPredicate("${header.beer} == \"\"", false);
        assertPredicate("${header.beer} == \" \"", true);
        assertPredicate("${header.beer} == \" \"", true);

        assertPredicate("${headerAs(beer, String).toString().trim()} == \"\"", true);
        assertPredicate("${headerAs(beer, String).toString().trim()} == \"\"", true);

        exchange.getIn().setHeader("beer", "   ");
        assertPredicate("${headerAs(beer, String).trim()} == \"\"", true);
        assertPredicate("${headerAs(beer, String).trim()} == \"\"", true);
    }

    @Test
    public void testDateExpressions() {
        Calendar inHeaderCalendar = Calendar.getInstance();
        inHeaderCalendar.set(1974, Calendar.APRIL, 20);
        exchange.getIn().setHeader("birthday", inHeaderCalendar.getTime());

        Calendar propertyCalendar = Calendar.getInstance();
        propertyCalendar.set(1976, Calendar.JUNE, 22);
        exchange.setProperty("birthday", propertyCalendar.getTime());

        assertExpression("${date:header.birthday}", inHeaderCalendar.getTime());
        assertExpression("${date:header.birthday:yyyyMMdd}", "19740420");
        assertExpression("${date:header.birthday+24h:yyyyMMdd}", "19740421");

        // long
        assertExpression("${date:exchangeProperty.birthday}", propertyCalendar.getTime().getTime());
        // date
        assertExpression("${date:exchangeProperty.birthday}", propertyCalendar.getTime());
        assertExpression("${date:exchangeProperty.birthday:yyyyMMdd}", "19760622");
        assertExpression("${date:exchangeProperty.birthday+24h:yyyyMMdd}", "19760623");

        try {
            assertExpression("${date:yyyyMMdd}", "19740420");
            fail("Should thrown an exception");
        } catch (Exception e) {
            assertEquals("Command not supported for dateExpression: yyyyMMdd", e.getCause().getMessage());
        }
    }

    @Test
    public void testDateAndTimeExpressions() {
        Calendar cal = Calendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20, 8, 55, 47);
        cal.set(Calendar.MILLISECOND, 123);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertExpression("${date:header.birthday - 10s:yyyy-MM-dd'T'HH:mm:ss:SSS}", "1974-04-20T08:55:37:123");
        assertExpression("${date:header.birthday:yyyy-MM-dd'T'HH:mm:ss:SSS}", "1974-04-20T08:55:47:123");
    }

    @Test
    public void testDateWithTimezone() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        cal.set(1974, Calendar.APRIL, 20, 8, 55, 47);
        cal.set(Calendar.MILLISECOND, 123);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertExpression("${date-with-timezone:header.birthday:GMT+8:yyyy-MM-dd'T'HH:mm:ss:SSS}", "1974-04-20T08:55:47:123");
        assertExpression("${date-with-timezone:header.birthday:GMT:yyyy-MM-dd'T'HH:mm:ss:SSS}", "1974-04-20T00:55:47:123");
    }

    @Test
    public void testDateNow() {
        Object out = evaluateExpression("${date:now:hh:mm:ss a}", null);
        assertNotNull(out);
    }

    @Test
    public void testDateExchangeCreated() {
        Object out = evaluateExpression("${date:exchangeCreated:hh:mm:ss a}", "" + exchange.getCreated());
        assertNotNull(out);
    }

    @Test
    public void testDatePredicates() {
        assertPredicate("${date:now} < ${date:now+60s}");
        assertPredicate("${date:now-5s} < ${date:now}");
        assertPredicate("${date:now+5s} > ${date:now}");
    }

    @Test
    public void testComplexExpressions() {
        assertExpression("hey ${in.header.foo}", "hey abc");
        assertExpression("hey ${in.header:foo}", "hey abc");
        assertExpression("hey ${in.header.foo}!", "hey abc!");
        assertExpression("hey ${in.header:foo}!", "hey abc!");
        assertExpression("hey ${in.header.foo}-${in.header.foo}!", "hey abc-abc!");
        assertExpression("hey ${in.header:foo}-${in.header.foo}!", "hey abc-abc!");
        assertExpression("hey ${in.header.foo}${in.header.foo}", "hey abcabc");
        assertExpression("hey ${in.header:foo}${in.header.foo}", "hey abcabc");
        assertExpression("${in.header.foo}${in.header.foo}", "abcabc");
        assertExpression("${in.header:foo}${in.header:foo}", "abcabc");
        assertExpression("${in.header.foo}", "abc");
        assertExpression("${in.header:foo}", "abc");
        assertExpression("${in.header.foo}!", "abc!");
        assertExpression("${in.header:foo}!", "abc!");
    }

    @Test
    public void testComplexExpressionsUsingAlternativeStartToken() {
        assertExpression("hey $simple{in.header.foo}", "hey abc");
        assertExpression("hey $simple{in.header:foo}", "hey abc");
        assertExpression("hey $simple{in.header.foo}!", "hey abc!");
        assertExpression("hey $simple{in.header:foo}!", "hey abc!");
        assertExpression("hey $simple{in.header.foo}-$simple{in.header.foo}!", "hey abc-abc!");
        assertExpression("hey $simple{in.header:foo}-$simple{in.header.foo}!", "hey abc-abc!");
        assertExpression("hey $simple{in.header.foo}$simple{in.header.foo}", "hey abcabc");
        assertExpression("hey $simple{in.header:foo}$simple{in.header.foo}", "hey abcabc");
        assertExpression("$simple{in.header.foo}$simple{in.header.foo}", "abcabc");
        assertExpression("$simple{in.header:foo}$simple{in.header.foo}", "abcabc");
        assertExpression("$simple{in.header.foo}", "abc");
        assertExpression("$simple{in.header:foo}", "abc");
        assertExpression("$simple{in.header.foo}!", "abc!");
        assertExpression("$simple{in.header:foo}!", "abc!");
    }

    @Test
    public void testInvalidComplexExpression() {
        try {
            assertExpression("hey ${foo", "bad expression!");
            fail("Should have thrown an exception!");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(8, e.getIndex());
        }
    }

    @Test
    public void testPredicates() {
        assertPredicate("${body}");
        assertPredicate("${header.foo}");
        assertPredicate("${header.madeUpHeader}", false);
    }

    @Test
    public void testExceptionMessage() {
        exchange.setException(new IllegalArgumentException("Just testing"));
        assertExpression("${exception.message}", "Just testing");
        assertExpression("Hello ${exception.message} World", "Hello Just testing World");
    }

    @Test
    public void testExceptionStacktrace() {
        exchange.setException(new IllegalArgumentException("Just testing"));

        String out = context.resolveLanguage("csimple").createExpression("${exception.stacktrace}").evaluate(exchange,
                String.class);
        assertNotNull(out);
        assertTrue(out.startsWith("java.lang.IllegalArgumentException: Just testing"));
        assertTrue(out.contains("at org.apache.camel.language."));
    }

    @Test
    public void testException() {
        exchange.setException(new IllegalArgumentException("Just testing"));

        Exception out = context.resolveLanguage("csimple").createExpression("${exception}").evaluate(exchange, Exception.class);
        assertNotNull(out);
        assertIsInstanceOf(IllegalArgumentException.class, out);
        assertEquals("Just testing", out.getMessage());
    }

    @Test
    public void testBodyAs() {
        assertExpression("${bodyAs(String)}", "<hello id='m123'>world!</hello>");
        assertExpression("${bodyAs(\"String\")}", "<hello id='m123'>world!</hello>");

        exchange.getIn().setBody(null);
        assertExpression("${bodyAs(\"String\")}", null);

        exchange.getIn().setBody(456);
        assertExpression("${bodyAs(Integer)}", 456);
        assertExpression("${bodyAs(int)}", 456);
        assertExpression("${bodyAs(\"int\")}", 456);

        try {
            assertExpression("${bodyAs(XXX)}", 456);
            fail("Should have thrown an exception");
        } catch (JoorCSimpleCompilationException e) {
            // expected
        }
    }

    @Test
    public void testMandatoryBodyAs() {
        assertExpression("${mandatoryBodyAs(String)}", "<hello id='m123'>world!</hello>");
        assertExpression("${mandatoryBodyAs(\"String\")}", "<hello id='m123'>world!</hello>");

        exchange.getIn().setBody(null);
        try {
            assertExpression("${mandatoryBodyAs(\"String\")}", "");
            fail("Should have thrown exception");
        } catch (ExpressionEvaluationException e) {
            assertIsInstanceOf(InvalidPayloadException.class, e.getCause());
        }

        exchange.getIn().setBody(456);
        assertExpression("${mandatoryBodyAs(Integer)}", 456);
        assertExpression("${mandatoryBodyAs(int)}", 456);
        assertExpression("${mandatoryBodyAs('int')}", 456);
        assertExpression("${mandatoryBodyAs(\"int\")}", 456);

        try {
            assertExpression("${mandatoryBodyAs(XXX)}", 456);
            fail("Should have thrown an exception");
        } catch (JoorCSimpleCompilationException e) {
            // expected
        }
    }

    @Test
    public void testHeaderEmptyBody() {
        // set an empty body
        exchange.getIn().setBody(null);

        assertExpression("${header.foo}", "abc");
        assertExpression("${header:foo}", "abc");
        assertExpression("${headers.foo}", "abc");
        assertExpression("${headers:foo}", "abc");
        assertExpression("${in.header.foo}", "abc");
        assertExpression("${in.header:foo}", "abc");
        assertExpression("${in.headers.foo}", "abc");
        assertExpression("${in.headers:foo}", "abc");
    }

    @Test
    public void testHeadersWithBracket() {
        assertExpression("${headers[foo]}", "abc");
        assertExpression("${in.headers[foo]}", "abc");
    }

    @Test
    public void testOgnlOnHeadersWithBracket() {
        assertOgnlOnHeadersWithSquareBrackets("order");
        assertOgnlOnHeadersWithSquareBrackets("purchase.order");
        assertOgnlOnHeadersWithSquareBrackets("foo.bar.qux");
        assertOgnlOnHeadersWithSquareBrackets("purchase order");
    }

    private void assertOgnlOnHeadersWithSquareBrackets(String key) {
        exchange.getIn().setHeader(key, new OrderLine(123, "Camel in Action"));
        assertExpression("${headerAs(" + key + ", OrderLine).name}", "Camel in Action");
        assertExpression("${headerAs(" + key + ", OrderLine).name}", "Camel in Action");
        assertExpression("${headerAs(\"" + key + "\", OrderLine).name}", "Camel in Action");
    }

    @Test
    public void testOgnlOnExchangePropertiesWithBracket() throws Exception {
        assertOgnlOnExchangePropertiesWithBracket("order");
        assertOgnlOnExchangePropertiesWithBracket("purchase.order");
        assertOgnlOnExchangePropertiesWithBracket("foo.bar.qux");
        assertOgnlOnExchangePropertiesWithBracket("purchase order");
    }

    public void assertOgnlOnExchangePropertiesWithBracket(String key) {
        exchange.setProperty(key, new OrderLine(123, "Camel in Action"));
        assertExpression("${exchangePropertyAs(" + key + ", OrderLine).name}", "Camel in Action");
        assertExpression("${exchangePropertyAs(\"" + key + "\", OrderLine).name}", "Camel in Action");
    }

    @Test
    public void testIsInstanceOfEmptyBody() {
        // set an empty body
        exchange.getIn().setBody(null);

        try {
            assertPredicate("${body} is null", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(11, e.getIndex());
        }
    }

    @Test
    public void testHeaders() {
        Map<String, Object> headers = exchange.getIn().getHeaders();
        assertEquals(2, headers.size());

        assertExpression("${headers}", headers);
        assertExpression("${in.headers}", headers);
    }

    @Test
    public void testHeaderKeyWithSpace() {
        Map<String, Object> headers = exchange.getIn().getHeaders();
        headers.put("some key", "Some Value");
        assertEquals(3, headers.size());

        assertExpression("${headerAs(foo,String)}", "abc");
        assertExpression("${headerAs(some key,String)}", "Some Value");
        assertExpression("${headerAs(\"some key\",String)}", "Some Value");

        assertExpression("${header[foo]}", "abc");
        assertExpression("${header[some key]}", "Some Value");
        assertExpression("${header[\"some key\"]}", "Some Value");

        assertExpression("${headers[foo]}", "abc");
        assertExpression("${headers[some key]}", "Some Value");
        assertExpression("${headers[\"some key\"]}", "Some Value");
    }

    @Test
    public void testHeaderAs() {
        assertExpression("${headerAs(foo,String)}", "abc");

        assertExpression("${headerAs(bar,int)}", 123);
        assertExpression("${headerAs(bar, int)}", 123);
        assertExpression("${headerAs(\"bar\", int)}", 123);
        assertExpression("${headerAs(\"bar\",\"int\")}", 123);
        assertExpression("${headerAs(\"bar\",\"Integer\")}", 123);
        assertExpression("${headerAs(\"bar\",\"int\")}", 123);
        assertExpression("${headerAs(bar,String)}", "123");

        assertExpression("${headerAs(unknown,String)}", null);

        try {
            assertExpression("${headerAs(unknown String)}", null);
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Valid syntax: ${headerAs(key, type)} was: headerAs(unknown String)"));
        }

        try {
            assertExpression("${headerAs(fool,String).test}", null);
            fail("Should have thrown an exception");
        } catch (JoorCSimpleCompilationException e) {
            // expected
        }

        try {
            assertExpression("${headerAs(bar,XXX)}", 123);
            fail("Should have thrown an exception");
        } catch (JoorCSimpleCompilationException e) {
            // expected
        }
    }

    @Test
    public void testIllegalSyntax() {
        try {
            assertExpression("hey ${xxx} how are you?", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Unknown function: xxx at location 4"));
        }

        try {
            assertExpression("${xxx}", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Unknown function: xxx at location 0"));
        }

        try {
            assertExpression("${bodyAs(xxx}", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Valid syntax: ${bodyAs(type)} was: bodyAs(xxx"));
        }
    }

    @Test
    public void testOGNLHeaderList() {
        List<String> lines = new ArrayList<>();
        lines.add("Camel in Action");
        lines.add("ActiveMQ in Action");
        exchange.getIn().setHeader("wicket", lines);

        assertExpression("${header.wicket[0]}", "Camel in Action");
        assertExpression("${header.wicket[1]}", "ActiveMQ in Action");
        try {
            assertExpression("${header.wicket[2]}", "");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertEquals(INDEX_OUT_OF_BOUNDS_ERROR_MSG, cause.getMessage());
        }
        assertExpression("${header.unknown[cool]}", null);
    }

    @Test
    public void testOGNLHeaderLinesList() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        exchange.getIn().setHeader("wicket", lines);

        assertExpression("${headerAsIndex(wicket, OrderLine, 0).getId}", 123);
        assertExpression("${headerAsIndex(wicket, OrderLine, 1).getName}", "ActiveMQ in Action");
        try {
            assertExpression("${header.wicket[2]}", "");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertEquals(INDEX_OUT_OF_BOUNDS_ERROR_MSG, cause.getMessage());
        }
        assertExpression("${header.unknown[cool]}", null);
    }

    @Test
    public void testOGNLHeaderMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("cool", "Camel rocks");
        map.put("dude", "Hey dude");
        map.put("code", 4321);
        exchange.getIn().setHeader("wicket", map);

        assertExpression("${header.wicket[cool]}", "Camel rocks");
        assertExpression("${header.wicket[dude]}", "Hey dude");
        assertExpression("${header.wicket[unknown]}", null);
        assertExpression("${header.wicket[code]}", 4321);
        // no header named unknown
        assertExpression("${header?.unknown[cool]}", null);
        assertExpression("${header.unknown[cool]}", null);
    }

    @Test
    public void testOGNLHeaderMapWithDot() {
        Map<String, Object> map = new HashMap<>();
        map.put("this.code", "This code");
        exchange.getIn().setHeader("wicket", map);

        assertExpression("${header.wicket[this.code]}", "This code");
    }

    @Test
    public void testOGNLHeaderMapNotMap() {
        assertExpression("${header.foo[bar]}", null);
    }

    @Test
    public void testOGNLHeaderMapIllegalSyntax() {
        try {
            assertExpression("${header.foo[bar}", null);
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertTrue(e.getMessage().startsWith("Valid syntax: ${header.name[key]} was: header.foo[bar"));
        }
    }

    @Test
    public void testBodyOGNLAsMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo", "Camel");
        map.put("bar", 6);
        exchange.getIn().setBody(map);

        assertExpression("${in.body[foo]}", "Camel");
        assertExpression("${in.body[bar]}", 6);
    }

    @Test
    public void testBodyOGNLAsMapWithDot() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo.bar", "Camel");
        exchange.getIn().setBody(map);

        assertExpression("${body[foo.bar]}", "Camel");
    }

    @Test
    public void testBodyOGNLAsMapShorthand() {
        Map<String, Object> map = new HashMap<>();
        map.put("foo", "Camel");
        map.put("bar", 6);
        exchange.getIn().setBody(map);

        assertExpression("${body[foo]}", "Camel");
        assertExpression("${body[bar]}", 6);
    }

    @Test
    public void testBodyOGNLSimple() {
        Animal camel = new Animal("Camel", 6);
        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).getName}", "Camel");
        assertExpression("${bodyAs(Animal).getAge}", 6);
    }

    @Test
    public void testExceptionOGNLSimple() {
        exchange.getIn().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, "myPolicy");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT,
                new CamelAuthorizationException("The camel authorization exception", exchange));

        assertExpression("${exceptionAs(org.apache.camel.CamelAuthorizationException).getPolicyId}", "myPolicy");
    }

    @Test
    public void testBodyOGNLSimpleShorthand() {
        Animal camel = new Animal("Camel", 6);
        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).name}", "Camel");
        assertExpression("${bodyAs(Animal).age}", 6);
    }

    @Test
    public void testBodyOGNLSimpleOperator() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertPredicate("${bodyAs(Animal).getName} contains \"Camel\"", true);
        assertPredicate("${bodyAs(Animal).getName} contains \"Tiger\"", false);
        assertPredicate("${bodyAs(Animal).getAge} < 10", true);
        assertPredicate("${bodyAs(Animal).getAge} > 10", false);
        assertPredicate("${bodyAs(Animal).getAge} <= \"6\"", true);
        assertPredicate("${bodyAs(Animal).getAge} > \"6\"", false);

        assertPredicate("${bodyAs(Animal).getAge} < ${bodyAs(Animal).getFriend.getAge}", true);
        assertPredicate("${bodyAs(Animal).getFriend.isDangerous()} == true", true);
    }

    @Test
    public void testBodyOGNLSimpleOperatorShorthand() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertPredicate("${bodyAs(Animal).name} contains \"Camel\"", true);
        assertPredicate("${bodyAs(Animal).name} contains \"Tiger\"", false);
        assertPredicate("${bodyAs(Animal).age} < 10", true);
        assertPredicate("${bodyAs(Animal).age} > 10", false);
        assertPredicate("${bodyAs(Animal).age} <= \"6\"", true);
        assertPredicate("${bodyAs(Animal).age} > \"6\"", false);

        assertPredicate("${bodyAs(Animal).age} < ${bodyAs(Animal).friend.age}", true);
        assertPredicate("${bodyAs(Animal).friend.isDangerous()} == true", true);
    }

    @Test
    public void testBodyOGNLNested() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).getName}", "Camel");
        assertExpression("${bodyAs(Animal).getAge}", 6);

        assertExpression("${bodyAs(Animal).getFriend.getName}", "Tony the Tiger");
        assertExpression("${bodyAs(Animal).getFriend.getAge}", "13");
    }

    @Test
    public void testBodyOGNLNestedShorthand() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).name}", "Camel");
        assertExpression("${bodyAs(Animal).age}", 6);

        assertExpression("${bodyAs(Animal).friend.name}", "Tony the Tiger");
        assertExpression("${bodyAs(Animal).friend.age}", "13");
    }

    @Test
    public void testBodyOGNLOrderList() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        assertExpression("${bodyAs(Order).getLines().get(0).getId}", 123);
        assertExpression("${bodyAs(Order).getLines().get(0).getName}", "Camel in Action");

        assertExpression("${bodyAs(Order).getLines().get(1).getId}", 456);
        assertExpression("${bodyAs(Order).getLines().get(1).getName}", "ActiveMQ in Action");
    }

    @Test
    public void testBodyOGNLOrderListShorthand() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        assertExpression("${bodyAs(Order).lines[0].id}", 123);
        assertExpression("${bodyAs(Order).lines[0].name}", "Camel in Action");

        assertExpression("${bodyAs(Order).lines[1].id}", 456);
        assertExpression("${bodyAs(Order).lines[1].name}", "ActiveMQ in Action");

        assertExpression("${bodyAs(Order).lines.size()}", 2);
    }

    @Test
    public void testBodyOGNLListMap() {
        List<Map<String, String>> grid = new ArrayList<>();
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("ABC", "123");
        cells.put("DEF", "456");
        grid.add(cells);

        Map<String, String> cells2 = new LinkedHashMap<>();
        cells2.put("HIJ", "789");
        grid.add(cells2);

        exchange.getIn().setBody(grid);

        assertExpression("${in.body[0][ABC]}", "123");
        assertExpression("${in.body[0][DEF]}", "456");
        assertExpression("${in.body[0][\"ABC\"]}", "123");
        assertExpression("${in.body[0][\"DEF\"]}", "456");
        assertExpression("${in.body[1][HIJ]}", "789");
        assertExpression("${in.body[1][\"HIJ\"]}", "789");
    }

    @Test
    public void testBodyOGNLList() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));

        exchange.getIn().setBody(lines);

        assertExpression("${bodyAs(OrderLine)[0].getId}", 123);
        assertExpression("${bodyAs(OrderLine)[0].getName}", "Camel in Action");

        assertExpression("${bodyAs(OrderLine)[1].getId}", 456);
        assertExpression("${bodyAs(OrderLine)[1].getName}", "ActiveMQ in Action");
    }

    @Test
    public void testBodyOGNLListShorthand() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));

        exchange.getIn().setBody(lines);

        assertExpression("${bodyAs(OrderLine)[0].id}", 123);
        assertExpression("${bodyAs(OrderLine)[0].name}", "Camel in Action");

        assertExpression("${bodyAs(OrderLine)[1].id}", 456);
        assertExpression("${bodyAs(OrderLine)[1].name}", "ActiveMQ in Action");
    }

    @Test

    public void testBodyAsIndexOGNL() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));

        exchange.getIn().setBody(lines);

        assertExpression("${bodyAsIndex(OrderLine, 0).id}", 123);
        assertExpression("${bodyAsIndex(OrderLine, 0).name}", "Camel in Action");
        assertExpression("${bodyAsIndex(OrderLine, 1).id}", 456);
        assertExpression("${bodyAsIndex(OrderLine, 1).name}", "ActiveMQ in Action");
    }

    @Test
    public void testBodyOGNLArray() {
        OrderLine[] lines = new OrderLine[2];
        lines[0] = new OrderLine(123, "Camel in Action");
        lines[1] = new OrderLine(456, "ActiveMQ in Action");

        exchange.getIn().setBody(lines);

        assertExpression("${bodyAs(OrderLine)[0].getId}", 123);
        assertExpression("${bodyAs(OrderLine)[0].getName}", "Camel in Action");

        assertExpression("${bodyAs(OrderLine)[1].getId}", 456);
        assertExpression("${bodyAs(OrderLine)[1].getName}", "ActiveMQ in Action");
    }

    @Test
    public void testBodyOGNLArrayShorthand() {
        OrderLine[] lines = new OrderLine[2];
        lines[0] = new OrderLine(123, "Camel in Action");
        lines[1] = new OrderLine(456, "ActiveMQ in Action");

        exchange.getIn().setBody(lines);

        assertExpression("${bodyAsIndex(OrderLine, 0).id}", 123);
        assertExpression("${bodyAsIndex(OrderLine, 0).name}", "Camel in Action");

        assertExpression("${bodyAsIndex(OrderLine, 1).id}", 456);
        assertExpression("${bodyAsIndex(OrderLine, 1).name}", "ActiveMQ in Action");
    }

    @Test
    public void testBodyOGNLOrderListOutOfBounds() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${bodyAs(Order).getLines[3].getId}", 123);
            fail("Should have thrown an exception");
        } catch (ExpressionEvaluationException e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertEquals("Index 3 out of bounds for length 2", cause.getMessage());
        }
    }

    @Test
    public void testBodyOGNLOrderListOutOfBoundsShorthand() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${bodyAs(Order).lines[3].id}", 123);
            fail("Should have thrown an exception");
        } catch (Exception e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Index 3 out of bounds for length 2"));
        }
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOGNLOrderListOutOfBoundsWithNullSafe() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        assertExpression("${bodyAs(Order)?.getLines[3].getId}", null);
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOGNLOrderListOutOfBoundsWithNullSafeShorthand() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        assertExpression("${bodyAs(Order)?.lines[3].id}", null);
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOGNLOrderListNoMethodNameWithNullSafe() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${bodyAs(Order).getLines[0]?.getRating}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            MethodNotFoundException cause = assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
            assertEquals("getRating", cause.getMethodName());
        }
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOGNLOrderListNoMethodNameWithNullSafeShorthand() {
        List<OrderLine> lines = new ArrayList<>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${bodyAs(Order).lines[0]?.rating}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            MethodNotFoundException cause = assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
            assertEquals("rating", cause.getMethodName());
        }
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOGNLNullSafeToAvoidNPE() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).getName}", "Camel");
        assertExpression("${bodyAs(Animal).getAge}", 6);

        assertExpression("${bodyAs(Animal).getFriend.getName}", "Tony the Tiger");
        assertExpression("${bodyAs(Animal).getFriend.getAge}", "13");

        // using null safe to avoid the NPE
        assertExpression("${bodyAs(Animal).getFriend?.getFriend.getName}", null);
        try {
            // without null safe we get an NPE
            assertExpression("${bodyAs(Animal).getFriend.getFriend.getName}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            assertEquals(
                    "Failed to invoke method: .getFriend.getFriend.getName on org.apache.camel.language.simple.SimpleTest.Animal"
                         + " due last method returned null and therefore cannot continue to invoke method .getName on a null instance",
                    e.getMessage());
        }
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOGNLNullSafeToAvoidNPEShorthand() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).name}", "Camel");
        assertExpression("${bodyAs(Animal).age}", 6);

        // just to mix it a bit
        assertExpression("${bodyAs(Animal).friend.getName}", "Tony the Tiger");
        assertExpression("${bodyAs(Animal).getFriend.age}", "13");

        // using null safe to avoid the NPE
        assertExpression("${bodyAs(Animal).friend?.friend.name}", null);
        try {
            // without null safe we get an NPE
            assertExpression("${bodyAs(Animal).friend.friend.name}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            assertEquals("Failed to invoke method: .friend.friend.name on org.apache.camel.language.simple.SimpleTest.Animal"
                         + " due last method returned null and therefore cannot continue to invoke method .name on a null instance",
                    e.getMessage());
        }
    }

    @Test
    public void testBodyOGNLReentrant() {
        Animal camel = new Animal("Camel", 6);
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal elephant = new Animal("Big Ella", 48);

        camel.setFriend(tiger);
        tiger.setFriend(elephant);
        elephant.setFriend(camel);

        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).getFriend.getFriend.getFriend.getName}", "Camel");
        assertExpression("${bodyAs(Animal).getFriend.getFriend.getFriend.getFriend.getName}", "Tony the Tiger");
        assertExpression("${bodyAs(Animal).getFriend.getFriend.getFriend.getFriend.getFriend.getName}", "Big Ella");
    }

    @Test
    public void testBodyOGNLReentrantShorthand() {
        Animal camel = new Animal("Camel", 6);
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal elephant = new Animal("Big Ella", 48);

        camel.setFriend(tiger);
        tiger.setFriend(elephant);
        elephant.setFriend(camel);

        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).friend.friend.friend.name}", "Camel");
        assertExpression("${bodyAs(Animal).friend.friend.friend.friend.name}", "Tony the Tiger");
        assertExpression("${bodyAs(Animal).friend.friend.friend.friend.friend.name}", "Big Ella");
    }

    @Test
    public void testBodyOGNLBoolean() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        exchange.getIn().setBody(tiger);

        assertExpression("${bodyAs(Animal).isDangerous()}", "true");

        Animal camel = new Animal("Camel", 6);
        exchange.getIn().setBody(camel);

        assertExpression("${bodyAs(Animal).isDangerous()}", "false");
    }

    @Test
    public void testBodyOgnlOnString() {
        exchange.getIn().setBody("Camel");

        assertExpression("${bodyAs(String).substring(2)}", "mel");
        assertExpression("${bodyAs(String).substring(2, 4)}", "me");
        assertExpression("${bodyAs(String).length()}", 5);
        assertExpression("${bodyAs(String).toUpperCase()}", "CAMEL");
        assertExpression("${bodyAs(String).toUpperCase()}", "CAMEL");
        assertExpression("${bodyAs(String).toUpperCase().substring(2)}", "MEL");
        assertExpression("${bodyAs(String).toLowerCase().length()}", 5);
    }

    @Test
    public void testBodyOgnlOnStringWithOgnlParams() {
        exchange.getIn().setBody("Camel");
        exchange.getIn().setHeader("max", 4);
        exchange.getIn().setHeader("min", 2);

        assertExpression("${bodyAs(String).substring(${headerAs(min, int)}, ${headerAs(max, int)})}", "me");
    }

    @Test
    public void testHeaderOgnlOnStringWithOgnlParams() {
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader("name", "Camel");
        exchange.getIn().setHeader("max", 4);
        exchange.getIn().setHeader("min", 2);

        assertExpression("${headerAs(name, String).substring(${headerAs(min, int)}, ${headerAs(max, int)})}", "me");
    }

    @Test
    public void testBodyOgnlReplace() {
        exchange.getIn().setBody("Kamel is a cool Kamel");

        assertExpression("${bodyAs(String).replace(\"Kamel\", \"Camel\")}", "Camel is a cool Camel");
    }

    @Test
    public void testBodyOgnlReplaceEscapedChar() {
        exchange.getIn().setBody("foo$bar$baz");

        assertExpression("${bodyAs(String).replace(\"$\", \"-\")}", "foo-bar-baz");
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOgnlReplaceEscapedBackslashChar() {
        exchange.getIn().setBody("foo\\bar\\baz");

        assertExpression("${bodyAs(String).replace(\"\\\", \"\\\\\")}", "foo\\\\bar\\\\baz");
    }

    @Test
    public void testBodyOgnlReplaceFirst() {
        exchange.getIn().setBody("http:camel.apache.org");

        assertExpression("${bodyAs(String).replaceFirst(\"http:\", \"https:\")}", "https:camel.apache.org");
        assertExpression("${bodyAs(String).replaceFirst(\"http:\", \"\")}", "camel.apache.org");
        assertExpression("${bodyAs(String).replaceFirst(\"http:\", \" \")}", " camel.apache.org");
        assertExpression("${bodyAs(String).replaceFirst(\"http:\",    \" \")}", " camel.apache.org");
        assertExpression("${bodyAs(String).replaceFirst(\"http:\",\" \")}", " camel.apache.org");
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testBodyOgnlReplaceSingleQuoteInDouble() {
        exchange.getIn().setBody("Hello O\"Conner");

        assertExpression("${bodyAs(String).replace(\"O\"C\", \"OC\")}", "Hello OConner");
        assertExpression("${bodyAs(String).replace(\"O\"C\", \"O C\")}", "Hello O Conner");
        assertExpression("${bodyAs(String).replace(\"O\"C\", \"O-C\")}", "Hello O-Conner");
        assertExpression("${bodyAs(String).replace(\"O\"C\", \"O\"\"C\")}", "Hello O\"\"Conner");
        assertExpression("${bodyAs(String).replace(\"O\"C\", \"O\n\"C\")}", "Hello O\n\"Conner");
    }

    @Test
    public void testBodyOgnlSpaces() {
        exchange.getIn().setBody("Hello World");

        assertExpression("${bodyAs(String).compareTo('Hello World')}", 0);
        assertExpression("${bodyAs(String).compareTo(\"Hello World\")}", 0);

        assertExpression("${bodyAs(String).compareTo('Hello World')}", 0);
        assertExpression("${bodyAs(String).compareTo(${bodyAs(String)})}", 0);
        assertExpression("${bodyAs(String).compareTo('foo')}", "Hello World".compareTo("foo"));

        assertExpression("${bodyAs(String).compareTo( \"Hello World\" )}", 0);
        assertExpression("${bodyAs(String).compareTo( ${bodyAs(String)} )}", 0);
        assertExpression("${bodyAs(String).compareTo( \"foo\" )}", "Hello World".compareTo("foo"));
    }

    @Test
    public void testBodySingleQuote() {
        exchange.getIn().setBody("It's a great World");

        assertExpression("${bodyAs(String).compareTo(\"It's a great World\")}", 0);
        assertExpression("${bodyAs(String).compareTo('It\\'s a great World')}", 0);
    }

    @Test
    public void testClassSimpleName() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        exchange.getIn().setBody(tiger);

        assertExpression("${body.getClass().getSimpleName()}", "Animal");
        assertExpression("${body.getClass.getSimpleName}", "Animal");
        assertExpression("${body.class.simpleName}", "Animal");
    }

    @Test
    public void testExceptionClassSimpleName() {
        Animal tiger = new Animal("Tony the Tiger", 13);
        exchange.getIn().setBody(tiger);

        Exception cause = new IllegalArgumentException("Forced");
        exchange.setException(cause);

        assertExpression("${exception.getClass().getSimpleName()}", "IllegalArgumentException");
        assertExpression("${exception.getClass.getSimpleName}", "IllegalArgumentException");
        assertExpression("${exception.class.simpleName}", "IllegalArgumentException");
    }

    @Test
    public void testSlashBeforeHeader() {
        assertExpression("foo/${header.foo}", "foo/abc");
        assertExpression("foo\\${header.foo}", "foo\\abc");
    }

    @Test
    public void testJSonLike() {
        exchange.getIn().setBody("Something");

        assertExpression("{\"oneline\": \"${body}\"}", "{\"oneline\": \"Something\"}");
        assertExpression("{\n\"data\": \"${body}\"\n}", "{\n\"data\": \"Something\"\n}");
    }

    @Test
    public void testFunctionEnds() {
        exchange.getIn().setBody("Something");

        assertExpression("{{", "{{");
        assertExpression("}}", "}}");
        assertExpression("{{}}", "{{}}");
        assertExpression("{{foo}}", "{{foo}}");
        assertExpression("{{${body}}}", "{{Something}}");
        assertExpression("{{${body}-${body}}}", "{{Something-Something}}");
    }

    @Test
    public void testEscape() {
        exchange.getIn().setBody("Something");

        assertExpression("\\n${body}", "\nSomething");
        assertExpression("\\t${body}", "\tSomething");
        assertExpression("\\r${body}", "\rSomething");
        assertExpression("\\n\\r${body}", "\n\rSomething");
        assertExpression("\\n${body}\\n", "\nSomething\n");
        assertExpression("\\t${body}\\t", "\tSomething\t");
        assertExpression("\\r${body}\\r", "\rSomething\r");
        assertExpression("\\n\\r${body}\\n\\r", "\n\rSomething\n\r");

        assertExpression("$${body}", "$Something");
    }

    @Test
    public void testEscapeEndFunction() {
        exchange.getIn().setBody("Something");

        assertExpression("{hello\\}", "{hello}");
        assertExpression("${body}{hello\\}", "Something{hello}");
    }

    @Test
    public void testCamelContextOGNL() {
        assertExpression("${camelContext.getName()}", context.getName());
        assertExpression("${camelContext.version}", context.getVersion());
    }

    @Test
    public void testTypeConstant() {
        assertExpression("${type:org.apache.camel.Exchange.FILE_NAME}", Exchange.FILE_NAME);
        assertExpression("${type:org.apache.camel.ExchangePattern.InOut}", ExchangePattern.InOut);

        // non existing fields
        assertExpression("${type:org.apache.camel.ExchangePattern.}", null);
        assertExpression("${type:org.apache.camel.ExchangePattern.UNKNOWN}", null);
    }

    @Test
    public void testTypeConstantInnerClass() {
        assertExpression("${type:org.apache.camel.language.csimple.joor.Constants$MyInnerStuff.FOO}", 123);
        assertExpression("${type:org.apache.camel.language.csimple.joor.Constants.MyInnerStuff.FOO}", 123);
        assertExpression("${type:org.apache.camel.language.csimple.joor.Constants.BAR}", 456);
    }

    @Test
    public void testStringArrayLength() {
        exchange.getIn().setBody(new String[] { "foo", "bar" });
        assertExpression("${body[0]}", "foo");
        assertExpression("${body[1]}", "bar");
        assertExpression("${bodyAs(String[]).length}", 2);

        exchange.getIn().setBody(new String[] { "foo", "bar", "beer" });
        assertExpression("${bodyAs(String[]).length}", 3);
    }

    @Test
    public void testByteArrayLength() {
        exchange.getIn().setBody(new byte[] { 65, 66, 67 });
        assertExpression("${body[0]}", 65);
        assertExpression("${body[1]}", 66);
        assertExpression("${body[2]}", 67);
        assertExpression("${bodyAs(byte[]).length}", 3);
    }

    @Test
    public void testIntArrayLength() {
        exchange.getIn().setBody(new int[] { 1, 20, 300 });
        assertExpression("${body[0]}", 1);
        assertExpression("${body[1]}", 20);
        assertExpression("${body[2]}", 300);
        assertExpression("${bodyAs(int[]).length}", 3);
    }

    @Test
    public void testSimpleMapBoolean() {
        Map<String, Object> map = new HashMap<>();
        exchange.getIn().setBody(map);

        map.put("isCredit", true);
        assertPredicate("${body[isCredit]} == true", true);
        assertPredicate("${body[isCredit]} == false", false);
        assertPredicate("${body[\"isCredit\"]} == true", true);
        assertPredicate("${body[\"isCredit\"]} == false", false);

        // wrong case
        assertPredicate("${body[\"IsCredit\"]} == true", false);

        map.put("isCredit", false);
        assertPredicate("${body[isCredit]} == true", false);
        assertPredicate("${body[isCredit]} == false", true);
        assertPredicate("${body[\"isCredit\"]} == true", false);
        assertPredicate("${body[\"isCredit\"]} == false", true);
    }

    @Test
    public void testSimpleRegexp() {
        exchange.getIn().setBody("12345678");
        assertPredicate("${body} regex '\\d+'", true);
        assertPredicate("${body} regex '\\w{1,4}'", false);

        exchange.getIn().setBody("tel:+97444549697");
        assertPredicate("${body} regex \"^(tel:\\+)(974)(44)(\\d+)|^(974)(44)(\\d+)\"", true);

        exchange.getIn().setBody("97444549697");
        assertPredicate("${body} regex \"^(tel:\\+)(974)(44)(\\d+)|^(974)(44)(\\d+)\"", true);

        exchange.getIn().setBody("tel:+87444549697");
        assertPredicate("${body} regex \"^(tel:\\+)(974)(44)(\\d+)|^(974)(44)(\\d+)\"", false);

        exchange.getIn().setBody("87444549697");
        assertPredicate("${body} regex \"^(tel:\\+)(974)(44)(\\d+)|^(974)(44)(\\d+)\"", false);
    }

    @Test
    public void testCollateEven() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        data.add("F");
        exchange.getIn().setBody(data);

        Iterator it = (Iterator) evaluateExpression("${collate(3)}", null);
        List chunk = (List) it.next();
        List chunk2 = (List) it.next();
        assertFalse(it.hasNext());

        assertEquals(3, chunk.size());
        assertEquals(3, chunk2.size());

        assertEquals("A", chunk.get(0));
        assertEquals("B", chunk.get(1));
        assertEquals("C", chunk.get(2));
        assertEquals("D", chunk2.get(0));
        assertEquals("E", chunk2.get(1));
        assertEquals("F", chunk2.get(2));
    }

    @Test
    public void testCollateOdd() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        data.add("D");
        data.add("E");
        data.add("F");
        data.add("G");
        exchange.getIn().setBody(data);

        Iterator it = (Iterator) evaluateExpression("${collate(3)}", null);
        List chunk = (List) it.next();
        List chunk2 = (List) it.next();
        List chunk3 = (List) it.next();
        assertFalse(it.hasNext());

        assertEquals(3, chunk.size());
        assertEquals(3, chunk2.size());
        assertEquals(1, chunk3.size());

        assertEquals("A", chunk.get(0));
        assertEquals("B", chunk.get(1));
        assertEquals("C", chunk.get(2));
        assertEquals("D", chunk2.get(0));
        assertEquals("E", chunk2.get(1));
        assertEquals("F", chunk2.get(2));
        assertEquals("G", chunk3.get(0));
    }

    @Test
    public void testRandomExpression() {
        int min = 1;
        int max = 10;
        int iterations = 30;
        int i = 0;
        for (i = 0; i < iterations; i++) {
            Expression expression = context.resolveLanguage("csimple").createExpression("${random(1,10)}");
            assertTrue(
                    min <= expression.evaluate(exchange, Integer.class) && expression.evaluate(exchange, Integer.class) < max);
        }
        for (i = 0; i < iterations; i++) {
            Expression expression = context.resolveLanguage("csimple").createExpression("${random(10)}");
            assertTrue(0 <= expression.evaluate(exchange, Integer.class) && expression.evaluate(exchange, Integer.class) < max);
        }
        Expression expression = context.resolveLanguage("csimple").createExpression("${random(1, 10)}");
        assertTrue(min <= expression.evaluate(exchange, Integer.class) && expression.evaluate(exchange, Integer.class) < max);

        Expression expression1 = context.resolveLanguage("csimple").createExpression("${random( 10)}");
        assertTrue(0 <= expression1.evaluate(exchange, Integer.class) && expression1.evaluate(exchange, Integer.class) < max);

        try {
            assertExpression("${random(10,21,30)}", null);
            fail("Should have thrown exception");
        } catch (JoorCSimpleCompilationException e) {
            // expected
        }
        try {
            assertExpression("${random()}", null);
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertEquals("Valid syntax: ${random(min,max)} or ${random(max)} was: random()", e.getCause().getMessage());
        }

        exchange.getIn().setHeader("max", 20);
        Expression expression3 = context.resolveLanguage("csimple").createExpression("${random(10,${header.max})}");
        int num = expression3.evaluate(exchange, Integer.class);
        assertTrue(num >= 0 && num < 20, "Should be 10..20");
    }

    @Test
    public void testListRemoveByInstance() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        exchange.getIn().setBody(data);

        assertEquals(2, data.size());

        Expression expression = context.resolveLanguage("csimple").createExpression("${bodyAs(List).remove(\"A\")}");
        expression.evaluate(exchange, Object.class);

        assertEquals(1, data.size());
        assertEquals("B", data.get(0));
    }

    @Test
    public void testListRemoveIndex() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        exchange.getIn().setBody(data);

        assertEquals(2, data.size());

        Expression expression = context.resolveLanguage("csimple").createExpression("${bodyAs(List).remove(0)}");
        expression.evaluate(exchange, Object.class);

        assertEquals(1, data.size());
        assertEquals("B", data.get(0));
    }

    @Test
    public void testBodyAsOneLine() {
        exchange.getIn().setBody("Hello" + System.lineSeparator() + "Great" + System.lineSeparator() + "World");
        assertExpression("${bodyOneLine}", "HelloGreatWorld");
        assertExpression("Hi ${bodyOneLine}", "Hi HelloGreatWorld");
        assertExpression("Hi ${bodyOneLine} Again", "Hi HelloGreatWorld Again");
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testListIndexByNestedFunction() {
        List<String> alist = new ArrayList<>();
        alist.add("1");
        alist.add("99");
        exchange.getIn().setHeader("ITEMS", alist);
        exchange.getIn().setHeader("TOTAL_LOOPS", alist.size());

        String exp = "${header.ITEMS[${exchangeProperty.CamelLoopIndex}]}";

        exchange.setProperty(Exchange.LOOP_INDEX, 0);
        assertExpression(exp, "1");
        exchange.setProperty(Exchange.LOOP_INDEX, 1);
        assertExpression(exp, "99");
    }

    @Disabled("Investigation pending - see CAMEL-19681")
    @Test
    public void testNestedFunction() {
        exchange.getMessage().setBody("Tony");
        exchange.getMessage().setHeader("counter", 3);

        assertExpression("Hello ${bean:greeter(${body}, ${header.counter})}", "Hello TonyTonyTony");
    }

    @Override
    protected String getLanguageName() {
        return "csimple";
    }

    protected void assertExpressionResultInstanceOf(String expressionText, Class<?> expectedType) {
        Language language = assertResolveLanguage(getLanguageName());
        Expression expression = language.createExpression(expressionText);
        assertNotNull(expectedType, "Cannot assert type when no type is provided");
        assertNotNull(expression, "No Expression could be created for text: " + expressionText + " language: " + language);
        Object answer = expression.evaluate(exchange, Object.class);
        assertIsInstanceOf(expectedType, answer);
    }

    /**
     * Evaluates the expression
     */
    protected Object evaluateExpression(String expressionText, String expectedValue) {
        Language language = assertResolveLanguage(getLanguageName());

        Expression expression = language.createExpression(expressionText);
        assertNotNull(expression, "No Expression could be created for text: " + expressionText + " language: " + language);

        Object value;
        if (expectedValue != null) {
            value = expression.evaluate(exchange, expectedValue.getClass());
        } else {
            value = expression.evaluate(exchange, Object.class);
        }
        return value;
    }

    public static final class Animal {
        private String name;
        private int age;
        private Animal friend;

        private Animal(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public Animal getFriend() {
            return friend;
        }

        public void setFriend(Animal friend) {
            this.friend = friend;
        }

        public boolean isDangerous() {
            return name.contains("Tiger");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class Greeter {

        public String greetMe(String name, int times) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                sb.append(name);
            }
            return sb.toString();
        }

    }

    public static final class Order {
        private List<OrderLine> lines;

        public Order(List<OrderLine> lines) {
            this.lines = lines;
        }

        public List<OrderLine> getLines() {
            return lines;
        }

        public void setLines(List<OrderLine> lines) {
            this.lines = lines;
        }
    }

    public static final class OrderLine {
        private int id;
        private String name;

        public OrderLine(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class MyClass {
        public Object[] getMyArray() {
            return new Object[] { "Hallo", "World", "!" };
        }
    }

}
