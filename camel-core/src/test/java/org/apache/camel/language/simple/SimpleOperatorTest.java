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
package org.apache.camel.language.simple;

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;

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

    public void testEmptyValue() throws Exception {
        exchange.getIn().setBody("");
        assertPredicate("${in.body} == null", false);
        assertPredicate("${body} == null", false);

        exchange.getIn().setBody("");
        assertPredicate("${in.body} == ''", true);
        assertPredicate("${body} == \"\"", true);

        exchange.getIn().setBody(" ");
        assertPredicate("${in.body} == ''", false);
        assertPredicate("${body} == \"\"", false);

        exchange.getIn().setBody("Value");
        assertPredicate("${in.body} == ''", false);
        assertPredicate("${body} == \"\"", false);
    }

    public void testAnd() throws Exception {
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 444", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == 123", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == 444", false);

        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} > 100", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} < 200", true);
    }

    public void testTwoAnd() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 123 && ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 123 && ${body} == 'Bye World'", false);
    }

    public void testThreeAnd() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 123 && ${body} == 'Hello World' && ${in.header.xx} == null", true);
    }

    public void testTwoOr() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == 44 || ${body} == 'Bye World'", true);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Bye World'", false);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 123 || ${body} == 'Bye World'", true);
    }

    public void testThreeOr() throws Exception {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Bye Moon' || ${body} contains 'World'", true);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Bye Moon' || ${body} contains 'Moon'", false);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == 44 || ${body} == 'Bye Moon' || ${body} contains 'Moon'", true);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 123 || ${body} == 'Bye Moon' || ${body} contains 'Moon'", true);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Hello World' || ${body} contains 'Moon'", true);
    }

    public void testAndWithQuotation() throws Exception {
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == '444'", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == '123'", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == '444'", false);

        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} > '100'", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} < '200'", true);
    }

    public void testOr() throws Exception {
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == 444", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == 444", false);

        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < 100", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < 200", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < 200", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < 100", false);
    }

    public void testOrWithQuotation() throws Exception {
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == '444'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == '444'", false);

        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < '100'", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < '200'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < '200'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < '100'", false);
    }

    public void testEqualOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} == 'abc'", true);
        assertPredicate("${in.header.foo} == 'def'", false);
        assertPredicate("${in.header.foo} == '1'", false);

        // integer to string comparison
        assertPredicate("${in.header.bar} == '123'", true);
        assertPredicate("${in.header.bar} == 123", true);
        assertPredicate("${in.header.bar} == '444'", false);
        assertPredicate("${in.header.bar} == 444", false);
        assertPredicate("${in.header.bar} == '1'", false);
    }

    public void testEqualIgnoreOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} =~ 'abc'", true);
        assertPredicate("${in.header.foo} =~ 'ABC'", true);
        assertPredicate("${in.header.foo} =~ 'Abc'", true);
        assertPredicate("${in.header.foo} =~ 'Def'", false);
        assertPredicate("${in.header.foo} =~ '1'", false);

        // integer to string comparison
        assertPredicate("${in.header.bar} =~ '123'", true);
        assertPredicate("${in.header.bar} =~ 123", true);
        assertPredicate("${in.header.bar} =~ '444'", false);
        assertPredicate("${in.header.bar} =~ 444", false);
        assertPredicate("${in.header.bar} =~ '1'", false);
    }

    public void testNotEqualOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} != 'abc'", false);
        assertPredicate("${in.header.foo} != 'def'", true);
        assertPredicate("${in.header.foo} != '1'", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} != '123'", false);
        assertPredicate("${in.header.bar} != 123", false);
        assertPredicate("${in.header.bar} != '444'", true);
        assertPredicate("${in.header.bar} != 444", true);
        assertPredicate("${in.header.bar} != '1'", true);
    }

    public void testFloatingNumber() throws Exception {
        // set a String value
        exchange.getIn().setBody("0.02");

        assertPredicate("${body} > 0", true);
        assertPredicate("${body} < 0", false);

        assertPredicate("${body} > 0.00", true);
        assertPredicate("${body} < 0.00", false);

        assertPredicate("${body} > 0.01", true);
        assertPredicate("${body} < 0.01", false);

        assertPredicate("${body} > 0.02", false);
        assertPredicate("${body} < 0.02", false);

        assertPredicate("${body} == 0.02", true);
    }

    public void testGreaterThanOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} > 'aaa'", true);
        assertPredicate("${in.header.foo} > 'def'", false);

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
        assertPredicate("${in.header.foo} >= 'abc'", true);
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
        assertPredicate("${in.header.foo} < 'def'", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} < '100'", false);
        assertPredicate("${in.header.bar} < 100", false);
        assertPredicate("${in.header.bar} < '123'", false);
        assertPredicate("${in.header.bar} < 123", false);
        assertPredicate("${in.header.bar} < '200'", true);
    }
    
    public void testAgainstNegativeValue() throws Exception {
        assertPredicate("${in.header.bar} == 123", true);
        assertPredicate("${in.header.bar} == -123", false);
        assertPredicate("${in.header.bar} =~ 123", true);
        assertPredicate("${in.header.bar} =~ -123", false);
        assertPredicate("${in.header.bar} > -123", true);
        assertPredicate("${in.header.bar} >= -123", true);
        assertPredicate("${in.header.bar} > 123", false);
        assertPredicate("${in.header.bar} >= 123", true);
        assertPredicate("${in.header.bar} < -123", false);
        assertPredicate("${in.header.bar} <= -123", false);
        assertPredicate("${in.header.bar} < 123", false);
        assertPredicate("${in.header.bar} <= 123", true);

        exchange.getIn().setHeader("strNum", "123");
        assertPredicate("${in.header.strNum} contains '123'", true);
        assertPredicate("${in.header.strNum} not contains '123'", false);
        assertPredicate("${in.header.strNum} contains '-123'", false);
        assertPredicate("${in.header.strNum} not contains '-123'", true);
        assertPredicate("${in.header.strNum} ~~ '123'", true);
        assertPredicate("${in.header.strNum} ~~ '-123'", false);
    
        exchange.getIn().setHeader("num", -123);
        assertPredicate("${in.header.num} == -123", true);
        assertPredicate("${in.header.num} == 123", false);
        assertPredicate("${in.header.num} =~ -123", true);
        assertPredicate("${in.header.num} =~ 123", false);
        assertPredicate("${in.header.num} > -123", false);
        assertPredicate("${in.header.num} >= -123", true);
        assertPredicate("${in.header.num} > 123", false);
        assertPredicate("${in.header.num} >= 123", false);
        assertPredicate("${in.header.num} < -123", false);
        assertPredicate("${in.header.num} <= -123", true);
        assertPredicate("${in.header.num} < 123", true);
        assertPredicate("${in.header.num} <= 123", true);
    
        exchange.getIn().setHeader("strNumNegative", "-123");
        assertPredicate("${in.header.strNumNegative} contains '123'", true);
        assertPredicate("${in.header.strNumNegative} not contains '123'", false);
        assertPredicate("${in.header.strNumNegative} contains '-123'", true);
        assertPredicate("${in.header.strNumNegative} not contains '-123'", false);
        assertPredicate("${in.header.strNumNegative} ~~ '123'", true);
        assertPredicate("${in.header.strNumNegative} ~~ '-123'", true);

    }

    public void testLessThanOrEqualOperator() throws Exception {
        // string to string comparison
        assertPredicate("${in.header.foo} <= 'aaa'", false);
        assertPredicate("${in.header.foo} <= 'abc'", true);
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
    }

    public void testIsNotNull() throws Exception {
        assertPredicate("${in.header.foo} != null", true);
        assertPredicate("${in.header.none} != null", false);
    }

    public void testRightOperatorIsSimpleLanguage() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertPredicate("${in.header.foo} == ${in.header.foo}", true);
        assertPredicate("${in.header.foo} == ${in.header.bar}", false);
    }

    public void testRightOperatorIsBeanLanguage() throws Exception {
        // operator on right side is also using ${ } placeholders
        assertPredicate("${in.header.foo} == ${bean:generator.generateFilename}", true);

        assertPredicate("${in.header.bar} == ${bean:generator.generateId}", true);
        assertPredicate("${in.header.bar} >= ${bean:generator.generateId}", true);
    }

    public void testContains() throws Exception {
        assertPredicate("${in.header.foo} contains 'a'", true);
        assertPredicate("${in.header.foo} contains 'ab'", true);
        assertPredicate("${in.header.foo} contains 'abc'", true);
        assertPredicate("${in.header.foo} contains 'def'", false);
    }
  
    public void testNotContains() throws Exception {
        assertPredicate("${in.header.foo} not contains 'a'", false);
        assertPredicate("${in.header.foo} not contains 'ab'", false);
        assertPredicate("${in.header.foo} not contains 'abc'", false);
        assertPredicate("${in.header.foo} not contains 'def'", true);
    }
    
    public void testContainsIgnoreCase() throws Exception {
        assertPredicate("${in.header.foo} ~~ 'A'", true);
        assertPredicate("${in.header.foo} ~~ 'Ab'", true);
        assertPredicate("${in.header.foo} ~~ 'Abc'", true);
        assertPredicate("${in.header.foo} ~~ 'defG'", false);
    }

    public void testRegex() throws Exception {
        assertPredicate("${in.header.foo} regex '^a..$'", true);
        assertPredicate("${in.header.foo} regex '^ab.$'", true);
        assertPredicate("${in.header.foo} regex '^ab.$'", true);
        assertPredicate("${in.header.foo} regex '^d.*$'", false);

        assertPredicate("${in.header.bar} regex '^\\d{3}'", true);
        assertPredicate("${in.header.bar} regex '^\\d{2}'", false);
    }

    public void testNotRegex() throws Exception {
        assertPredicate("${in.header.foo} not regex '^a..$'", false);
        assertPredicate("${in.header.foo} not regex '^ab.$'", false);
        assertPredicate("${in.header.foo} not regex '^ab.$'", false);
        assertPredicate("${in.header.foo} not regex '^d.*$'", true);

        assertPredicate("${in.header.bar} not regex '^\\d{3}'", false);
        assertPredicate("${in.header.bar} not regex '^\\d{2}'", true);
    }

    public void testIn() throws Exception {
        // string to string
        assertPredicate("${in.header.foo} in 'foo,abc,def'", true);
        assertPredicate("${in.header.foo} in ${bean:generator.generateFilename}", true);
        assertPredicate("${in.header.foo} in 'foo,abc,def'", true);
        assertPredicate("${in.header.foo} in 'foo,def'", false);

        // integer to string
        assertPredicate("${in.header.bar} in '100,123,200'", true);
        assertPredicate("${in.header.bar} in ${bean:generator.generateId}", true);
        assertPredicate("${in.header.bar} in '100,200'", false);
    }

    public void testNotIn() throws Exception {
        // string to string
        assertPredicate("${in.header.foo} not in 'foo,abc,def'", false);
        assertPredicate("${in.header.foo} not in ${bean:generator.generateFilename}", false);
        assertPredicate("${in.header.foo} not in 'foo,abc,def'", false);
        assertPredicate("${in.header.foo} not in 'foo,def'", true);

        // integer to string
        assertPredicate("${in.header.bar} not in '100,123,200'", false);
        assertPredicate("${in.header.bar} not in ${bean:generator.generateId}", false);
        assertPredicate("${in.header.bar} not in '100,200'", true);
    }

    public void testIs() throws Exception {
        assertPredicate("${in.header.foo} is 'java.lang.String'", true);
        assertPredicate("${in.header.foo} is 'java.lang.Integer'", false);

        assertPredicate("${in.header.foo} is 'String'", true);
        assertPredicate("${in.header.foo} is 'Integer'", false);

        try {
            assertPredicate("${in.header.foo} is com.mycompany.DoesNotExist", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(20, e.getIndex());
        }
    }

    public void testIsNot() throws Exception {
        assertPredicate("${in.header.foo} not is 'java.lang.String'", false);
        assertPredicate("${in.header.foo} not is 'java.lang.Integer'", true);

        assertPredicate("${in.header.foo} not is 'String'", false);
        assertPredicate("${in.header.foo} not is 'Integer'", true);

        try {
            assertPredicate("${in.header.foo} not is com.mycompany.DoesNotExist", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(24, e.getIndex());
        }
    }

    public void testRange() throws Exception {
        assertPredicate("${in.header.bar} range '100..200'", true);
        assertPredicate("${in.header.bar} range '200..300'", false);

        assertPredicate("${in.header.foo} range '200..300'", false);
        assertPredicate("${bean:generator.generateId} range '123..130'", true);
        assertPredicate("${bean:generator.generateId} range '120..123'", true);
        assertPredicate("${bean:generator.generateId} range '120..122'", false);
        assertPredicate("${bean:generator.generateId} range '124..130'", false);

        try {
            assertPredicate("${in.header.foo} range abc..200", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(23, e.getIndex());
        }

        try {
            assertPredicate("${in.header.foo} range abc..", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(23, e.getIndex());
        }

        try {
            assertPredicate("${in.header.foo} range 100.200", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(30, e.getIndex());
        }

        assertPredicate("${in.header.bar} range '100..200' && ${in.header.foo} == 'abc'", true);
        assertPredicate("${in.header.bar} range '200..300' && ${in.header.foo} == 'abc'", false);
        assertPredicate("${in.header.bar} range '200..300' || ${in.header.foo} == 'abc'", true);
        assertPredicate("${in.header.bar} range '200..300' || ${in.header.foo} == 'def'", false);
    }

    public void testNotRange() throws Exception {
        assertPredicate("${in.header.bar} not range '100..200'", false);
        assertPredicate("${in.header.bar} not range '200..300'", true);

        assertPredicate("${in.header.foo} not range '200..300'", true);
        assertPredicate("${bean:generator.generateId} not range '123..130'", false);
        assertPredicate("${bean:generator.generateId} not range '120..123'", false);
        assertPredicate("${bean:generator.generateId} not range '120..122'", true);
        assertPredicate("${bean:generator.generateId} not range '124..130'", true);

        try {
            assertPredicate("${in.header.foo} not range abc..200", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(27, e.getIndex());
        }

        try {
            assertPredicate("${in.header.foo} not range abc..", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(27, e.getIndex());
        }

        try {
            assertPredicate("${in.header.foo} not range 100.200", false);
            fail("Should have thrown an exception");
        } catch (SimpleIllegalSyntaxException e) {
            assertEquals(34, e.getIndex());
        }
    }

    public void testUnaryInc() throws Exception {
        assertExpression("${in.header.bar}++", 124);
        assertExpression("+++++++++++++", "+++++++++++++");
        assertExpression("Logging ++ start ++", "Logging ++ start ++");
        assertExpression("Logging +++ start +++", "Logging +++ start +++");
        assertExpression("++ start ++", "++ start ++");
        assertExpression("+++ start +++", "+++ start +++");

        assertPredicate("${in.header.bar}++ == 122", false);
        assertPredicate("${in.header.bar}++ == 123", false);
        assertPredicate("${in.header.bar}++ == 124", true);
    }

    public void testUnaryDec() throws Exception {
        assertExpression("${in.header.bar}--", 122);
        assertExpression("-------------", "-------------");
        assertExpression("Logging -- start --", "Logging -- start --");
        assertExpression("Logging --- start ---", "Logging --- start ---");
        assertExpression("-- start --", "-- start --");
        assertExpression("--- start ---", "--- start ---");

        assertPredicate("${in.header.bar}-- == 122", true);
        assertPredicate("${in.header.bar}-- == 123", false);
        assertPredicate("${in.header.bar}-- == 124", false);
    }

    public void testStartsWith() throws Exception {
        exchange.getIn().setBody("Hello there");
        assertPredicate("${in.body} starts with 'Hello'", true);
        assertPredicate("${in.body} starts with 'H'", true);
        assertPredicate("${in.body} starts with 'Hello there'", true);
        assertPredicate("${in.body} starts with 'Hello ther'", true);
        assertPredicate("${in.body} starts with 'ello there'", false);
        assertPredicate("${in.body} starts with 'Hi'", false);
    }
    
    public void testEndsWith() throws Exception {
        exchange.getIn().setBody("Hello there");
        assertPredicate("${in.body} ends with 'there'", true);
        assertPredicate("${in.body} ends with 're'", true);
        assertPredicate("${in.body} ends with ' there'", true);
        assertPredicate("${in.body} ends with 'Hello there'", true);
        assertPredicate("${in.body} ends with 'Hello ther'", false);
        assertPredicate("${in.body} ends with 'Hi'", false);
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