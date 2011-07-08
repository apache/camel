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
package org.apache.camel.language;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.language.bean.RuntimeBeanExpressionException;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.spi.Language;

/**
 * @version 
 */
public class SimpleTest extends LanguageTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myAnimal", new Animal("Donkey", 17));
        return jndi;
    }

    public void testRefExpression() throws Exception {
        assertExpressionResultInstanceOf("ref:myAnimal", Animal.class);
        assertExpressionResultInstanceOf("${ref:myAnimal}", Animal.class);
        
        assertExpression("ref:myAnimal", "Donkey");
        assertExpression("${ref:myAnimal}", "Donkey");
        assertExpression("ref:unknown", null);
        assertExpression("${ref:unknown}", null);
        assertExpression("Hello ${ref:myAnimal}", "Hello Donkey");
        assertExpression("Hello ${ref:unknown}", "Hello ");
    }

    public void testConstantExpression() throws Exception {
        assertExpression("Hello World", "Hello World");
    }

    public void testBodyExpression() throws Exception {
        Expression exp = SimpleLanguage.simple("${body}");
        assertNotNull(exp);
    }

    public void testBodyExpressionUsingAlternativeStartToken() throws Exception {
        Expression exp = SimpleLanguage.simple("$simple{body}");
        assertNotNull(exp);
    }

    public void testBodyExpressionNotStringType() throws Exception {
        exchange.getIn().setBody(123);
        Expression exp = SimpleLanguage.simple("${body}");
        assertNotNull(exp);
        Object val = exp.evaluate(exchange, Object.class);
        assertIsInstanceOf(Integer.class, val);
        assertEquals(123, val);
    }

    public void testSimpleExpressions() throws Exception {
        assertExpression("exchangeId", exchange.getExchangeId());
        assertExpression("id", exchange.getIn().getMessageId());
        assertExpression("body", "<hello id='m123'>world!</hello>");
        assertExpression("in.body", "<hello id='m123'>world!</hello>");
        assertExpression("in.header.foo", "abc");
        assertExpression("in.headers.foo", "abc");
        assertExpression("header.foo", "abc");
        assertExpression("headers.foo", "abc");
    }

    public void testSimpleThreadName() throws Exception {
        String name = Thread.currentThread().getName();
        assertExpression("threadName", name);
        assertExpression("The name is ${threadName}", "The name is " + name);
    }

    public void testSimpleOutExpressions() throws Exception {
        exchange.getOut().setBody("Bye World");
        exchange.getOut().setHeader("quote", "Camel rocks");
        assertExpression("out.body", "Bye World");
        assertExpression("out.header.quote", "Camel rocks");
        assertExpression("out.headers.quote", "Camel rocks");
    }

    public void testSimplePropertyExpressions() throws Exception {
        exchange.setProperty("medal", "gold");
        assertExpression("property.medal", "gold");
    }

    public void testSimpleSystemPropertyExpressions() throws Exception {
        System.setProperty("who", "I was here");
        assertExpression("sys.who", "I was here");
    }

    public void testSimpleSystemEnvironmentExpressions() throws Exception {
        String path = System.getenv("PATH");
        if (path != null) {
            assertExpression("sysenv.PATH", path);
        }
    }

    public void testDateExpressions() throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertExpression("date:header.birthday:yyyyMMdd", "19740420");

        try {
            assertExpression("date:yyyyMMdd", "19740420");
            fail("Should thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            // expected
        }
    }

    public void testDateAndTimeExpressions() throws Exception {
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(1974, Calendar.APRIL, 20, 8, 55, 47);
        cal.set(Calendar.MILLISECOND, 123);
        exchange.getIn().setHeader("birthday", cal.getTime());

        assertExpression("date:header.birthday:yyyy-MM-dd'T'HH:mm:ss:SSS", "1974-04-20T08:55:47:123");
    }

    public void testLanguagesInContext() throws Exception {
        // evaluate so we know there is 1 language in the context
        assertExpression("id", exchange.getIn().getMessageId());

        assertEquals(1, context.getLanguageNames().size());
        assertEquals("simple", context.getLanguageNames().get(0));
    }

    public void testComplexExpressions() throws Exception {
        assertExpression("hey ${in.header.foo}", "hey abc");
        assertExpression("hey ${in.header.foo}!", "hey abc!");
        assertExpression("hey ${in.header.foo}-${in.header.foo}!", "hey abc-abc!");
        assertExpression("hey ${in.header.foo}${in.header.foo}", "hey abcabc");
        assertExpression("${in.header.foo}${in.header.foo}", "abcabc");
        assertExpression("${in.header.foo}", "abc");
        assertExpression("${in.header.foo}!", "abc!");
    }

    public void testComplexExpressionsUsingAlternativeStartToken() throws Exception {
        assertExpression("hey $simple{in.header.foo}", "hey abc");
        assertExpression("hey $simple{in.header.foo}!", "hey abc!");
        assertExpression("hey $simple{in.header.foo}-$simple{in.header.foo}!", "hey abc-abc!");
        assertExpression("hey $simple{in.header.foo}$simple{in.header.foo}", "hey abcabc");
        assertExpression("$simple{in.header.foo}$simple{in.header.foo}", "abcabc");
        assertExpression("$simple{in.header.foo}", "abc");
        assertExpression("$simple{in.header.foo}!", "abc!");
    }

    public void testInvalidComplexExpression() throws Exception {
        try {
            assertExpression("hey ${foo", "bad expression!");
            fail("Should have thrown an exception!");
        } catch (IllegalArgumentException e) {
            log.debug("Caught expected exception: " + e, e);
        }
    }

    public void testPredicates() throws Exception {
        assertPredicate("body");
        assertPredicate("header.foo");
        assertPredicate("header.madeUpHeader", false);
    }

    public void testExceptionMessage() throws Exception {
        exchange.setException(new IllegalArgumentException("Just testing"));
        assertExpression("exception.message", "Just testing");
        assertExpression("Hello ${exception.message} World", "Hello Just testing World");
    }

    public void testExceptionStacktrace() throws Exception {
        exchange.setException(new IllegalArgumentException("Just testing"));

        String out = SimpleLanguage.simple("exception.stacktrace").evaluate(exchange, String.class);
        assertNotNull(out);
        assertTrue(out.startsWith("java.lang.IllegalArgumentException: Just testing"));
        assertTrue(out.contains("at org.apache.camel.language."));
    }

    public void testException() throws Exception {
        exchange.setException(new IllegalArgumentException("Just testing"));

        Exception out = SimpleLanguage.simple("exception").evaluate(exchange, Exception.class);
        assertNotNull(out);
        assertIsInstanceOf(IllegalArgumentException.class, out);
        assertEquals("Just testing", out.getMessage());
    }

    public void testBodyAs() throws Exception {
        assertExpression("${bodyAs(String)}", "<hello id='m123'>world!</hello>");
        assertExpression("${bodyAs('String')}", "<hello id='m123'>world!</hello>");

        exchange.getIn().setBody(null);
        assertExpression("${bodyAs('String')}", null);

        exchange.getIn().setBody(456);
        assertExpression("${bodyAs(Integer)}", 456);
        assertExpression("${bodyAs(int)}", 456);
        assertExpression("${bodyAs('int')}", 456);

        try {
            assertExpression("${bodyAs(XXX)}", 456);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(ClassNotFoundException.class, e.getCause());
        }
    }

    public void testMandatoryBodyAs() throws Exception {
        assertExpression("${mandatoryBodyAs(String)}", "<hello id='m123'>world!</hello>");
        assertExpression("${mandatoryBodyAs('String')}", "<hello id='m123'>world!</hello>");

        exchange.getIn().setBody(null);
        try {
            assertExpression("${mandatoryBodyAs('String')}", "");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(InvalidPayloadException.class, e.getCause());
        }

        exchange.getIn().setBody(456);
        assertExpression("${mandatoryBodyAs(Integer)}", 456);
        assertExpression("${mandatoryBodyAs(int)}", 456);
        assertExpression("${mandatoryBodyAs('int')}", 456);

        try {
            assertExpression("${mandatoryBodyAs(XXX)}", 456);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(ClassNotFoundException.class, e.getCause());
        }
    }

    public void testHeaderEmptyBody() throws Exception {
        // set an empty body
        exchange.getIn().setBody(null);

        assertExpression("header.foo", "abc");
        assertExpression("headers.foo", "abc");
        assertExpression("in.header.foo", "abc");
        assertExpression("in.headers.foo", "abc");
        assertExpression("${header.foo}", "abc");
        assertExpression("${headers.foo}", "abc");
        assertExpression("${in.header.foo}", "abc");
        assertExpression("${in.headers.foo}", "abc");
    }

    public void testIsInstanceOfEmptyBody() throws Exception {
        // set an empty body
        exchange.getIn().setBody(null);

        try {
            assertExpression("${body} is null", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Syntax error in is operator: ${body} is null cannot be null. It must be a class type.", e.getMessage());
        }
    }

    public void testHeaderAs() throws Exception {
        assertExpression("${headerAs(foo,String)}", "abc");

        assertExpression("${headerAs(bar,int)}", 123);
        assertExpression("${headerAs(bar, int)}", 123);
        assertExpression("${headerAs('bar', int)}", 123);
        assertExpression("${headerAs('bar','int')}", 123);
        assertExpression("${headerAs('bar','Integer')}", 123);
        assertExpression("${headerAs('bar',\"int\")}", 123);
        assertExpression("${headerAs(bar,String)}", "123");
        
        assertExpression("${headerAs(unknown,String)}", null);

        try {
            assertExpression("${headerAs(unknown String)}", null);
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertEquals("Illegal syntax: Valid syntax: ${headerAs(key, type)} was: headerAs(unknown String)", e.getMessage());
        }

        try {
            assertExpression("${headerAs(bar,XXX)}", 123);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(ClassNotFoundException.class, e.getCause());
        }
    }

    public void testIllegalSyntax() throws Exception {
        try {
            assertExpression("hey ${xxx} how are you?", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertEquals("Illegal syntax: xxx", e.getMessage());
        }

        try {
            assertExpression("${xxx}", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertEquals("Illegal syntax: xxx", e.getMessage());
        }

        try {
            assertExpression("${bodyAs(xxx}", "");
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertEquals("Illegal syntax: Valid syntax: ${bodyAs(type)} was: bodyAs(xxx", e.getMessage());
        }
    }

    public void testOGNLHeaderList() throws Exception {
        List<String> lines = new ArrayList<String>();
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
            assertEquals("Index: 2, Size: 2", cause.getMessage());
        }
        assertExpression("${header.unknown[cool]}", null);
    }

    public void testOGNLHeaderLinesList() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        exchange.getIn().setHeader("wicket", lines);

        assertExpression("${header.wicket[0].getId}", 123);
        assertExpression("${header.wicket[1].getName}", "ActiveMQ in Action");
        try {
            assertExpression("${header.wicket[2]}", "");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertEquals("Index: 2, Size: 2", cause.getMessage());
        }
        assertExpression("${header.unknown[cool]}", null);
    }

    public void testOGNLHeaderMap() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
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

    public void testOGNLHeaderMapWithDot() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("this.code","This code");
        exchange.getIn().setHeader("wicket", map);

        assertExpression("${header.wicket[this.code]}","This code");
    }

    public void testOGNLHeaderMapNotMap() throws Exception {
        try {
            assertExpression("${header.foo[bar]}", null);
            fail("Should have thrown an exception");
        } catch (RuntimeBeanExpressionException e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertEquals("Key: bar not found in bean: abc of type: java.lang.String using OGNL path [[bar]]", cause.getMessage());
        }
    }

    public void testOGNLHeaderMapIllegalSyntax() throws Exception {
        try {
            assertExpression("${header.foo[bar}", null);
            fail("Should have thrown an exception");
        } catch (ExpressionIllegalSyntaxException e) {
            assertEquals("Illegal syntax: Valid syntax: ${header.name[key]} was: header.foo[bar", e.getMessage());
        }
    }

    public void testBodyOGNLAsMap() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", "Camel");
        map.put("bar", 6);
        exchange.getIn().setBody(map);

        assertExpression("${in.body[foo]}", "Camel");
        assertExpression("${in.body[bar]}", 6);
    }

    public void testBodyOGNLAsMapWithDot() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo.bar", "Camel");
        exchange.getIn().setBody(map);

        assertExpression("${in.body[foo.bar]}", "Camel");
    }
    
    public void testBodyOGNLAsMapShorthand() throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("foo", "Camel");
        map.put("bar", 6);
        exchange.getIn().setBody(map);

        assertExpression("${body[foo]}", "Camel");
        assertExpression("${body[bar]}", 6);
    }

    public void testBodyOGNLSimple() throws Exception {
        Animal camel = new Animal("Camel", 6);
        exchange.getIn().setBody(camel);

        assertExpression("${in.body.getName}", "Camel");
        assertExpression("${in.body.getAge}", 6);
    }

    public void testExceptionOGNLSimple() throws Exception {
        exchange.getIn().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID, "myPolicy");
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, new CamelAuthorizationException("The camel authorization exception", exchange));

        assertExpression("${exception.getPolicyId}", "myPolicy");
    }

    public void testBodyOGNLSimpleShorthand() throws Exception {
        Animal camel = new Animal("Camel", 6);
        exchange.getIn().setBody(camel);

        assertExpression("${in.body.name}", "Camel");
        assertExpression("${in.body.age}", 6);
    }

    public void testBodyOGNLSimpleOperator() throws Exception {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${in.body.getName} contains 'Camel'", true);
        assertExpression("${in.body.getName} contains 'Tiger'", false);
        assertExpression("${in.body.getAge} < 10", true);
        assertExpression("${in.body.getAge} > 10", false);
        assertExpression("${in.body.getAge} <= '6'", true);
        assertExpression("${in.body.getAge} > '6'", false);

        assertExpression("${in.body.getAge} < ${body.getFriend.getAge}'", true);
        assertExpression("${in.body.getFriend.isDangerous} == true", true);
    }

    public void testBodyOGNLSimpleOperatorShorthand() throws Exception {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${in.body.name} contains 'Camel'", true);
        assertExpression("${in.body.name} contains 'Tiger'", false);
        assertExpression("${in.body.age} < 10", true);
        assertExpression("${in.body.age} > 10", false);
        assertExpression("${in.body.age} <= '6'", true);
        assertExpression("${in.body.age} > '6'", false);

        assertExpression("${in.body.age} < ${body.friend.age}'", true);
        assertExpression("${in.body.friend.dangerous} == true", true);
    }

    public void testBodyOGNLNested() throws Exception {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${in.body.getName}", "Camel");
        assertExpression("${in.body.getAge}", 6);

        assertExpression("${in.body.getFriend.getName}", "Tony the Tiger");
        assertExpression("${in.body.getFriend.getAge}", "13");
    }

    public void testBodyOGNLNestedShorthand() throws Exception {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${in.body.name}", "Camel");
        assertExpression("${in.body.age}", 6);

        assertExpression("${in.body.friend.name}", "Tony the Tiger");
        assertExpression("${in.body.friend.age}", "13");
    }

    public void testBodyOGNLOrderList() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);
        
        exchange.getIn().setBody(order);

        assertExpression("${in.body.getLines[0].getId}", 123);
        assertExpression("${in.body.getLines[0].getName}", "Camel in Action");

        assertExpression("${in.body.getLines[1].getId}", 456);
        assertExpression("${in.body.getLines[1].getName}", "ActiveMQ in Action");

        assertExpression("${in.body.getLines[last].getId}", 456);
        assertExpression("${in.body.getLines[last].getName}", "ActiveMQ in Action");

        assertExpression("${in.body.getLines[last-1].getId}", 123);
        assertExpression("${in.body.getLines[last-1].getName}", "Camel in Action");
    }

    public void testBodyOGNLOrderListShorthand() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        assertExpression("${in.body.lines[0].id}", 123);
        assertExpression("${in.body.lines[0].name}", "Camel in Action");

        assertExpression("${in.body.lines[1].id}", 456);
        assertExpression("${in.body.lines[1].name}", "ActiveMQ in Action");

        assertExpression("${in.body.lines[last].id}", 456);
        assertExpression("${in.body.lines[last].name}", "ActiveMQ in Action");

        assertExpression("${in.body.lines[last-1].id}", 123);
        assertExpression("${in.body.lines[last-1].name}", "Camel in Action");
    }

    public void testBodyOGNLList() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));

        exchange.getIn().setBody(lines);

        assertExpression("${in.body[0].getId}", 123);
        assertExpression("${in.body[0].getName}", "Camel in Action");

        assertExpression("${in.body[1].getId}", 456);
        assertExpression("${in.body[1].getName}", "ActiveMQ in Action");
    }

    public void testBodyOGNLListShorthand() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));

        exchange.getIn().setBody(lines);

        assertExpression("${in.body[0].id}", 123);
        assertExpression("${in.body[0].name}", "Camel in Action");

        assertExpression("${in.body[1].id}", 456);
        assertExpression("${in.body[1].name}", "ActiveMQ in Action");
    }

    public void testBodyOGNLArray() throws Exception {
        OrderLine[] lines = new OrderLine[2];
        lines[0] = new OrderLine(123, "Camel in Action");
        lines[1] = new OrderLine(456, "ActiveMQ in Action");

        exchange.getIn().setBody(lines);

        assertExpression("${in.body[0].getId}", 123);
        assertExpression("${in.body[0].getName}", "Camel in Action");

        assertExpression("${in.body[1].getId}", 456);
        assertExpression("${in.body[1].getName}", "ActiveMQ in Action");
    }

    public void testBodyOGNLArrayShorthand() throws Exception {
        OrderLine[] lines = new OrderLine[2];
        lines[0] = new OrderLine(123, "Camel in Action");
        lines[1] = new OrderLine(456, "ActiveMQ in Action");

        exchange.getIn().setBody(lines);

        assertExpression("${in.body[0].id}", 123);
        assertExpression("${in.body[0].name}", "Camel in Action");

        assertExpression("${in.body[1].id}", 456);
        assertExpression("${in.body[1].name}", "ActiveMQ in Action");
    }

    public void testBodyOGNLOrderListOutOfBounds() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${in.body.getLines[3].getId}", 123);
            fail("Should have thrown an exception");
        } catch (RuntimeBeanExpressionException e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Index: 3, Size: 2 out of bounds with List from bean"));
        }

        try {
            assertExpression("${in.body.getLines[last-2].getId}", 123);
            fail("Should have thrown an exception");
        } catch (RuntimeBeanExpressionException e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Index: -1, Size: 2 out of bounds with List from bean"));
        }

        try {
            assertExpression("${in.body.getLines[last - XXX].getId}", 123);
            fail("Should have thrown an exception");
        } catch (RuntimeBeanExpressionException e) {
            ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class, e.getCause());
            assertEquals("last - XXX", cause.getExpression());
        }
    }

    public void testBodyOGNLOrderListOutOfBoundsShorthand() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${in.body.lines[3].id}", 123);
            fail("Should have thrown an exception");
        } catch (RuntimeBeanExpressionException e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Index: 3, Size: 2 out of bounds with List from bean"));
        }

        try {
            assertExpression("${in.body.lines[last - 2].id}", 123);
            fail("Should have thrown an exception");
        } catch (RuntimeBeanExpressionException e) {
            IndexOutOfBoundsException cause = assertIsInstanceOf(IndexOutOfBoundsException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Index: -1, Size: 2 out of bounds with List from bean"));
        }

        try {
            assertExpression("${in.body.lines[last - XXX].id}", 123);
            fail("Should have thrown an exception");
        } catch (RuntimeBeanExpressionException e) {
            ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class, e.getCause());
            assertEquals("last - XXX", cause.getExpression());
        }
    }

    public void testBodyOGNLOrderListOutOfBoundsWithNullSafe() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        assertExpression("${in.body?.getLines[3].getId}", null);
    }

    public void testBodyOGNLOrderListOutOfBoundsWithNullSafeShorthand() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        assertExpression("${in.body?.lines[3].id}", null);
    }

    public void testBodyOGNLOrderListNoMethodNameWithNullSafe() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${in.body.getLines[0]?.getRating}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            MethodNotFoundException cause = assertIsInstanceOf(MethodNotFoundException.class, e.getCause().getCause());
            assertEquals("getRating", cause.getMethodName());
        }
    }

    public void testBodyOGNLOrderListNoMethodNameWithNullSafeShorthand() throws Exception {
        List<OrderLine> lines = new ArrayList<OrderLine>();
        lines.add(new OrderLine(123, "Camel in Action"));
        lines.add(new OrderLine(456, "ActiveMQ in Action"));
        Order order = new Order(lines);

        exchange.getIn().setBody(order);

        try {
            assertExpression("${in.body.lines[0]?.rating}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            MethodNotFoundException cause = assertIsInstanceOf(MethodNotFoundException.class, e.getCause().getCause());
            assertEquals("rating", cause.getMethodName());
        }
    }

    public void testBodyOGNLNullSafeToAvoidNPE() throws Exception {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${in.body.getName}", "Camel");
        assertExpression("${in.body.getAge}", 6);

        assertExpression("${in.body.getFriend.getName}", "Tony the Tiger");
        assertExpression("${in.body.getFriend.getAge}", "13");

        // using null safe to avoid the NPE
        assertExpression("${in.body.getFriend?.getFriend.getName}", null);
        try {
            // without null safe we get an NPE
            assertExpression("${in.body.getFriend.getFriend.getName}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            assertEquals("Failed to invoke method: .getFriend.getFriend.getName on null due to: java.lang.NullPointerException", e.getMessage());
            assertIsInstanceOf(NullPointerException.class, e.getCause());
        }
    }

    public void testBodyOGNLNullSafeToAvoidNPEShorthand() throws Exception {
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal camel = new Animal("Camel", 6);
        camel.setFriend(tiger);

        exchange.getIn().setBody(camel);

        assertExpression("${in.body.name}", "Camel");
        assertExpression("${in.body.age}", 6);

        // just to mix it a bit
        assertExpression("${in.body.friend.getName}", "Tony the Tiger");
        assertExpression("${in.body.getFriend.age}", "13");

        // using null safe to avoid the NPE
        assertExpression("${in.body.friend?.friend.name}", null);
        try {
            // without null safe we get an NPE
            assertExpression("${in.body.friend.friend.name}", "");
            fail("Should have thrown exception");
        } catch (RuntimeBeanExpressionException e) {
            assertEquals("Failed to invoke method: .friend.friend.name on null due to: java.lang.NullPointerException", e.getMessage());
            assertIsInstanceOf(NullPointerException.class, e.getCause());
        }
    }

    public void testBodyOGNLReentrant() throws Exception {
        Animal camel = new Animal("Camel", 6);
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal elephant = new Animal("Big Ella", 48);

        camel.setFriend(tiger);
        tiger.setFriend(elephant);
        elephant.setFriend(camel);

        exchange.getIn().setBody(camel);

        assertExpression("${body.getFriend.getFriend.getFriend.getName}", "Camel");
        assertExpression("${body.getFriend.getFriend.getFriend.getFriend.getName}", "Tony the Tiger");
        assertExpression("${body.getFriend.getFriend.getFriend.getFriend.getFriend.getName}", "Big Ella");
    }

    public void testBodyOGNLReentrantShorthand() throws Exception {
        Animal camel = new Animal("Camel", 6);
        Animal tiger = new Animal("Tony the Tiger", 13);
        Animal elephant = new Animal("Big Ella", 48);

        camel.setFriend(tiger);
        tiger.setFriend(elephant);
        elephant.setFriend(camel);

        exchange.getIn().setBody(camel);

        assertExpression("${body.friend.friend.friend.name}", "Camel");
        assertExpression("${body.friend.friend.friend.friend.name}", "Tony the Tiger");
        assertExpression("${body.friend.friend.friend.friend.friend.name}", "Big Ella");
    }

    public void testBodyOGNLBoolean() throws Exception {
        Animal tiger = new Animal("Tony the Tiger", 13);
        exchange.getIn().setBody(tiger);
        
        assertExpression("${body.isDangerous}", "true");
        assertExpression("${body.dangerous}", "true");

        Animal camel = new Animal("Camel", 6);
        exchange.getIn().setBody(camel);

        assertExpression("${body.isDangerous}", "false");
        assertExpression("${body.dangerous}", "false");
    }

    protected String getLanguageName() {
        return "simple";
    }

    protected void assertExpressionResultInstanceOf(String expressionText, Class<?> expectedType) {
        // TODO [hz]: we should refactor TestSupport.assertExpression(Expression, Exchange, Object)
        // into 2 methods, a helper that returns the value and use that helper in assertExpression
        // Then use the helper here to get the value and move this method to LanguageTestSupport
        Language language = assertResolveLanguage(getLanguageName());
        Expression expression = language.createExpression(expressionText);
        assertNotNull("Cannot assert type when no type is provided", expectedType);
        assertNotNull("No Expression could be created for text: " + expressionText + " language: " + language, expression);
        Object answer = expression.evaluate(exchange, Object.class);
        assertIsInstanceOf(Animal.class, answer);
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
}
