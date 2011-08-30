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
 * @version 
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
        assertPredicate("${in.body} == 'Hello Big World'", true);
    }
    
    public void testNullValue() throws Exception {
        exchange.getIn().setBody("Value");
        assertPredicate("${in.body} != null", true);
        assertPredicate("${body} == null", false);
        
        exchange.getIn().setBody(null);
        assertPredicate("${in.body} == null", true);
        assertPredicate("${body} != null", false);
    }

    public void testAnd() throws Exception {
        assertPredicate("${in.header.foo} == abc and ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == abc and ${in.header.bar} == 444", false);
        assertPredicate("${in.header.foo} == def and ${in.header.bar} == 123", false);
        assertPredicate("${in.header.foo} == def and ${in.header.bar} == 444", false);

        assertPredicate("${in.header.foo} == abc and ${in.header.bar} > 100", true);
        assertPredicate("${in.header.foo} == abc and ${in.header.bar} < 200", true);
    }

    public void testTwoAnd() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == abc and ${in.header.bar} == 123 and ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == 'abc' and ${in.header.bar} == 123 and ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == abc and ${in.header.bar} == 123 and ${body} == 'Bye World'", false);
        assertPredicate("${in.header.foo} == 'abc' and ${in.header.bar} == 123 and ${body} == 'Bye World'", false);
    }

    public void testThreeAnd() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == abc and ${in.header.bar} == 123 and ${body} == 'Hello World' and ${in.header.xx}} == null", true);
        assertPredicate("${in.header.foo} == 'abc' and ${in.header.bar} == 123 and ${body} == 'Hello World' and ${in.header.xx}} == null", true);
    }

    public void testTwoOr() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == abc or ${in.header.bar} == 44 or ${body} == 'Bye World'", true);
        assertPredicate("${in.header.foo} == 'abc' or ${in.header.bar} == 44 or ${body} == 'Bye World'", true);
        assertPredicate("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Bye World'", false);
        assertPredicate("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Bye World'", false);
        assertPredicate("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == xxx or ${in.header.bar} == 123 or ${body} == 'Bye World'", true);
        assertPredicate("${in.header.foo} == 'xxx' or ${in.header.bar} == 123 or ${body} == 'Bye World'", true);
    }

    public void testThreeOr() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'World'", true);
        assertPredicate("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'World'", true);
        assertPredicate("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", false);
        assertPredicate("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", false);
        assertPredicate("${in.header.foo} == abc or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertPredicate("${in.header.foo} == 'abc' or ${in.header.bar} == 44 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertPredicate("${in.header.foo} == xxx or ${in.header.bar} == 123 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertPredicate("${in.header.foo} == 'xxx' or ${in.header.bar} == 123 or ${body} == 'Bye Moon' or ${body} contains 'Moon'", true);
        assertPredicate("${in.header.foo} == xxx or ${in.header.bar} == 44 or ${body} == 'Hello World' or ${body} contains 'Moon'", true);
        assertPredicate("${in.header.foo} == 'xxx' or ${in.header.bar} == 44 or ${body} == 'Hello World' or ${body} contains 'Moon'", true);
    }

    public void testAndWithQuotation() throws Exception {
        assertPredicate("${in.header.foo} == 'abc' and ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'abc' and ${in.header.bar} == '444'", false);
        assertPredicate("${in.header.foo} == 'def' and ${in.header.bar} == '123'", false);
        assertPredicate("${in.header.foo} == 'def' and ${in.header.bar} == '444'", false);

        assertPredicate("${in.header.foo} == 'abc' and ${in.header.bar} > '100'", true);
        assertPredicate("${in.header.foo} == 'abc' and ${in.header.bar} < '200'", true);
    }

    public void testOr() throws Exception {
        assertPredicate("${in.header.foo} == abc or ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == abc or ${in.header.bar} == 444", true);
        assertPredicate("${in.header.foo} == def or ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == def or ${in.header.bar} == 444", false);

        assertPredicate("${in.header.foo} == abc or ${in.header.bar} < 100", true);
        assertPredicate("${in.header.foo} == abc or ${in.header.bar} < 200", true);
        assertPredicate("${in.header.foo} == def or ${in.header.bar} < 200", true);
        assertPredicate("${in.header.foo} == def or ${in.header.bar} < 100", false);
    }

    public void testOrWithQuotation() throws Exception {
        assertPredicate("${in.header.foo} == 'abc' or ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'abc' or ${in.header.bar} == '444'", true);
        assertPredicate("${in.header.foo} == 'def' or ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'def' or ${in.header.bar} == '444'", false);

        assertPredicate("${in.header.foo} == 'abc' or ${in.header.bar} < '100'", true);
        assertPredicate("${in.header.foo} == 'abc' or ${in.header.bar} < '200'", true);
        assertPredicate("${in.header.foo} == 'def' or ${in.header.bar} < '200'", true);
        assertPredicate("${in.header.foo} == 'def' or ${in.header.bar} < '100'", false);
    }

    public void testEqualOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} == 'abc'", true);
        assertPredicate("${in.header.foo} == abc", true);
        assertPredicate("${in.header.foo} == 'def'", false);
        assertPredicate("${in.header.foo} == def", false);
        assertPredicate("${in.header.foo} == '1'", false);

        // integer to string comparison
        assertPredicate("${in.header.bar} == '123'", true);
        assertPredicate("${in.header.bar} == 123", true);
        assertPredicate("${in.header.bar} == '444'", false);
        assertPredicate("${in.header.bar} == 444", false);
        assertPredicate("${in.header.bar} == '1'", false);
    }

    public void testNotEqualOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} != 'abc'", false);
        assertPredicate("${in.header.foo} != abc", false);
        assertPredicate("${in.header.foo} != 'def'", true);
        assertPredicate("${in.header.foo} != def", true);
        assertPredicate("${in.header.foo} != '1'", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} != '123'", false);
        assertPredicate("${in.header.bar} != 123", false);
        assertPredicate("${in.header.bar} != '444'", true);
        assertPredicate("${in.header.bar} != 444", true);
        assertPredicate("${in.header.bar} != '1'", true);
    }

    public void testGreaterThanOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} > 'aaa'", true);
        assertPredicate("${in.header.foo} > aaa", true);
        assertPredicate("${in.header.foo} > 'def'", false);
        assertPredicate("${in.header.foo} > def", false);

        // integer to string comparison
        assertPredicate("${in.header.bar} > '100'", true);
        assertPredicate("${in.header.bar} > 100", true);
        assertPredicate("${in.header.bar} > '123'", false);
        assertPredicate("${in.header.bar} > 123", false);
        assertPredicate("${in.header.bar} > '200'", false);
    }

    public void testGreaterThanStringToInt() throws Exception {
        // set a String value
        exchange.getIn().setHeader("num", "70");

        // string to int comparison
        assertPredicate("${in.header.num} > 100", false);
        assertPredicate("${in.header.num} > 100", false);
        assertPredicate("${in.header.num} > 80", false);
        assertPredicate("${in.header.num} > 800", false);
        assertPredicate("${in.header.num} > 1", true);
        assertPredicate("${in.header.num} > 8", true);
        assertPredicate("${in.header.num} > 48", true);
        assertPredicate("${in.header.num} > 69", true);
        assertPredicate("${in.header.num} > 71", false);
        assertPredicate("${in.header.num} > 88", false);
        assertPredicate("${in.header.num} > 777", false);
    }

    public void testLessThanStringToInt() throws Exception {
        // set a String value
        exchange.getIn().setHeader("num", "70");

        // string to int comparison
        assertPredicate("${in.header.num} < 100", true);
        assertPredicate("${in.header.num} < 100", true);
        assertPredicate("${in.header.num} < 80", true);
        assertPredicate("${in.header.num} < 800", true);
        assertPredicate("${in.header.num} < 1", false);
        assertPredicate("${in.header.num} < 8", false);
        assertPredicate("${in.header.num} < 48", false);
        assertPredicate("${in.header.num} < 69", false);
        assertPredicate("${in.header.num} < 71", true);
        assertPredicate("${in.header.num} < 88", true);
        assertPredicate("${in.header.num} < 777", true);
    }

    public void testGreaterThanOrEqualOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} >= 'aaa'", true);
        assertPredicate("${in.header.foo} >= aaa", true);
        assertPredicate("${in.header.foo} >= 'abc'", true);
        assertPredicate("${in.header.foo} >= abc", true);
        assertPredicate("${in.header.foo} >= 'def'", false);

        // integer to string comparison
        assertPredicate("${in.header.bar} >= '100'", true);
        assertPredicate("${in.header.bar} >= 100", true);
        assertPredicate("${in.header.bar} >= '123'", true);
        assertPredicate("${in.header.bar} >= 123", true);
        assertPredicate("${in.header.bar} >= '200'", false);
    }

    public void testLessThanOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} < 'aaa'", false);
        assertPredicate("${in.header.foo} < aaa", false);
        assertPredicate("${in.header.foo} < 'def'", true);
        assertPredicate("${in.header.foo} < def", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} < '100'", false);
        assertPredicate("${in.header.bar} < 100", false);
        assertPredicate("${in.header.bar} < '123'", false);
        assertPredicate("${in.header.bar} < 123", false);
        assertPredicate("${in.header.bar} < '200'", true);
    }

    public void testLessThanOrEqualOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} <= 'aaa'", false);
        assertPredicate("${in.header.foo} <= aaa", false);
        assertPredicate("${in.header.foo} <= 'abc'", true);
        assertPredicate("${in.header.foo} <= abc", true);
        assertPredicate("${in.header.foo} <= 'def'", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} <= '100'", false);
        assertPredicate("${in.header.bar} <= 100", false);
        assertPredicate("${in.header.bar} <= '123'", true);
        assertPredicate("${in.header.bar} <= 123", true);
        assertPredicate("${in.header.bar} <= '200'", true);
    }

    public void testIsNull() throws Exception {
        assertPredicate("${in.header.foo} == null", false);
        assertPredicate("${in.header.none} == null", true);

        assertPredicate("${in.header.foo} == 'null'", false);
        assertPredicate("${in.header.none} == 'null'", true);
    }

    public void testIsNotNull() throws Exception {
        assertPredicate("${in.header.foo} != null", true);
        assertPredicate("${in.header.none} != null", false);

        assertPredicate("${in.header.foo} != 'null'", true);
        assertPredicate("${in.header.none} != 'null'", false);
    }

    public void testRightOperatorIsSimpleLanauge() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertPredicate("${in.header.foo} == ${in.header.foo}", true);
        assertPredicate("${in.header.foo} == ${in.header.bar}", false);
    }

    public void testRightOperatorIsBeanLanauge() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertPredicate("${in.header.foo} == ${bean:generator.generateFilename}", true);

        assertPredicate("${in.header.bar} == ${bean:generator.generateId}", true);
        assertPredicate("${in.header.bar} >= ${bean:generator.generateId}", true);
    }

    public void testConstains() throws Exception {
        assertPredicate("${in.header.foo} contains 'a'", true);
        assertPredicate("${in.header.foo} contains a", true);
        assertPredicate("${in.header.foo} contains 'ab'", true);
        assertPredicate("${in.header.foo} contains 'abc'", true);
        assertPredicate("${in.header.foo} contains 'def'", false);
        assertPredicate("${in.header.foo} contains def", false);
    }

    public void testNotConstains() throws Exception {
        assertPredicate("${in.header.foo} not contains 'a'", false);
        assertPredicate("${in.header.foo} not contains a", false);
        assertPredicate("${in.header.foo} not contains 'ab'", false);
        assertPredicate("${in.header.foo} not contains 'abc'", false);
        assertPredicate("${in.header.foo} not contains 'def'", true);
        assertPredicate("${in.header.foo} not contains def", true);
    }

    public void testRegex() throws Exception {
        assertPredicate("${in.header.foo} regex '^a..$'", true);
        assertPredicate("${in.header.foo} regex '^ab.$'", true);
        assertPredicate("${in.header.foo} regex ^ab.$", true);
        assertPredicate("${in.header.foo} regex ^d.*$", false);

        assertPredicate("${in.header.bar} regex '^\\d{3}'", true);
        assertPredicate("${in.header.bar} regex '^\\d{2}'", false);
        assertPredicate("${in.header.bar} regex ^\\d{3}", true);
        assertPredicate("${in.header.bar} regex ^\\d{2}", false);
    }

    public void testNotRegex() throws Exception {
        assertPredicate("${in.header.foo} not regex '^a..$'", false);
        assertPredicate("${in.header.foo} not regex '^ab.$'", false);
        assertPredicate("${in.header.foo} not regex ^ab.$", false);
        assertPredicate("${in.header.foo} not regex ^d.*$", true);

        assertPredicate("${in.header.bar} not regex '^\\d{3}'", false);
        assertPredicate("${in.header.bar} not regex '^\\d{2}'", true);
        assertPredicate("${in.header.bar} not regex ^\\d{3}", false);
        assertPredicate("${in.header.bar} not regex ^\\d{2}", true);
    }

    public void testIn() throws Exception {
        // string to string
        assertPredicate("${in.header.foo} in 'foo,abc,def'", true);
        assertPredicate("${in.header.foo} in ${bean:generator.generateFilename}", true);
        assertPredicate("${in.header.foo} in foo,abc,def", true);
        assertPredicate("${in.header.foo} in 'foo,def'", false);

        // integer to string
        assertPredicate("${in.header.bar} in '100,123,200'", true);
        assertPredicate("${in.header.bar} in 100,123,200", true);
        assertPredicate("${in.header.bar} in ${bean:generator.generateId}", true);
        assertPredicate("${in.header.bar} in '100,200'", false);
    }

    public void testNotIn() throws Exception {
        // string to string
        assertPredicate("${in.header.foo} not in 'foo,abc,def'", false);
        assertPredicate("${in.header.foo} not in ${bean:generator.generateFilename}", false);
        assertPredicate("${in.header.foo} not in foo,abc,def", false);
        assertPredicate("${in.header.foo} not in 'foo,def'", true);

        // integer to string
        assertPredicate("${in.header.bar} not in '100,123,200'", false);
        assertPredicate("${in.header.bar} not in 100,123,200", false);
        assertPredicate("${in.header.bar} not in ${bean:generator.generateId}", false);
        assertPredicate("${in.header.bar} not in '100,200'", true);
    }

    public void testIs() throws Exception {
        assertPredicate("${in.header.foo} is 'java.lang.String'", true);
        assertPredicate("${in.header.foo} is 'java.lang.Integer'", false);

        assertPredicate("${in.header.foo} is 'String'", true);
        assertPredicate("${in.header.foo} is 'Integer'", false);

        assertPredicate("${in.header.foo} is String", true);
        assertPredicate("${in.header.foo} is Integer", false);

        try {
            assertPredicate("${in.header.foo} is com.mycompany.DoesNotExist", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }
    }

    public void testIsNot() throws Exception {
        assertPredicate("${in.header.foo} not is 'java.lang.String'", false);
        assertPredicate("${in.header.foo} not is 'java.lang.Integer'", true);

        assertPredicate("${in.header.foo} not is 'String'", false);
        assertPredicate("${in.header.foo} not is 'Integer'", true);

        assertPredicate("${in.header.foo} not is String", false);
        assertPredicate("${in.header.foo} not is Integer", true);

        try {
            assertPredicate("${in.header.foo} not is com.mycompany.DoesNotExist", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }
    }

    public void testRange() throws Exception {
        assertPredicate("${in.header.bar} range 100..200", true);
        assertPredicate("${in.header.bar} range 200..300", false);

        assertPredicate("${in.header.foo} range 200..300", false);
        assertPredicate("${bean:generator.generateId} range 123..130", true);
        assertPredicate("${bean:generator.generateId} range 120..123", true);
        assertPredicate("${bean:generator.generateId} range 120..122", false);
        assertPredicate("${bean:generator.generateId} range 124..130", false);

        try {
            assertPredicate("${in.header.foo} range abc..200", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertPredicate("${in.header.foo} range abc..", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertPredicate("${in.header.foo} range 100.200", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        assertPredicate("${in.header.bar} range 100..200 and ${in.header.foo} == abc" , true);
        assertPredicate("${in.header.bar} range 200..300 and ${in.header.foo} == abc" , false);
        assertPredicate("${in.header.bar} range 200..300 or ${in.header.foo} == abc" , true);
        assertPredicate("${in.header.bar} range 200..300 or ${in.header.foo} == def" , false);
    }

    public void testNotRange() throws Exception {
        assertPredicate("${in.header.bar} not range 100..200", false);
        assertPredicate("${in.header.bar} not range 200..300", true);

        assertPredicate("${in.header.foo} not range 200..300", true);
        assertPredicate("${bean:generator.generateId} not range 123..130", false);
        assertPredicate("${bean:generator.generateId} not range 120..123", false);
        assertPredicate("${bean:generator.generateId} not range 120..122", true);
        assertPredicate("${bean:generator.generateId} not range 124..130", true);

        try {
            assertPredicate("${in.header.foo} not range abc..200", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertPredicate("${in.header.foo} not range abc..", false);
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Syntax error"));
        }

        try {
            assertPredicate("${in.header.foo} not range 100.200", false);
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