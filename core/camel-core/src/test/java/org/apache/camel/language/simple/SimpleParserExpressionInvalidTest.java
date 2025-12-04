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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class SimpleParserExpressionInvalidTest extends ExchangeTestSupport {

    @Test
    public void testSimpleUnbalanceFunction() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body is a nice day", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parseExpression, "Should thrown exception");

        assertEquals(19, e.getIndex());
    }

    @Test
    public void testSimpleNestedUnbalanceFunction() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "${body${foo}", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parseExpression, "Should thrown exception");

        assertEquals(11, e.getIndex());
    }

    @Test
    public void testSimpleUnknownFunction() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "Hello ${foo} how are you?", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parseExpression, "Should thrown exception");

        assertEquals(6, e.getIndex());
    }

    @Test
    public void testSimpleNestedUnknownFunction() {
        SimpleExpressionParser parser =
                new SimpleExpressionParser(context, "Hello ${bodyAs(${foo})} how are you?", true, null);
        // nested functions can only be syntax evaluated when evaluating an
        // exchange at runtime
        SimpleIllegalSyntaxException e = assertThrows(
                SimpleIllegalSyntaxException.class,
                () -> parser.parseExpression().evaluate(exchange, String.class),
                "Should thrown exception");

        assertEquals(0, e.getIndex());
    }

    @Test
    public void testNoEndFunction() {
        SimpleExpressionParser parser = new SimpleExpressionParser(context, "Hello ${body", true, null);
        SimpleIllegalSyntaxException e =
                assertThrows(SimpleIllegalSyntaxException.class, parser::parseExpression, "Should thrown exception");

        assertEquals(11, e.getIndex());
    }
}
