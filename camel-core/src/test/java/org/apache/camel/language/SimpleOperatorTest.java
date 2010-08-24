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

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version $Revision$
 */
public class SimpleOperatorTest extends LanguageTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("generator", new MyFileNameGenerator());
        return jndi;
    }

    public void testValueWithSpace() throws Exception {
        exchange.getIn().setBody("Hello Big World");
        assertExpression("${in.body} == 'Hello Big World'", true);
    }

    public void testAnd() throws Exception {
        assertExpression("${in.header.foo} == abc and ${in.header.bar} == 123", true);
        assertExpression("${in.header.foo} == abc and ${in.header.bar} == 444", false);
        assertExpression("${in.header.foo} == def and ${in.header.bar} == 123", false);
        assertExpression("${in.header.foo} == def and ${in.header.bar} == 444", false);

        assertExpression("${in.header.foo} == abc and ${in.header.bar} > 100", true);
        assertExpression("${in.header.foo} == abc and ${in.header.bar} < 200", true);
    }

    public void testTwoAnd() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertExpression("${in.header.foo} == abc and ${in.header.bar} == 123 and ${body} == 'Hello World'", true);
        assertExpression("${in.header.foo} == 'abc' and ${in.header.bar} == 123 and ${body} == 'Hello World'", true);
        assertExpression("${in.header.foo} == abc and ${in.header.bar} == 123 and ${body} == 'Bye World'", false);
        assertExpression("${in.header.foo} == 'abc' and ${in.header.bar} == 123 and ${body} == 'Bye World'", false);
    }

    public void testThreeAnd() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertExpression("${in.header.foo} == abc and ${in.header.bar} == 123 and ${body} == 'Hello World' and ${in.header.xx}} == null", true);
        assertExpression("${in.header.foo} == 'abc' and ${in.header.bar} == 123 and ${body} == 'Hello World' and ${in.header.xx}} == null", true);
    }

    public void testTwoOr() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertExpression("${in.header.foo} == abc or ${in.header.bar} == 44 or ${body} == 'Bye World'", true);
        assertExpression("${in.header.foo} == 'abc' or ${in.header.bar} == 44 or ${body} == 'Bye World'", true);
        assertExpression("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Bye World'", false);
        assertExpression("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Bye World'", false);
        assertExpression("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Hello World'", true);
        assertExpression("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Hello World'", true);
        assertExpression("${in.header.foo} == xxx or ${in.header.bar} == 123 or ${body} == 'Bye World'", true);
        assertExpression("${in.header.foo} == 'xxx' or ${in.header.bar} == 123 or ${body} == 'Bye World'", true);
    }

    public void testThreeOr() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertExpression("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'World'", true);
        assertExpression("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'World'", true);
        assertExpression("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", false);
        assertExpression("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", false);
        assertExpression("${in.header.foo} == abc or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertExpression("${in.header.foo} == 'abc' or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertExpression("${in.header.foo} == xxx or ${in.header.bar} == 123 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertExpression("${in.header.foo} == 'xxx' or ${in.header.bar} == 123 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertExpression("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Hello World' or ${body} contains 'Moon'", true);
        assertExpression("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Hello World' or ${body} contains 'Moon'", true);
    }

    public void testAndWithQuotation() throws Exception {
        assertExpression("${in.header.foo} == 'abc' and ${in.header.bar} == '123'", true);
        assertExpression("${in.header.foo} == 'abc' and ${in.header.bar} == '444'", false);
        assertExpression("${in.header.foo} == 'def' and ${in.header.bar} == '123'", false);
        assertExpression("${in.header.foo} == 'def' and ${in.header.bar} == '444'", false);

        assertExpression("${in.header.foo} == 'abc' and ${in.header.bar} > '100'", true);
        assertExpression("${in.header.foo} == 'abc' and ${in.header.bar} < '200'", true);
    }

    public void testOr() throws Exception {
        assertExpression("${in.header.foo} == abc or ${in.header.bar} == 123", true);
        assertExpression("${in.header.foo} == abc or ${in.header.bar} == 444", true);
        assertExpression("${in.header.foo} == def or ${in.header.bar} == 123", true);
        assertExpression("${in.header.foo} == def or ${in.header.bar} == 444", false);

        assertExpression("${in.header.foo} == abc or ${in.header.bar} < 100", true);
        assertExpression("${in.header.foo} == abc or ${in.header.bar} < 200", true);
        assertExpression("${in.header.foo} == def or ${in.header.bar} < 200", true);
        assertExpression("${in.header.foo} == def or ${in.header.bar} < 100", false);
    }

    public void testOrWithQuotation() throws Exception {
        assertExpression("${in.header.foo} == 'abc' or ${in.header.bar} == '123'", true);
        assertExpression("${in.header.foo} == 'abc' or ${in.header.bar} == '444'", true);
        assertExpression("${in.header.foo} == 'def' or ${in.header.bar} == '123'", true);
        assertExpression("${in.header.foo} == 'def' or ${in.header.bar} == '444'", false);

        assertExpression("${in.header.foo} == 'abc' or ${in.header.bar} < '100'", true);
        assertExpression("${in.header.foo} == 'abc' or ${in.header.bar} < '200'", true);
        assertExpression("${in.header.foo} == 'def' or ${in.header.bar} < '200'", true);
        assertExpression("${in.header.foo} == 'def' or ${in.header.bar} < '100'", false);
    }

    public void testEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} == 'abc'", true);
        assertExpression("${in.header.foo} == abc", true);
        assertExpression("${in.header.foo} == 'def'", false);
        assertExpression("${in.header.foo} == def", false);
        assertExpression("${in.header.foo} == '1'", false);

        // integer to string comparison
        assertExpression("${in.header.bar} == '123'", true);
        assertExpression("${in.header.bar} == 123", true);
        assertExpression("${in.header.bar} == '444'", false);
        assertExpression("${in.header.bar} == 444", false);
        assertExpression("${in.header.bar} == '1'", false);
    }

    public void testNotEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} != 'abc'", false);
        assertExpression("${in.header.foo} != abc", false);
        assertExpression("${in.header.foo} != 'def'", true);
        assertExpression("${in.header.foo} != def", true);
        assertExpression("${in.header.foo} != '1'", true);

        // integer to string comparison
        assertExpression("${in.header.bar} != '123'", false);
        assertExpression("${in.header.bar} != 123", false);
        assertExpression("${in.header.bar} != '444'", true);
        assertExpression("${in.header.bar} != 444", true);
        assertExpression("${in.header.bar} != '1'", true);
    }

    public void testGreaterThanOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} > 'aaa'", true);
        assertExpression("${in.header.foo} > aaa", true);
        assertExpression("${in.header.foo} > 'def'", false);
        assertExpression("${in.header.foo} > def", false);

        // integer to string comparison
        assertExpression("${in.header.bar} > '100'", true);
        assertExpression("${in.header.bar} > 100", true);
        assertExpression("${in.header.bar} > '123'", false);
        assertExpression("${in.header.bar} > 123", false);
        assertExpression("${in.header.bar} > '200'", false);
    }

    public void testGreaterThanStringToInt() throws Exception {
        // set a String value
        exchange.getIn().setHeader("num", "70");

        // string to int comparison
        assertExpression("${in.header.num} > 100", false);
        assertExpression("${in.header.num} > 100", false);
        assertExpression("${in.header.num} > 80", false);
        assertExpression("${in.header.num} > 800", false);
        assertExpression("${in.header.num} > 1", true);
        assertExpression("${in.header.num} > 8", true);
        assertExpression("${in.header.num} > 48", true);
        assertExpression("${in.header.num} > 69", true);
        assertExpression("${in.header.num} > 71", false);
        assertExpression("${in.header.num} > 88", false);
        assertExpression("${in.header.num} > 777", false);
    }

    public void testLessThanStringToInt() throws Exception {
        // set a String value
        exchange.getIn().setHeader("num", "70");

        // string to int comparison
        assertExpression("${in.header.num} < 100", true);
        assertExpression("${in.header.num} < 100", true);
        assertExpression("${in.header.num} < 80", true);
        assertExpression("${in.header.num} < 800", true);
        assertExpression("${in.header.num} < 1", false);
        assertExpression("${in.header.num} < 8", false);
        assertExpression("${in.header.num} < 48", false);
        assertExpression("${in.header.num} < 69", false);
        assertExpression("${in.header.num} < 71", true);
        assertExpression("${in.header.num} < 88", true);
        assertExpression("${in.header.num} < 777", true);
    }

    public void testGreaterThanOrEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} >= 'aaa'", true);
        assertExpression("${in.header.foo} >= aaa", true);
        assertExpression("${in.header.foo} >= 'abc'", true);
        assertExpression("${in.header.foo} >= abc", true);
        assertExpression("${in.header.foo} >= 'def'", false);

        // integer to string comparison
        assertExpression("${in.header.bar} >= '100'", true);
        assertExpression("${in.header.bar} >= 100", true);
        assertExpression("${in.header.bar} >= '123'", true);
        assertExpression("${in.header.bar} >= 123", true);
        assertExpression("${in.header.bar} >= '200'", false);
    }

    public void testLessThanOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} < 'aaa'", false);
        assertExpression("${in.header.foo} < aaa", false);
        assertExpression("${in.header.foo} < 'def'", true);
        assertExpression("${in.header.foo} < def", true);

        // integer to string comparison
        assertExpression("${in.header.bar} < '100'", false);
        assertExpression("${in.header.bar} < 100", false);
        assertExpression("${in.header.bar} < '123'", false);
        assertExpression("${in.header.bar} < 123", false);
        assertExpression("${in.header.bar} < '200'", true);
    }

    public void testLessThanOrEqualOperator() throws Exception {
        // string to string comparison
        assertExpression("${in.header.foo} <= 'aaa'", false);
        assertExpression("${in.header.foo} <= aaa", false);
        assertExpression("${in.header.foo} <= 'abc'", true);
        assertExpression("${in.header.foo} <= abc", true);
        assertExpression("${in.header.foo} <= 'def'", true);

        // integer to string comparison
        assertExpression("${in.header.bar} <= '100'", false);
        assertExpression("${in.header.bar} <= 100", false);
        assertExpression("${in.header.bar} <= '123'", true);
        assertExpression("${in.header.bar} <= 123", true);
        assertExpression("${in.header.bar} <= '200'", true);
    }

    public void testIsNull() throws Exception {
        assertExpression("${in.header.foo} == null", false);
        assertExpression("${in.header.none} == null", true);

        assertExpression("${in.header.foo} == 'null'", false);
        assertExpression("${in.header.none} == 'null'", true);
    }

    public void testIsNotNull() throws Exception {
        assertExpression("${in.header.foo} != null", true);
        assertExpression("${in.header.none} != null", false);

        assertExpression("${in.header.foo} != 'null'", true);
        assertExpression("${in.header.none} != 'null'", false);
    }

    public void testRightOperatorIsSimpleLanauge() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertExpression("${in.header.foo} == ${in.header.foo}", true);
        assertExpression("${in.header.foo} == ${in.header.bar}", false);
    }

    public void testRightOperatorIsBeanLanauge() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertExpression("${in.header.foo} == ${bean:generator.generateFilename}", true);

        assertExpression("${in.header.bar} == ${bean:generator.generateId}", true);
        assertExpression("${in.header.bar} >= ${bean:generator.generateId}", true);
    }

    public void testConstains() throws Exception {
        assertExpression("${in.header.foo} contains 'a'", true);
        assertExpression("${in.header.foo} contains a", true);
        assertExpression("${in.header.foo} contains 'ab'", true);
        assertExpression("${in.header.foo} contains 'abc'", true);
        assertExpression("${in.header.foo} contains 'def'", false);
        assertExpression("${in.header.foo} contains def", false);
    }

    public void testNotConstains() throws Exception {
        assertExpression("${in.header.foo} not contains 'a'", false);
        assertExpression("${in.header.foo} not contains a", false);
        assertExpression("${in.header.foo} not contains 'ab'", false);
        assertExpression("${in.header.foo} not contains 'abc'", false);
        assertExpression("${in.header.foo} not contains 'def'", true);
        assertExpression("${in.header.foo} not contains def", true);
    }

    public void testRegex() throws Exception {
        assertExpression("${in.header.foo} regex '^a..$'", true);
        assertExpression("${in.header.foo} regex '^ab.$'", true);
        assertExpression("${in.header.foo} regex ^ab.$", true);
        assertExpression("${in.header.foo} regex ^d.*$", false);

        assertExpression("${in.header.bar} regex '^\\d{3}'", true);
        assertExpression("${in.header.bar} regex '^\\d{2}'", false);
        assertExpression("${in.header.bar} regex ^\\d{3}", true);
        assertExpression("${in.header.bar} regex ^\\d{2}", false);
    }

    public void testNotRegex() throws Exception {
        assertExpression("${in.header.foo} not regex '^a..$'", false);
        assertExpression("${in.header.foo} not regex '^ab.$'", false);
        assertExpression("${in.header.foo} not regex ^ab.$", false);
        assertExpression("${in.header.foo} not regex ^d.*$", true);

        assertExpression("${in.header.bar} not regex '^\\d{3}'", false);
        assertExpression("${in.header.bar} not regex '^\\d{2}'", true);
        assertExpression("${in.header.bar} not regex ^\\d{3}", false);
        assertExpression("${in.header.bar} not regex ^\\d{2}", true);
    }

    public void testIn() throws Exception {
        // string to string
        assertExpression("${in.header.foo} in 'foo,abc,def'", true);
        assertExpression("${in.header.foo} in ${bean:generator.generateFilename}", true);
        assertExpression("${in.header.foo} in foo,abc,def", true);
        assertExpression("${in.header.foo} in 'foo,def'", false);

        // integer to string
        assertExpression("${in.header.bar} in '100,123,200'", true);
        assertExpression("${in.header.bar} in 100,123,200", true);
        assertExpression("${in.header.bar} in ${bean:generator.generateId}", true);
        assertExpression("${in.header.bar} in '100,200'", false);
    }

    public void testNotIn() throws Exception {
        // string to string
        assertExpression("${in.header.foo} not in 'foo,abc,def'", false);
        assertExpression("${in.header.foo} not in ${bean:generator.generateFilename}", false);
        assertExpression("${in.header.foo} not in foo,abc,def", false);
        assertExpression("${in.header.foo} not in 'foo,def'", true);

        // integer to string
        assertExpression("${in.header.bar} not in '100,123,200'", false);
        assertExpression("${in.header.bar} not in 100,123,200", false);
        assertExpression("${in.header.bar} not in ${bean:generator.generateId}", false);
        assertExpression("${in.header.bar} not in '100,200'", true);
    }

    public void testIs() throws Exception {
        assertExpression("${in.header.foo} is 'java.lang.String'", true);
        assertExpression("${in.header.foo} is 'java.lang.Integer'", false);

        assertExpression("${in.header.foo} is 'String'", true);
        assertExpression("${in.header.foo} is 'Integer'", false);

        assertExpression("${in.header.foo} is String", true);
        assertExpression("${in.header.foo} is Integer", false);

        try {
            assertExpression("${in.header.foo} is com.mycompany.DoesNotExist", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }
    }

    public void testIsNot() throws Exception {
        assertExpression("${in.header.foo} not is 'java.lang.String'", false);
        assertExpression("${in.header.foo} not is 'java.lang.Integer'", true);

        assertExpression("${in.header.foo} not is 'String'", false);
        assertExpression("${in.header.foo} not is 'Integer'", true);

        assertExpression("${in.header.foo} not is String", false);
        assertExpression("${in.header.foo} not is Integer", true);

        try {
            assertExpression("${in.header.foo} not is com.mycompany.DoesNotExist", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }
    }

    public void testRange() throws Exception {
        assertExpression("${in.header.bar} range 100..200", true);
        assertExpression("${in.header.bar} range 200..300", false);

        assertExpression("${in.header.foo} range 200..300", false);
        assertExpression("${bean:generator.generateId} range 123..130", true);
        assertExpression("${bean:generator.generateId} range 120..123", true);
        assertExpression("${bean:generator.generateId} range 120..122", false);
        assertExpression("${bean:generator.generateId} range 124..130", false);

        try {
            assertExpression("${in.header.foo} range abc..200", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertExpression("${in.header.foo} range abc..", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertExpression("${in.header.foo} range 100.200", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        assertExpression("${in.header.bar} range 100..200 and ${in.header.foo} == abc" , true);
        assertExpression("${in.header.bar} range 200..300 and ${in.header.foo} == abc" , false);
        assertExpression("${in.header.bar} range 200..300 or ${in.header.foo} == abc" , true);
        assertExpression("${in.header.bar} range 200..300 or ${in.header.foo} == def" , false);
    }

    public void testNotRange() throws Exception {
        assertExpression("${in.header.bar} not range 100..200", false);
        assertExpression("${in.header.bar} not range 200..300", true);

        assertExpression("${in.header.foo} not range 200..300", true);
        assertExpression("${bean:generator.generateId} not range 123..130", false);
        assertExpression("${bean:generator.generateId} not range 120..123", false);
        assertExpression("${bean:generator.generateId} not range 120..122", true);
        assertExpression("${bean:generator.generateId} not range 124..130", true);

        try {
            assertExpression("${in.header.foo} not range abc..200", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertExpression("${in.header.foo} not range abc..", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertExpression("${in.header.foo} not range 100.200", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }
    }

    protected String getLanguageName() {
        return "simple";
    }

    public class MyFileNameGenerator {
        public String generateFilename(Exchange exchange) {
            return "abc";
        }

        public int generateId(Exchange exchange) {
            return 123;
        }
    }

}