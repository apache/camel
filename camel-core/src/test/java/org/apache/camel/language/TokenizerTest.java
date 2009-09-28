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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.language.tokenizer.TokenizeLanguage;

public class TokenizerTest extends ExchangeTestSupport {

    @Override
    protected void populateExchange(Exchange exchange) {
        super.populateExchange(exchange);
        exchange.getIn().setHeader("names", "Claus,James,Willem");
    }

    public void testTokenizeHeader() throws Exception {
        Expression exp = TokenizeLanguage.tokenize("names", ",");

        List names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Willem", names.get(2));
    }

    public void testTokenizeBody() throws Exception {
        Expression exp = TokenizeLanguage.tokenize(",");

        exchange.getIn().setBody("Hadrian,Charles");

        List names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("Hadrian", names.get(0));
        assertEquals("Charles", names.get(1));
    }

    public void testTokenizeBodyRegEx() throws Exception {
        Expression exp = TokenizeLanguage.tokenize("(\\W+)\\s*", true);

        exchange.getIn().setBody("The little fox");

        List names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("The", names.get(0));
        assertEquals("little", names.get(1));
        assertEquals("fox", names.get(2));
    }

    public void testTokenizeHeaderRegEx() throws Exception {
        Expression exp = TokenizeLanguage.tokenize("quote", "(\\W+)\\s*", true);

        exchange.getIn().setHeader("quote", "Camel rocks");

        List names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("Camel", names.get(0));
        assertEquals("rocks", names.get(1));
    }

    public void testTokenizeManualConfiguration() throws Exception {
        TokenizeLanguage lan = new TokenizeLanguage();
        lan.setHeaderName("names");
        lan.setRegex(false);
        lan.setToken(",");
        Expression exp = lan.createExpression();

        List names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Willem", names.get(2));

        assertEquals("names", lan.getHeaderName());
        assertEquals(",", lan.getToken());
        assertEquals(false, lan.isRegex());
        assertEquals(false, lan.isSingleton());
    }

}