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

import org.apache.camel.LanguageTestSupport;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-24964: edge cases of the tokenizer and the predicate parser.
 */
public class SimpleParserEdgeCasesTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Test
    public void testSingleQuoteInsideDoubleQuotesInFunction() {
        exchange.getMessage().setBody("a'b'c");
        assertPredicate("${body.replace(\"'\", \"\")} == 'abc'", true);
        assertPredicate("${body.replace(\"'\", \"\")} == 'x'", false);
    }

    @Test
    public void testBraceInsideDoubleQuotes() {
        exchange.getMessage().setBody("a}b");
        exchange.getMessage().setHeader("foo", "y");
        assertPredicate("${body} contains \"}\" && ${header.foo} == 'y'", true);
        assertPredicate("${body} contains \"}\" && ${header.foo} == 'z'", false);
    }

    @Test
    public void testChainFollowedByNumberOrNull() {
        exchange.getMessage().setBody("abcdef");
        assertPredicate("${body} ~> ${length()} > 5", true);
        assertPredicate("${body} ~> ${length()} > 10", false);
        assertPredicate("${body} ~> ${length()} == null", false);
        assertPredicate("${body} ~> ${length()} > -5", true);
    }

    @Test
    public void testChainNeedsSpaces() {
        assertExpression("Move A~>B", "Move A~>B");
        exchange.getMessage().setBody("a~>b");
        assertExpression("${body.replace('~>', '-')}", "a-b");
    }

    @Test
    public void testOperatorInsideQuotesInBraces() {
        exchange.getMessage().setBody("a > b");
        assertPredicate("${body == 'a > b'}", true);
        exchange.getMessage().setBody("x == y");
        assertPredicate("${body startsWith 'x == y'}", true);
    }

    @Test
    public void testUnquotedTextWithDigitIsLenient() {
        // kept working as routes may compare with unquoted text such as v2
        exchange.getMessage().setHeader("version", "v2");
        assertPredicate("${header.version} == v2", true);
    }

    @Test
    public void testColonWithoutSpacesIsNotATernary() {
        context.getRegistry().bind("svc", new MyService());
        assertExpression("${bean:svc?method=echo('10:30')}", "10:30");
    }

    public static class MyService {
        public String echo(String s) {
            return s;
        }
    }
}
