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

import org.apache.camel.LanguageTestSupport;

/**
 *
 */
public class SimpleChangeFunctionTokensTest extends LanguageTestSupport {

    @Override
    protected String getLanguageName() {
        return "simple";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        SimpleLanguage.changeFunctionStartToken("[[");
        SimpleLanguage.changeFunctionEndToken("]]");
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        // replace old tokens
        SimpleLanguage.changeFunctionStartToken("${", "$simple{");
        SimpleLanguage.changeFunctionEndToken("}");
    }

    public void testSimpleBody() throws Exception {
        assertExpression(exchange, "[[body]]", "<hello id='m123'>world!</hello>");

        // old tokens do no longer work
        assertExpression(exchange, "${body}", "${body}");
    }

    public void testSimpleConstantAndBody() throws Exception {
        exchange.getIn().setBody("Camel");
        assertExpression(exchange, "Hi [[body]] how are you", "Hi Camel how are you");
        assertExpression(exchange, "'Hi '[[body]]' how are you'", "'Hi 'Camel' how are you'");

        // old tokens do no longer work
        assertExpression(exchange, "Hi ${body} how are you", "Hi ${body} how are you");
    }

    public void testSimpleConstantAndBodyAndHeader() throws Exception {
        exchange.getIn().setBody("Camel");
        exchange.getIn().setHeader("foo", "Tiger");
        assertExpression(exchange, "Hi [[body]] how are [[header.foo]]", "Hi Camel how are Tiger");
    }

    public void testSimpleEqOperator() throws Exception {
        exchange.getIn().setBody("Camel");
        assertPredicate(exchange, "[[body]] == 'Tiger'", false);
        assertPredicate(exchange, "[[body]] == 'Camel'", true);
        assertPredicate(exchange, "[[body]] == \"Tiger\"", false);
        assertPredicate(exchange, "[[body]] == \"Camel\"", true);
    }

}
