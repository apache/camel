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

import org.apache.camel.Exchange;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleOperatorTest extends LanguageTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("generator", new MyFileNameGenerator());
        return jndi;
    }

    @Test
    public void testValueWithSpace() {
        exchange.getIn().setBody("Hello Big World");
        assertPredicate("${in.body} == 'Hello Big World'", true);
        assertPredicate("${in.body} == ${body}", true);
    }

    @Test
    public void testNullValue() {
        exchange.getIn().setBody("Value");
        assertPredicate("${in.body} != null", true);
        assertPredicate("${body} == null", false);

        exchange.getIn().setBody(null);
        assertPredicate("${in.body} == null", true);
        assertPredicate("${body} != null", false);
    }

    @Test
    public void testEmptyValue() {
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

    @Test
    public void testAnd() {
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 444", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == 123", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == 444", false);

        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} > 100", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} < 200", true);
    }

    @Test
    public void testTwoAnd() {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 123 && ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == 123 && ${body} == 'Bye World'", false);
    }

    @Test
    public void testThreeAnd() {
        exchange.getIn().setBody("Hello World");
        assertPredicate(
                "${in.header.foo} == 'abc' && ${in.header.bar} == 123 && ${body} == 'Hello World' && ${in.header.xx} == null",
                true);
    }

    @Test
    public void testTwoOr() {
        exchange.getIn().setBody("Hello World");
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == 44 || ${body} == 'Bye World'", true);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Bye World'", false);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Hello World'", true);
        assertPredicate("${in.header.foo} == 'xxx' || ${in.header.bar} == 123 || ${body} == 'Bye World'", true);
    }

    @Test
    public void testThreeOr() {
        exchange.getIn().setBody("Hello World");
        assertPredicate(
                "${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Bye Moon' || ${body} contains 'World'",
                true);
        assertPredicate(
                "${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Bye Moon' || ${body} contains 'Moon'",
                false);
        assertPredicate(
                "${in.header.foo} == 'abc' || ${in.header.bar} == 44 || ${body} == 'Bye Moon' || ${body} contains 'Moon'",
                true);
        assertPredicate(
                "${in.header.foo} == 'xxx' || ${in.header.bar} == 123 || ${body} == 'Bye Moon' || ${body} contains 'Moon'",
                true);
        assertPredicate(
                "${in.header.foo} == 'xxx' || ${in.header.bar} == 44 || ${body} == 'Hello World' || ${body} contains 'Moon'",
                true);
    }

    @Test
    public void testAndWithQuotation() {
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} == '444'", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == '123'", false);
        assertPredicate("${in.header.foo} == 'def' && ${in.header.bar} == '444'", false);

        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} > '100'", true);
        assertPredicate("${in.header.foo} == 'abc' && ${in.header.bar} < '200'", true);
    }

    @Test
    public void testOr() {
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == 444", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == 123", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == 444", false);

        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < 100", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < 200", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < 200", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < 100", false);
    }

    @Test
    public void testOrWithQuotation() {
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} == '444'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == '123'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} == '444'", false);

        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < '100'", true);
        assertPredicate("${in.header.foo} == 'abc' || ${in.header.bar} < '200'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < '200'", true);
        assertPredicate("${in.header.foo} == 'def' || ${in.header.bar} < '100'", false);
    }

    @Test
    public void testEqualOperator() {
        // string to string comparison
        assertPredicate("${in.header.foo} == 'abc'", true);
        assertPredicate("${in.header.foo} == 'def'", false);
        assertPredicate("${in.header.foo} == '1'", false);

        // special with just minus sign
        assertPredicate("${in.header.foo} == '-'", false);
        assertPredicate("${in.header.bar} == '-'", false);

        // boolean to boolean comparison
        exchange.getIn().setHeader("bool", true);
        exchange.getIn().setHeader("booley", false);
        assertPredicate("${in.header.bool} == true", true);
        assertPredicate("${in.header.bool} == 'true'", true);
        assertPredicate("${in.header.booley} == false", true);
        assertPredicate("${in.header.booley} == 'false'", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} == '123'", true);
        assertPredicate("${in.header.bar} == 123", true);
        assertPredicate("${in.header.bar} == '444'", false);
        assertPredicate("${in.header.bar} == 444", false);
        assertPredicate("${in.header.bar} == '1'", false);

        // should not need type conversion
        assertEquals(0, context.getTypeConverterRegistry().getStatistics().getAttemptCounter());
    }

    @Test
    public void testEqualIgnoreOperator() {
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

        // special with just minus sign
        assertPredicate("${in.header.foo} =~ '-'", false);
        assertPredicate("${in.header.bar} =~ '-'", false);
    }

    @Test
    public void testNotEqualOperator() {
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

        // special with just minus sign
        assertPredicate("${in.header.foo} != '-'", true);
        assertPredicate("${in.header.bar} != '-'", true);
    }

    @Test
    public void testNotEqualIgnoreOperator() {
        // string to string comparison
        assertPredicate("${in.header.foo} !=~ 'abc'", false);
        assertPredicate("${in.header.foo} !=~ 'ABC'", false);
        assertPredicate("${in.header.foo} !=~ 'Abc'", false);
        assertPredicate("${in.header.foo} !=~ 'Def'", true);
        assertPredicate("${in.header.foo} !=~ '1'", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} !=~ '123'", false);
        assertPredicate("${in.header.bar} !=~ 123", false);
        assertPredicate("${in.header.bar} !=~ '444'", true);
        assertPredicate("${in.header.bar} !=~ 444", true);
        assertPredicate("${in.header.bar} !=~ '1'", true);
    }

    @Test
    public void testFloatingNumber() {
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

    @Test
    public void testGreaterThanOperator() {
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

    @Test
    public void testGreaterThanStringToInt() {
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

    @Test
    public void testLessThanStringToInt() {
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

    @Test
    public void testGreaterThanOrEqualOperator() {
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

        // special with just minus sign
        assertPredicate("${in.header.foo} >= '-'", true);
        assertPredicate("${in.header.bar} >= '-'", true);
    }

    @Test
    public void testLessThanOperator() {
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

    @Test
    public void testAgainstNegativeValue() {
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
        assertPredicate("${in.header.strNum} !contains '123'", false);
        assertPredicate("${in.header.strNum} contains '-123'", false);
        assertPredicate("${in.header.strNum} !contains '-123'", true);
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
        assertPredicate("${in.header.strNumNegative} !contains '123'", false);
        assertPredicate("${in.header.strNumNegative} contains '-123'", true);
        assertPredicate("${in.header.strNumNegative} !contains '-123'", false);
        assertPredicate("${in.header.strNumNegative} ~~ '123'", true);
        assertPredicate("${in.header.strNumNegative} ~~ '-123'", true);
    }

    @Test
    public void testLessThanOrEqualOperator() {
        // string to string comparison
        assertPredicate("${in.header.foo} <= 'aaa'", false);
        assertPredicate("${in.header.foo} <= 'abc'", true);
        assertPredicate("${in.header.foo} <= 'def'", true);

        // string to string
        exchange.getIn().setHeader("dude", "555");
        exchange.getIn().setHeader("dude2", "0099");
        assertPredicate("${in.header.dude} <= ${in.header.dude}", true);
        assertPredicate("${in.header.dude2} <= ${in.header.dude}", true);

        // integer to string comparison
        assertPredicate("${in.header.bar} <= '100'", false);
        assertPredicate("${in.header.bar} <= 100", false);
        assertPredicate("${in.header.bar} <= '123'", true);
        assertPredicate("${in.header.bar} <= 123", true);
        assertPredicate("${in.header.bar} <= '200'", true);

        // should not need type conversion
        assertEquals(0, context.getTypeConverterRegistry().getStatistics().getAttemptCounter());
    }

    @Test
    public void testTypeCoerceNoConversionNeeded() {
        // int to int comparison
        exchange.getIn().setHeader("num", 70);
        assertPredicate("${in.header.num} > 100", false);
        assertPredicate("${in.header.num} < 100", true);
        assertPredicate("${in.header.num} == 70", true);
        assertPredicate("${in.header.num} != 70", false);
        assertPredicate("${in.header.num} > 100", false);
        assertPredicate("${in.header.num} > 80", false);
        assertPredicate("${in.header.num} > 800", false);
        assertPredicate("${in.header.num} < 800", true);
        assertPredicate("${in.header.num} > 1", true);
        assertPredicate("${in.header.num} > 8", true);
        assertPredicate("${in.header.num} > 48", true);
        assertPredicate("${in.header.num} > 69", true);
        assertPredicate("${in.header.num} > 71", false);
        assertPredicate("${in.header.num} < 71", true);
        assertPredicate("${in.header.num} > 88", false);
        assertPredicate("${in.header.num} > 777", false);

        // String to int comparison
        exchange.getIn().setHeader("num", "70");
        assertPredicate("${in.header.num} > 100", false);
        assertPredicate("${in.header.num} < 100", true);
        assertPredicate("${in.header.num} == 70", true);
        assertPredicate("${in.header.num} != 70", false);
        assertPredicate("${in.header.num} > 100", false);
        assertPredicate("${in.header.num} > 80", false);
        assertPredicate("${in.header.num} > 800", false);
        assertPredicate("${in.header.num} < 800", true);
        assertPredicate("${in.header.num} > 1", true);
        assertPredicate("${in.header.num} > 8", true);
        assertPredicate("${in.header.num} > 48", true);
        assertPredicate("${in.header.num} > 69", true);
        assertPredicate("${in.header.num} > 71", false);
        assertPredicate("${in.header.num} < 71", true);
        assertPredicate("${in.header.num} > 88", false);
        assertPredicate("${in.header.num} > 777", false);

        // should not need type conversion
        assertEquals(0, context.getTypeConverterRegistry().getStatistics().getAttemptCounter());
    }

    @Test
    public void testIsNull() {
        assertPredicate("${in.header.foo} == null", false);
        assertPredicate("${in.header.none} == null", true);
    }

    @Test
    public void testIsNotNull() {
        assertPredicate("${in.header.foo} != null", true);
        assertPredicate("${in.header.none} != null", false);
    }

    @Test
    public void testRightOperatorIsSimpleLanguage() {
        // operator on right side is also using ${ } placeholders
        assertPredicate("${in.header.foo} == ${in.header.foo}", true);
        assertPredicate("${in.header.foo} == ${in.header.bar}", false);
    }

    @Test
    public void testRightOperatorIsBeanLanguage() {
        // operator on right side is also using ${ } placeholders
        assertPredicate("${in.header.foo} == ${bean:generator.generateFilename}", true);

        assertPredicate("${in.header.bar} == ${bean:generator.generateId}", true);
        assertPredicate("${in.header.bar} >= ${bean:generator.generateId}", true);
    }

    @Test
    public void testContains() {
        assertPredicate("${in.header.foo} contains 'a'", true);
        assertPredicate("${in.header.foo} contains 'ab'", true);
        assertPredicate("${in.header.foo} contains 'abc'", true);
        assertPredicate("${in.header.foo} contains 'def'", false);
    }

    @Test
    public void testContainsNumberInString() {
        exchange.getMessage().setBody("The answer is 42 and is the answer to life the universe and everything");
        assertPredicate("${body} contains '42'", true);
        assertPredicate("${body} contains 42", true);
        assertPredicate("${body} contains '77'", false);
        assertPredicate("${body} contains 77", false);
    }

    @Test
    public void testNotContains() {
        assertPredicate("${in.header.foo} not contains 'a'", false);
        assertPredicate("${in.header.foo} not contains 'ab'", false);
        assertPredicate("${in.header.foo} not contains 'abc'", false);
        assertPredicate("${in.header.foo} not contains 'def'", true);
        assertPredicate("${in.header.foo} !contains 'a'", false);
        assertPredicate("${in.header.foo} !contains 'ab'", false);
        assertPredicate("${in.header.foo} !contains 'abc'", false);
        assertPredicate("${in.header.foo} !contains 'def'", true);
    }

    @Test
    public void testContainsIgnoreCase() {
        assertPredicate("${in.header.foo} ~~ 'A'", true);
        assertPredicate("${in.header.foo} ~~ 'Ab'", true);
        assertPredicate("${in.header.foo} ~~ 'Abc'", true);
        assertPredicate("${in.header.foo} ~~ 'defG'", false);
    }

    @Test
    public void testNotContainsIgnoreCase() {
        assertPredicate("${in.header.foo} !~~ 'A'", false);
        assertPredicate("${in.header.foo} !~~ 'Ab'", false);
        assertPredicate("${in.header.foo} !~~ 'Abc'", false);
        assertPredicate("${in.header.foo} !~~ 'defG'", true);
    }

    @Test
    public void testRegex() {
        assertPredicate("${in.header.foo} regex '^a..$'", true);
        assertPredicate("${in.header.foo} regex '^ab.$'", true);
        assertPredicate("${in.header.foo} regex '^ab.$'", true);
        assertPredicate("${in.header.foo} regex '^d.*$'", false);

        assertPredicate("${in.header.bar} regex '^\\d{3}'", true);
        assertPredicate("${in.header.bar} regex '^\\d{2}'", false);
    }

    @Test
    public void testNotRegex() {
        assertPredicate("${in.header.foo} not regex '^a..$'", false);
        assertPredicate("${in.header.foo} not regex '^ab.$'", false);
        assertPredicate("${in.header.foo} not regex '^ab.$'", false);
        assertPredicate("${in.header.foo} not regex '^d.*$'", true);

        assertPredicate("${in.header.bar} not regex '^\\d{3}'", false);
        assertPredicate("${in.header.bar} not regex '^\\d{2}'", true);
    }

    @Test
    public void testIn() {
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

    @Test
    public void testNotIn() {
        // string to string
        assertPredicate("${in.header.foo} not in 'foo,abc,def'", false);
        assertPredicate("${in.header.foo} not in ${bean:generator.generateFilename}", false);
        assertPredicate("${in.header.foo} not in 'foo,abc,def'", false);
        assertPredicate("${in.header.foo} not in 'foo,def'", true);
        assertPredicate("${in.header.foo} !in 'foo,abc,def'", false);
        assertPredicate("${in.header.foo} !in ${bean:generator.generateFilename}", false);
        assertPredicate("${in.header.foo} !in 'foo,abc,def'", false);
        assertPredicate("${in.header.foo} !in 'foo,def'", true);

        // integer to string
        assertPredicate("${in.header.bar} not in '100,123,200'", false);
        assertPredicate("${in.header.bar} not in ${bean:generator.generateId}", false);
        assertPredicate("${in.header.bar} not in '100,200'", true);
        assertPredicate("${in.header.bar} !in '100,123,200'", false);
        assertPredicate("${in.header.bar} !in ${bean:generator.generateId}", false);
        assertPredicate("${in.header.bar} !in '100,200'", true);
    }

    @Test
    public void testIs() {
        assertPredicate("${in.header.foo} is 'java.lang.String'", true);
        assertPredicate("${in.header.foo} is 'java.lang.Integer'", false);

        assertPredicate("${in.header.foo} is 'String'", true);
        assertPredicate("${in.header.foo} is 'Integer'", false);

        SimpleIllegalSyntaxException e = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} is com.mycompany.DoesNotExist", false),
                "Should have thrown an exception");

        assertEquals(20, e.getIndex());
    }

    @Test
    public void testIsNot() {
        assertPredicate("${in.header.foo} not is 'java.lang.String'", false);
        assertPredicate("${in.header.foo} not is 'java.lang.Integer'", true);
        assertPredicate("${in.header.foo} !is 'java.lang.String'", false);
        assertPredicate("${in.header.foo} !is 'java.lang.Integer'", true);

        assertPredicate("${in.header.foo} not is 'String'", false);
        assertPredicate("${in.header.foo} not is 'Integer'", true);
        assertPredicate("${in.header.foo} !is 'String'", false);
        assertPredicate("${in.header.foo} !is 'Integer'", true);

        SimpleIllegalSyntaxException e1 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} not is com.mycompany.DoesNotExist", false),
                "Should have thrown an exception");

        assertEquals(24, e1.getIndex());

        SimpleIllegalSyntaxException e2 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} !is com.mycompany.DoesNotExist", false),
                "Should have thrown an exception");

        assertEquals(21, e2.getIndex());
    }

    @Test
    public void testRange() {
        assertPredicate("${in.header.bar} range '100..200'", true);
        assertPredicate("${in.header.bar} range '200..300'", false);

        assertPredicate("${in.header.foo} range '200..300'", false);
        assertPredicate("${bean:generator.generateId} range '123..130'", true);
        assertPredicate("${bean:generator.generateId} range '120..123'", true);
        assertPredicate("${bean:generator.generateId} range '120..122'", false);
        assertPredicate("${bean:generator.generateId} range '124..130'", false);

        SimpleIllegalSyntaxException e1 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} range abc..200", false),
                "Should have thrown an exception");

        assertEquals(23, e1.getIndex());

        SimpleIllegalSyntaxException e2 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} range abc..", false),
                "Should have thrown an exception");

        assertEquals(23, e2.getIndex());

        SimpleIllegalSyntaxException e3 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} range 100.200", false),
                "Should have thrown an exception");

        assertEquals(30, e3.getIndex());

        assertPredicate("${in.header.bar} range '100..200' && ${in.header.foo} == 'abc'", true);
        assertPredicate("${in.header.bar} range '200..300' && ${in.header.foo} == 'abc'", false);
        assertPredicate("${in.header.bar} range '200..300' || ${in.header.foo} == 'abc'", true);
        assertPredicate("${in.header.bar} range '200..300' || ${in.header.foo} == 'def'", false);
    }

    @Test
    public void testNotRange() {
        assertPredicate("${in.header.bar} not range '100..200'", false);
        assertPredicate("${in.header.bar} not range '200..300'", true);
        assertPredicate("${in.header.bar} !range '100..200'", false);
        assertPredicate("${in.header.bar} !range '200..300'", true);

        assertPredicate("${in.header.foo} not range '200..300'", true);
        assertPredicate("${bean:generator.generateId} not range '123..130'", false);
        assertPredicate("${bean:generator.generateId} not range '120..123'", false);
        assertPredicate("${bean:generator.generateId} not range '120..122'", true);
        assertPredicate("${bean:generator.generateId} not range '124..130'", true);
        assertPredicate("${in.header.foo} !range '200..300'", true);
        assertPredicate("${bean:generator.generateId} !range '123..130'", false);
        assertPredicate("${bean:generator.generateId} !range '120..123'", false);
        assertPredicate("${bean:generator.generateId} !range '120..122'", true);
        assertPredicate("${bean:generator.generateId} !range '124..130'", true);

        SimpleIllegalSyntaxException e1 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} not range abc..200", false),
                "Should have thrown an exception");

        assertEquals(27, e1.getIndex());

        SimpleIllegalSyntaxException e2 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} !range abc..200", false),
                "Should have thrown an exception");

        assertEquals(24, e2.getIndex());

        SimpleIllegalSyntaxException e3 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} not range abc..", false),
                "Should have thrown an exception");

        assertEquals(27, e3.getIndex());

        SimpleIllegalSyntaxException e4 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} !range abc..", false),
                "Should have thrown an exception");

        assertEquals(24, e4.getIndex());

        SimpleIllegalSyntaxException e5 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} not range 100.200", false),
                "Should have thrown an exception");

        assertEquals(34, e5.getIndex());

        SimpleIllegalSyntaxException e6 = assertThrows(SimpleIllegalSyntaxException.class,
                () -> assertPredicate("${in.header.foo} !range 100.200", false),
                "Should have thrown an exception");

        assertEquals(31, e6.getIndex());
    }

    @Test
    public void testUnaryInc() {
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

    @Test
    public void testUnaryDec() {
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

    @Test
    public void testStartsWith() {
        exchange.getIn().setBody("Hello there");
        assertPredicate("${in.body} starts with 'Hello'", true);
        assertPredicate("${in.body} starts with 'H'", true);
        assertPredicate("${in.body} starts with 'Hello there'", true);
        assertPredicate("${in.body} starts with 'Hello ther'", true);
        assertPredicate("${in.body} starts with 'ello there'", false);
        assertPredicate("${in.body} starts with 'Hi'", false);
        assertPredicate("${in.body} startsWith 'Hello'", true);
        assertPredicate("${in.body} startsWith 'H'", true);
        assertPredicate("${in.body} startsWith 'Hello there'", true);
        assertPredicate("${in.body} startsWith 'Hello ther'", true);
        assertPredicate("${in.body} startsWith 'ello there'", false);
        assertPredicate("${in.body} startsWith 'Hi'", false);
    }

    @Test
    public void testStartsWithTextAsNumeric() {
        exchange.getIn().setBody("01234");
        assertPredicate("${in.body} starts with '1234'", false);
        assertPredicate("${in.body} starts with 1234", false);
        assertPredicate("${in.body} starts with '01234'", true);
        assertPredicate("${in.body} starts with \"01234\"", true);
        assertPredicate("${in.body} starts with 01234", false);
    }

    @Test
    public void testNotStartsWith() {
        exchange.getIn().setBody("Hello there");
        assertPredicate("${in.body} !startsWith 'Bye'", true);
        assertPredicate("${in.body} !startsWith 'Hello'", false);
        assertPredicate("${in.body} !startsWith 'B'", true);
        assertPredicate("${in.body} !startsWith 'H'", false);
        assertPredicate("${in.body} !startsWith 'Bye there'", true);
        assertPredicate("${in.body} !startsWith 'Hello there'", false);
        assertPredicate("${in.body} !startsWith 'Hello ther'", false);
        assertPredicate("${in.body} !startsWith 'ello there'", true);
        assertPredicate("${in.body} !startsWith 'Hi'", true);
    }

    @Test
    public void testEndsWith() {
        exchange.getIn().setBody("Hello there");
        assertPredicate("${in.body} ends with 'there'", true);
        assertPredicate("${in.body} ends with 're'", true);
        assertPredicate("${in.body} ends with ' there'", true);
        assertPredicate("${in.body} ends with 'Hello there'", true);
        assertPredicate("${in.body} ends with 'Hello ther'", false);
        assertPredicate("${in.body} ends with 'Hi'", false);
        assertPredicate("${in.body} endsWith 'there'", true);
        assertPredicate("${in.body} endsWith 're'", true);
        assertPredicate("${in.body} endsWith ' there'", true);
        assertPredicate("${in.body} endsWith 'Hello there'", true);
        assertPredicate("${in.body} endsWith 'Hello ther'", false);
        assertPredicate("${in.body} endsWith 'Hi'", false);
    }

    @Test
    public void testNotEndsWith() {
        exchange.getIn().setBody("Hello there");
        assertPredicate("${in.body} !endsWith 'B'", true);
        assertPredicate("${in.body} !endsWith 'world'", true);
        assertPredicate("${in.body} !endsWith 'there'", false);
        assertPredicate("${in.body} !endsWith 're'", false);
        assertPredicate("${in.body} !endsWith ' there'", false);
        assertPredicate("${in.body} !endsWith 'Hello there'", false);
        assertPredicate("${in.body} !endsWith 'Hello ther'", true);
        assertPredicate("${in.body} !endsWith 'Hi'", true);
    }

    @Test
    public void testElvis() {
        exchange.getIn().setBody(false);
        assertPredicate("${body} ?: 'true'", true);
        assertPredicate("${body} ?: 'false'", false);
        exchange.getIn().setBody("Hello");
        assertPredicate("${body} ?: 'false'", true);
        exchange.getIn().setBody(0);
        assertPredicate("${body} ?: 'true'", true);
        assertPredicate("${body} ?: 'false'", false);
        exchange.getIn().setBody(1);
        assertPredicate("${body} ?: 'true'", true);
        assertPredicate("${body} ?: 'false'", true);

        exchange.getIn().setBody(null);
        assertExpression("${body} ?: 'World'", "World");
        exchange.getIn().setBody("");
        assertExpression("${body} ?: 'World'", "World");
        exchange.getIn().setBody("Hello");
        assertExpression("${body} ?: 'World'", "Hello");
        exchange.getIn().setBody(false);
        assertExpression("${body} ?: 'World'", "World");
        exchange.getIn().setBody(true);
        assertExpression("${body} ?: 'World'", true);
        exchange.getIn().setHeader("myHeader", "Camel");
        assertExpression("${header.myHeader} ?: 'World'", "Camel");
        exchange.getIn().setBody(0);
        assertExpression("${body} ?: 'World'", "World");
        exchange.getIn().setBody(1);
        assertExpression("${body} ?: 'World'", 1);
    }

    @Test
    public void testTernary() {
        exchange.getIn().setBody(false);
        assertPredicate("${body == true ? 'true' : 'false'}", false);
        assertPredicate("${body == true ? 'false' : 'true'}", true);
        exchange.getIn().setBody("Hello");
        assertPredicate("${body != null ? 'true' : 'false'}", true);
        assertPredicate("${body != null ? 'false' : 'true'}", false);
        exchange.getIn().setBody(0);
        assertPredicate("${body == 0 ? 'true' : 'false'}", true);
        assertPredicate("${body == 0 ? 'false' : 'true'}", false);
        exchange.getIn().setBody(1);
        assertPredicate("${body > 0 ? 'true' : 'false'}", true);
        assertPredicate("${body > 0 ? 'false' : 'true'}", false);

        exchange.getIn().setBody(null);
        assertExpression("${body != null ? 'A' : 'B'}", "B");
        exchange.getIn().setBody("");
        assertExpression("${body == '' ? 'A' : 'B'}", "A");
        exchange.getIn().setBody("Hello");
        assertExpression("${body != 'Hello' ? 'A' : 'B'}", "B");
        exchange.getIn().setBody(false);
        assertExpression("${body == true ? 'A' : 'B'}", "B");
        exchange.getIn().setBody(false);
        assertExpression("${body != true ? 'A' : 'B'}", "A");
    }

    @Test
    public void testTernaryLog() {
        exchange.getIn().setBody("Hello World");
        assertExpression(">>> Message received from WebSocket Client : ${body}",
                ">>> Message received from WebSocket Client : Hello World");

        exchange.getMessage().setHeader(Exchange.FILE_NAME, "foo.txt");
        assertExpression("This is a test bug ${header.CamelFileName}",
                "This is a test bug foo.txt");

        assertExpression("This is a test bug : ${header.CamelFileName}",
                "This is a test bug : foo.txt");

        assertExpression("This is a test bug ? ${header.CamelFileName}",
                "This is a test bug ? foo.txt");

    }

    @Test
    public void testChain() {
        exchange.getIn().setBody(null);
        assertExpression("${substringAfter('Hello')} ~> ${trim()} ~> ${uppercase()}", null);
        exchange.getIn().setBody("   Hello    World   ");
        assertExpression("${substringAfter('Hello')} ~> ${trim()} ~> ${uppercase()}", "WORLD");
        // run 2nd time give same result
        assertExpression("${substringAfter('Hello')} ~> ${trim()} ~> ${uppercase()}", "WORLD");

        exchange.getIn().setBody("  Hello    World   ");
        Predicate predicate = context.resolveLanguage("simple")
                .createPredicate("${substringAfter('Hello')} ~> ${trim()} ~> ${uppercase()} == 'WORLD'");
        boolean matches = predicate.matches(exchange);
        assertTrue(matches);
        // run 2nd time give same result
        matches = predicate.matches(exchange);
        assertTrue(matches);

        exchange.getIn().setBody("  Hello    Camel   ");
        predicate = context.resolveLanguage("simple")
                .createPredicate("${substringAfter('Hello')} ~> ${trim()} ~> ${uppercase()} == 'WORLD'");
        matches = predicate.matches(exchange);
        assertFalse(matches);
    }

    @Test
    public void testChainNullSafe() {
        exchange.getIn().setBody(null);
        assertExpression("${substringAfter('Hello')} ?~> ${collate(2)} ~> ${uppercase()}", null);
        // run 2nd time give same result
        assertExpression("${substringAfter('Hello')} ?~> ${collate(2)} ~> ${uppercase()}", null);

        Predicate predicate = context.resolveLanguage("simple")
                .createPredicate("${substringAfter('Hello')} ?~> ${collate(2)} ~> ${uppercase()}");
        boolean matches = predicate.matches(exchange);
        assertFalse(matches);
        // run 2nd time give same result
        matches = predicate.matches(exchange);
        assertFalse(matches);

        exchange.getIn().setBody("Hello World,Hello Camel");
        assertExpression("${substringAfter('Hello')} ?~> ${collate(2)} ~> ${kindOfType()}", "object");
        // run 2nd time give same result
        assertExpression("${substringAfter('Hello')} ?~> ${collate(2)} ~> ${kindOfType()}", "object");

        predicate = context.resolveLanguage("simple")
                .createPredicate("${substringAfter('Hello')} ?~> ${collate(2)} ~> ${kindOfType()} == 'object'");
        matches = predicate.matches(exchange);
        assertTrue(matches);
        // run 2nd time give same result
        matches = predicate.matches(exchange);
        assertTrue(matches);
    }

    @Test
    public void testChainParam() {
        exchange.getIn().setBody("   Hello World from the Camel   ");
        // no param
        assertExpression("${trim()} ~> ${replace('Hello','Hi')}", "Hi World from the Camel");
        assertExpression("${trim()} ~> ${replace('Hello','Hi')} ~> ${split(' ')} ~> ${size()}", 5);
        // with $param
        assertExpression("${trim()} ~> ${replace('Hello','Hi',$param)}", "Hi World from the Camel");
        assertExpression("${trim()} ~> ${replace('Hello','Hi',$param)} ~> ${split($param,' ')} ~> ${size($param)}", 5);
    }

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    public static class MyFileNameGenerator {
        public String generateFilename(Exchange exchange) {
            return "abc";
        }

        public int generateId(Exchange exchange) {
            return 123;
        }
    }

}
