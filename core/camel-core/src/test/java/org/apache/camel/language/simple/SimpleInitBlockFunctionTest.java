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

public class SimpleInitBlockFunctionTest extends LanguageTestSupport {

    private static final String INIT1 = """
            $init{
              $clean ~:= ${trim()} ~> ${normalizeWhitespace()} ~> ${uppercase()};
            }init$
            You said: $clean()
            """;

    private static final String INIT2 = """
            $init{
              $clean ~:= ${trim()} ~> ${normalizeWhitespace()} ~> ${uppercase()};
              $count ~:= ${function(clean)} ~> ${split(' ')} ~> ${size()};
            }init$
            You said: $clean() in $count() words
            """;

    private static final String INIT3 = """
            $init{
              $clean ~:= ${trim()} ~> ${normalizeWhitespace()} ~> ${uppercase()};
            }init$
            You said: ${clean('  Clean this text  please ...    ')} and then do something else
            """;

    private static final String INIT4 = """
            $init{
              $clean ~:= ${trim()} ~> ${normalizeWhitespace()} ~> ${uppercase()};
              $count ~:= ${quote()} ~> ${function(clean)} ~> ${unquote()} ~> ${trim()} ~> ${split(' ')} ~> ${size()};
            }init$
            You said: $clean() in $count() words
            """;

    private static final String INIT5 = """
            $init{
              $clean ~:= ${trim()} ~> ${normalizeWhitespace()} ~> ${uppercase()};
              $count ~:= ${clean()} ~> ${split(' ')} ~> ${size()};
            }init$
            You said: $clean() in $count() words
            """;

    private static final String INIT6 = """
            $init{
              $clean ~:= ${trim()} ~> ${normalizeWhitespace()} ~> ${uppercase()};
              $count ~:= ${quote()} ~> ${clean()} ~> ${unquote()} ~> ${trim()} ~> ${split(' ')} ~> ${size()};
            }init$
            You said: $clean() in $count() words
            """;

    private static final String INIT7 = """
            $init{
              $clean ~:= ${trim()} ~> ${normalizeWhitespace()} ~> ${uppercase()};
            }init$
            You said: ${clean()}
            """;

    @Test
    public void testInitBlockChain1() throws Exception {
        exchange.getMessage().setBody("   Hello  big   World      ");

        assertExpression(exchange, INIT1, "You said: HELLO BIG WORLD\n");
    }

    @Test
    public void testInitBlockChain2() throws Exception {
        exchange.getMessage().setBody("   Hello  big   World      ");

        assertExpression(exchange, INIT2, "You said: HELLO BIG WORLD in 3 words\n");
    }

    @Test
    public void testInitBlockChain3() throws Exception {
        exchange.getMessage().setBody("Hello World");

        assertExpression(exchange, INIT3, "You said: CLEAN THIS TEXT PLEASE ... and then do something else\n");
    }

    @Test
    public void testInitBlockChain4() throws Exception {
        exchange.getMessage().setBody("   Hello  big   World      ");

        assertExpression(exchange, INIT4, "You said: HELLO BIG WORLD in 3 words\n");
    }

    @Test
    public void testInitBlockChain5() throws Exception {
        exchange.getMessage().setBody("   Hello  big   World      ");

        assertExpression(exchange, INIT5, "You said: HELLO BIG WORLD in 3 words\n");
    }

    @Test
    public void testInitBlockChain6() throws Exception {
        exchange.getMessage().setBody("   Hello  big   World      ");

        assertExpression(exchange, INIT6, "You said: HELLO BIG WORLD in 3 words\n");
    }

    @Test
    public void testInitBlockChain7() throws Exception {
        exchange.getMessage().setBody("   Hello  big   World      ");

        assertExpression(exchange, INIT7, "You said: HELLO BIG WORLD\n");
    }

    @Override
    protected String getLanguageName() {
        return "simple";
    }
}
