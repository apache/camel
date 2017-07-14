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

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Willem", names.get(2));
    }

    public void testTokenizeBody() throws Exception {
        Expression exp = TokenizeLanguage.tokenize(",");

        exchange.getIn().setBody("Hadrian,Charles");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("Hadrian", names.get(0));
        assertEquals("Charles", names.get(1));
    }

    public void testTokenizeBodyRegEx() throws Exception {
        Expression exp = TokenizeLanguage.tokenize("(\\W+)\\s*", true);

        exchange.getIn().setBody("The little fox");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("The", names.get(0));
        assertEquals("little", names.get(1));
        assertEquals("fox", names.get(2));
    }

    public void testTokenizeHeaderRegEx() throws Exception {
        Expression exp = TokenizeLanguage.tokenize("quote", "(\\W+)\\s*", true);

        exchange.getIn().setHeader("quote", "Camel rocks");

        List<?> names = exp.evaluate(exchange, List.class);
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

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Willem", names.get(2));

        assertEquals("names", lan.getHeaderName());
        assertEquals(",", lan.getToken());
        assertEquals(false, lan.isRegex());
        assertEquals(false, lan.isSingleton());
    }

    public void testTokenizePairSpecial() throws Exception {
        Expression exp = TokenizeLanguage.tokenizePair("!", "@", false);

        exchange.getIn().setBody("2011-11-11\n!James@!Claus@\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("James", names.get(0));
        assertEquals("Claus", names.get(1));
    }

    public void testTokenizePair() throws Exception {
        Expression exp = TokenizeLanguage.tokenizePair("[START]", "[END]", false);

        exchange.getIn().setBody("2011-11-11\n[START]James[END]\n[START]Claus[END]\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("James", names.get(0));
        assertEquals("Claus", names.get(1));
    }

    public void testTokenizePairSimple() throws Exception {
        Expression exp = TokenizeLanguage.tokenizePair("${header.foo}", "${header.bar}", false);

        exchange.getIn().setHeader("foo", "[START]");
        exchange.getIn().setHeader("bar", "[END]");
        exchange.getIn().setBody("2011-11-11\n[START]James[END]\n[START]Claus[END]\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("James", names.get(0));
        assertEquals("Claus", names.get(1));
    }

    public void testTokenizePairIncludeTokens() throws Exception {
        Expression exp = TokenizeLanguage.tokenizePair("[START]", "[END]", true);

        exchange.getIn().setBody("2011-11-11\n[START]James[END]\n[START]Claus[END]\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("[START]James[END]", names.get(0));
        assertEquals("[START]Claus[END]", names.get(1));
    }

    public void testTokenizeXMLPair() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>",  null);

        exchange.getIn().setBody("<persons><person>James</person><person>Claus</person><person>Jonathan</person><person>Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairSimple() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("${header.foo}", null);

        exchange.getIn().setHeader("foo", "<person>");
        exchange.getIn().setBody("<persons><person>James</person><person>Claus</person><person>Jonathan</person><person>Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairNoXMLTag() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("person",  null);

        exchange.getIn().setBody("<persons><person>James</person><person>Claus</person><person>Jonathan</person><person>Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithNoise() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody("<?xml version=\"1.0\"?><!-- bla bla --><persons>\n<person>James</person>\n<person>Claus</person>\n"
                + "<!-- more bla bla --><person>Jonathan</person>\n<person>Hadrian</person>\n</persons>   ");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairEmpty() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody("<?xml version=\"1.0\"?><!-- bla bla --><persons></persons>   ");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(0, names.size());
    }

    public void testTokenizeXMLPairNoData() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody("");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(0, names.size());
    }

    public void testTokenizeXMLPairNullData() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody(null);

        List<?> names = exp.evaluate(exchange, List.class);
        assertNull(names);
    }
    
    public void testTokenizeXMLPairWithSimilarChildNames() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("Trip", "Trips");
        exchange.getIn().setBody("<?xml version='1.0' encoding='UTF-8'?>\n<Trips>\n<Trip>\n<TripType>\n</TripType>\n</Trip>\n</Trips>");
        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(1, names.size());
    }
    

    public void testTokenizeXMLPairWithDefaultNamespace() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", "<persons>");

        exchange.getIn().setBody("<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person>James</person>\n<person>Claus</person>\n"
                + "<person>Jonathan</person>\n<person>Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithDefaultNamespaceNotInherit() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody("<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person>James</person>\n<person>Claus</person>\n"
                + "<person>Jonathan</person>\n<person>Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithDefaultAndFooNamespace() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", "<persons>");

        exchange.getIn().setBody("<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">\n<person>James</person>\n<person>Claus</person>\n"
                + "<person>Jonathan</person>\n<person>Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithLocalNamespace() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody("<?xml version=\"1.0\"?><persons>\n<person xmlns=\"http:acme.com/persons\">James</person>\n<person xmlns=\"http:acme.com/persons\">Claus</person>\n"
                + "<person xmlns=\"http:acme.com/persons\">Jonathan</person>\n<person xmlns=\"http:acme.com/persons\">Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithLocalAndInheritedNamespace() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", "<persons>");

        exchange.getIn().setBody("<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person xmlns:foo=\"http:foo.com\">James</person>\n<person>Claus</person>\n"
                + "<person>Jonathan</person>\n<person xmlns:bar=\"http:bar.com\">Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns:foo=\"http:foo.com\" xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns:bar=\"http:bar.com\" xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithLocalAndNotInheritedNamespace() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody("<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person xmlns:foo=\"http:foo.com\">James</person>\n"
                + "<person>Claus</person>\n<person>Jonathan</person>\n<person xmlns:bar=\"http:bar.com\">Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns:foo=\"http:foo.com\">James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person xmlns:bar=\"http:bar.com\">Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithAttributes() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", null);

        exchange.getIn().setBody("<persons><person id=\"1\">James</person><person id=\"2\">Claus</person><person id=\"3\">Jonathan</person>"
                + "<person id=\"4\">Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person id=\"1\">James</person>", names.get(0));
        assertEquals("<person id=\"2\">Claus</person>", names.get(1));
        assertEquals("<person id=\"3\">Jonathan</person>", names.get(2));
        assertEquals("<person id=\"4\">Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithAttributesInheritNamespace() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", "<persons>");

        exchange.getIn().setBody("<persons xmlns=\"http:acme.com/persons\"><person id=\"1\">James</person><person id=\"2\">Claus</person>"
                + "<person id=\"3\">Jonathan</person><person id=\"4\">Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person id=\"1\" xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person id=\"2\" xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person id=\"3\" xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person id=\"4\" xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    public void testTokenizeXMLPairWithAttributes2InheritNamespace() throws Exception {
        Expression exp = TokenizeLanguage.tokenizeXML("<person>", "<persons>");

        exchange.getIn().setBody("<persons riders=\"true\" xmlns=\"http:acme.com/persons\"><person id=\"1\">James</person><person id=\"2\">Claus</person>"
                + "<person id=\"3\">Jonathan</person><person id=\"4\">Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person id=\"1\" xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person id=\"2\" xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person id=\"3\" xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person id=\"4\" xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

}