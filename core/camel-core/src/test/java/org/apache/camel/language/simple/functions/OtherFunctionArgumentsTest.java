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
package org.apache.camel.language.simple.functions;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleFunction;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arguments of the functions that did not split their arguments with the rules of CAMEL-26465: an argument left out
 * keeps its place, and a comma inside a nested function does not split an argument.
 */
public class OtherFunctionArgumentsTest extends AbstractSimpleFunctionFactoryTestSupport {

    @BeforeEach
    public void setUpExchange() {
        exchange.getIn().setBody("abc");
        exchange.getIn().setHeader("s", "abcdef");
        exchange.getIn().setHeader("n", "1234");
        PluginHelper.getSimpleFunctionRegistry(context).addFunction(new SimpleFunction() {
            @Override
            public String getName() {
                return "shout";
            }

            @Override
            public Object apply(Exchange exchange, Object input) {
                return input + "!";
            }
        });
    }

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new MiscFunctionFactory();
    }

    @Test
    public void testCommaInsideANestedFunction() {
        assertEquals("bc-de", evaluate("concat(${header.s.substring(1,3)},${header.s.substring(3,5)},-)", String.class));
        assertEquals("bc   ", evaluate("pad(${header.s.substring(1,3)},5)", String.class));
        assertEquals(23, evaluate("convertTo(${header.n.substring(1,3)},int)", Integer.class));
        assertEquals("bc", evaluate("iif(${header.s.substring(0,1)} == 'a',${header.s.substring(1,3)},no)", String.class));
        assertEquals("bc!", evaluate("function(shout,${header.s.substring(1,3)})", String.class));
        assertEquals(evaluate("hash(bc)", String.class), evaluate("hash(${header.s.substring(1,3)})", String.class));
        assertEquals(35L, evaluate("sum(${header.n.substring(1,3)},12)", Long.class));
        assertEquals(23L, evaluate("max(${header.n.substring(1,3)},12)", Long.class));
        assertEquals("[23, 24]", evaluate("range(${header.n.substring(1,3)},25)", List.class).toString());
        int random = evaluate("random(${header.n.substring(1,3)},24)", Integer.class);
        assertEquals(23, random);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> evaluate("throwException(${header.s.substring(1,3)},java.lang.IllegalStateException)"));
        assertTrue(e.getMessage().contains("bc"), e.getMessage());
    }

    @Test
    public void testArgumentLeftOutWithADefault() {
        // the first expression of concat, and the expression of convertTo, function and hash, is the message body
        assertEquals("abcx", evaluate("concat(,x)", String.class));
        assertEquals("abc-x", evaluate("concat(,x,-)", String.class));
        exchange.getIn().setBody("42");
        assertEquals(42, evaluate("convertTo(,int)", Integer.class));
        assertEquals("42!", evaluate("function(shout,)", String.class));
        assertEquals(evaluate("hash(${body})", String.class), evaluate("hash(,SHA-256)", String.class));
        // the algorithm of hash is SHA-256, the separator of pad a space, of concat none
        assertEquals(evaluate("hash(${body})", String.class), evaluate("hash(${body},)", String.class));
        assertEquals("42   ", evaluate("pad(${body},5,)", String.class));
        assertEquals("42x", evaluate("concat(${body},x,)", String.class));
        // the min of range and random is 0
        assertEquals("[0, 1]", evaluate("range(,2)", List.class).toString());
        assertEquals(0, evaluate("random(,1)", Integer.class));
        // the type of throwException is IllegalArgumentException
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> evaluate("throwException(Oops,)"));
        assertEquals("Oops", e.getMessage());
    }

    @Test
    public void testArgumentLeftOutWithoutADefaultIsRefused() {
        // before, the next argument took its place, or the argument was dropped
        String[][] refused = {
                { "misc", "throwException(,java.lang.IllegalStateException)" },
                { "misc", "assert(,Must be Hello)" },
                { "misc", "assert(${body} == 'abc',)" },
                { "misc", "convertTo(${body},)" },
                { "misc", "iif(${body} == 'abc',,no)" },
                { "misc", "iif(${body} == 'abc',yes,)" },
                { "misc", "iif(,yes,no)" },
                { "string", "pad(,5)" },
                { "string", "pad(${body},)" },
                { "string", "concat(${body},)" },
                { "string", "concat(${body},,-)" },
                { "custom", "function(,${body})" },
                { "collection", "range(1,)" },
                { "random", "random(1,)" } };
        for (String[] r : refused) {
            SimpleLanguageFunctionFactory factory = switch (r[0]) {
                case "misc" -> new MiscFunctionFactory();
                case "string" -> new StringFunctionFactory();
                case "custom" -> new CustomFunctionFactory();
                case "collection" -> new CollectionFunctionFactory();
                default -> new RandomFunctionFactory();
            };
            assertThrows(SimpleParserException.class, () -> factory.createFunction(context, r[1], 0), r[1]);
        }
    }

    @Test
    public void testEmptyValueOfAMathFunctionIsNoValue() {
        // the values of sum, max, min and average are a list: an empty value is no value, as before
        assertEquals(3L, evaluate("sum(1,,2)", Long.class));
        assertEquals(2L, evaluate("max(1,,2)", Long.class));
    }

    @Test
    public void testQuotesAndSpacesAsBefore() {
        assertEquals("Hello, abc", evaluate("concat('Hello, ',${body})", String.class));
        assertEquals("@@abc", evaluate("pad(${body}, -5, '@')", String.class));
        assertEquals("yes", evaluate("iif(${body} == 'abc', 'yes' , 'no')", String.class));
        assertEquals(42, evaluate("convertTo( '42' , int )", Integer.class));
    }
}
