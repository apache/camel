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

import org.apache.camel.Expression;
import org.apache.camel.LanguageTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleInitBlockTest extends LanguageTestSupport {

    private static final String INIT = """
            $init{
              // this is a java like comment
              $sum := ${sum(${header.lines},100)};

              $sku := ${body contains 'Camel' ? '123' : '999'};
            }init$
            orderId=$sku,total=$sum
            """;

    private static final String INIT2 = """
            $init{
              // this is a java like comment
              $sum := ${sum(${header.lines},100)};

              $sku := ${body contains 'Camel' ? '123' : '999'};
            }init$
            $sum > 200 && $sku != 999
            """;

    private static final String INIT3 = """
            $init{
              // this is a java like comment
              $sum := ${sum(${header.lines},100)};

              $sku := ${body contains 'Camel' ? '123' : '999'};
            }init$
            """;

    private static final String INIT4 = """
            $init{
              // this is a java like comment
              $sum := ${sum(${header.lines},100)};

              $sku := ${body contains 'Hi := Me $sku' ? '123' : '999'};
            }init$
            orderId=$sku,total=$sum
            """;

    private static final String INIT5 = """
            $init{
              // this is a java like comment
              $sum := ${sum(${header.lines},100)};

              $sku := ${body contains 'Hi := Me $sku'
                           ?
                         '123'
                           :
                         '999'
                      };
            }init$
            orderId=$sku,total=$sum
            """;

    @Test
    public void testInitBlockExpression() throws Exception {
        exchange.getMessage().setBody("Hello Camel");
        exchange.getMessage().setHeader("lines", "75,33");

        assertExpression(exchange, INIT, "orderId=123,total=208\n");
    }

    @Test
    public void testInitBlockOnlyExpression() throws Exception {
        exchange.getMessage().setBody("Hello Camel");
        exchange.getMessage().setHeader("lines", "75,33");

        assertExpression(exchange, INIT3, "");
        Assertions.assertEquals("123", exchange.getVariable("sku"));
        Assertions.assertEquals(208L, exchange.getVariable("sum"));
    }

    @Test
    public void testInitBlockPredicate() throws Exception {
        exchange.getMessage().setBody("Hello Camel");
        exchange.getMessage().setHeader("lines", "75,33");
        assertPredicate(exchange, INIT2, true);

        exchange.getMessage().setBody("Hello Camel");
        exchange.getMessage().setHeader("lines", "3,5");
        assertPredicate(exchange, INIT2, false);

        exchange.getMessage().setBody("Hello World");
        exchange.getMessage().setHeader("lines", "75,99");
        assertPredicate(exchange, INIT2, false);

        exchange.getMessage().setBody("Hello World");
        exchange.getMessage().setHeader("lines", "3,5");
        assertPredicate(exchange, INIT2, false);
    }

    @Test
    public void testInitBlockExpressionWithAssignmentInFunction() throws Exception {
        exchange.getMessage().setBody("Hello Hi := Me $sku");
        exchange.getMessage().setHeader("lines", "75,33");

        assertExpression(exchange, INIT4, "orderId=123,total=208\n");
    }

    @Test
    public void testInitBlockSpanLines() throws Exception {
        exchange.getMessage().setBody("Hello Hi := Me $sku");
        exchange.getMessage().setHeader("lines", "76,34");

        assertExpression(exchange, INIT5, "orderId=123,total=210\n");
    }

    @Test
    public void testInitBlockAverageFunction() {
        String exp = """
                $init{
                  $a := ${body};
                  $b := ${header.foo};
                  $c := ${header.bar};
                }init$
                average: ${average($a,$b,$c)}
                """;

        exchange.getMessage().setBody(1);
        exchange.getMessage().setHeader("foo", 2);
        exchange.getMessage().setHeader("bar", 3);

        Expression expression = context.resolveLanguage("simple").createExpression(exp);
        String s = expression.evaluate(exchange, String.class);
        assertEquals("average: 2\n", s);
    }

    @Test
    public void testInitBlockAverageVal() {
        String exp = """
                $init{
                  $a := ${val(4)};
                  $b := ${val(5)};
                  $c := ${val(6)};
                }init$
                average: ${average($a,$b,$c)}
                """;

        Expression expression = context.resolveLanguage("simple").createExpression(exp);
        String s = expression.evaluate(exchange, String.class);
        assertEquals("average: 5\n", s);
    }

    @Test
    public void testInitBlockAverageLiteral() {
        String exp = """
                $init{
                  $a := '5';
                  $b := '6';
                  $c := '7';
                }init$
                average: ${average($a,$b,$c)}
                """;

        Expression expression = context.resolveLanguage("simple").createExpression(exp);
        String s = expression.evaluate(exchange, String.class);
        assertEquals("average: 6\n", s);
    }

    @Test
    public void testInitBlockAverageNumeric() {
        String exp = """
                $init{
                  $a := 6;
                  $b := 7;
                  $c := 8;
                }init$
                ${average($a,$b,$c)}
                """;

        Expression expression = context.resolveLanguage("simple").createExpression(exp);
        String s = expression.evaluate(exchange, String.class);
        assertEquals("7\n", s);
    }

    @Test
    public void testInitBlockBoolean() {
        String exp = """
                $init{
                  $a := true;
                  $b := false;
                }init$
                ${body != null ? $a : $b}
                """;

        Expression expression = context.resolveLanguage("simple").createExpression(exp);
        String s = expression.evaluate(exchange, String.class);
        assertEquals("true\n", s);

        exchange.getMessage().setBody(null);
        s = expression.evaluate(exchange, String.class);
        assertEquals("false\n", s);
    }

    @Test
    public void testInitBlockVal() {
        String exp = """
                $init{
                  $bar := ${val(Hi from ${body})};
                }init$
                $bar
                """;

        exchange.getMessage().setBody("Camel");

        Expression expression = context.resolveLanguage("simple").createExpression(exp);
        String s = expression.evaluate(exchange, String.class);
        assertEquals("Hi from Camel\n", s);
    }

    @Test
    public void testInitBlockConstant() {
        String exp = """
                $init{
                  $bar := 'Hi from ${body}';
                }init$
                $bar
                """;

        exchange.getMessage().setBody("Camel");

        Expression expression = context.resolveLanguage("simple").createExpression(exp);
        String s = expression.evaluate(exchange, String.class);
        assertEquals("Hi from Camel\n", s);
    }

    @Override
    protected String getLanguageName() {
        return "simple";
    }
}
