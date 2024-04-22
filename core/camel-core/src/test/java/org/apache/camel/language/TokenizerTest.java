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
package org.apache.camel.language;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.language.tokenizer.TokenizeLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TokenizerTest extends ExchangeTestSupport {

    Expression tokenize(String token) {
        return tokenize(token, false);
    }

    Expression tokenize(String token, boolean regex) {
        TokenizeLanguage language = new TokenizeLanguage();
        language.setCamelContext(context);
        return language.createExpression(null, new Object[] { null, null, token, null, null, null, regex });
    }

    Expression tokenize(String headerName, String token) {
        TokenizeLanguage language = new TokenizeLanguage();
        language.setCamelContext(context);
        return language.createExpression(null, new Object[] { null, "header:" + headerName, token });
    }

    Expression tokenizePair(String startToken, String endToken, boolean includeTokens) {
        TokenizeLanguage language = new TokenizeLanguage();
        language.setCamelContext(context);
        return language.createExpression(null,
                new Object[] { null, null, startToken, endToken, null, null, null, null, includeTokens });
    }

    Expression tokenizeXML(String tagName, String inheritNamespaceTagName) {
        TokenizeLanguage language = new TokenizeLanguage();
        language.setCamelContext(context);
        return language.createExpression(null,
                new Object[] { null, null, tagName, null, inheritNamespaceTagName, null, null, true });
    }

    @Override
    protected void populateExchange(Exchange exchange) {
        super.populateExchange(exchange);
        exchange.getIn().setHeader("names", "Claus,James,Willem");
    }

    @Test
    public void testTokenizeHeaderWithStringConstructor() {
        List<?> names = tokenize("names", ",").evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Willem", names.get(2));
    }

    @Test
    public void testTokenizeHeader() {
        Expression exp = tokenize("names", ",");
        exp.init(context);

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Willem", names.get(2));
    }

    @Test
    public void testTokenizeBody() {
        Expression exp = tokenize(",");
        exp.init(context);

        exchange.getIn().setBody("Hadrian,Charles");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("Hadrian", names.get(0));
        assertEquals("Charles", names.get(1));
    }

    @Test
    public void testTokenizeBodyRegEx() {
        Expression exp = tokenize("(\\W+)\\s*", true);
        exp.init(context);

        exchange.getIn().setBody("The little fox");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("The", names.get(0));
        assertEquals("little", names.get(1));
        assertEquals("fox", names.get(2));
    }

    @Test
    public void testTokenizeHeaderRegEx() {
        Expression exp = tokenize("quote", "(\\W+)\\s*");
        exp.init(context);

        exchange.getIn().setHeader("quote", "Camel rocks");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("Camel", names.get(0));
        assertEquals("rocks", names.get(1));
    }

    @Test
    public void testTokenizeManualConfiguration() {
        TokenizeLanguage lan = new TokenizeLanguage();
        lan.setCamelContext(context);
        Expression exp = lan.createExpression(null, new Object[] { null, "header:names", "," });
        exp.init(context);

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(3, names.size());

        assertEquals("Claus", names.get(0));
        assertEquals("James", names.get(1));
        assertEquals("Willem", names.get(2));
    }

    @Test
    public void testTokenizePairSpecial() {
        Expression exp = tokenizePair("!", "@", false);
        exp.init(context);

        exchange.getIn().setBody("2011-11-11\n!James@!Claus@\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("James", names.get(0));
        assertEquals("Claus", names.get(1));
    }

    @Test
    public void testTokenizePair() {
        Expression exp = tokenizePair("[START]", "[END]", false);
        exp.init(context);

        exchange.getIn().setBody("2011-11-11\n[START]James[END]\n[START]Claus[END]\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("James", names.get(0));
        assertEquals("Claus", names.get(1));
    }

    @Test
    public void testTokenizePairSimple() {
        Expression exp = tokenizePair("${header.foo}", "${header.bar}", false);
        exp.init(context);

        exchange.getIn().setHeader("foo", "[START]");
        exchange.getIn().setHeader("bar", "[END]");
        exchange.getIn().setBody("2011-11-11\n[START]James[END]\n[START]Claus[END]\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("James", names.get(0));
        assertEquals("Claus", names.get(1));
    }

    @Test
    public void testTokenizePairIncludeTokens() {
        Expression exp = tokenizePair("[START]", "[END]", true);
        exp.init(context);

        exchange.getIn().setBody("2011-11-11\n[START]James[END]\n[START]Claus[END]\n2 records");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(2, names.size());

        assertEquals("[START]James[END]", names.get(0));
        assertEquals("[START]Claus[END]", names.get(1));
    }

    @Test
    public void testTokenizeXMLPair() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn().setBody(
                "<persons><person>James</person><person>Claus</person><person>Jonathan</person><person>Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairSimple() {
        Expression exp = tokenizeXML("${header.foo}", null);
        exp.init(context);

        exchange.getIn().setHeader("foo", "<person>");
        exchange.getIn().setBody(
                "<persons><person>James</person><person>Claus</person><person>Jonathan</person><person>Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairNoXMLTag() {
        Expression exp = tokenizeXML("person", null);
        exp.init(context);

        exchange.getIn().setBody(
                "<persons><person>James</person><person>Claus</person><person>Jonathan</person><person>Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithNoise() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn()
                .setBody("<?xml version=\"1.0\"?><!-- bla bla --><persons>\n<person>James</person>\n<person>Claus</person>\n"
                         + "<!-- more bla bla --><person>Jonathan</person>\n<person>Hadrian</person>\n</persons>   ");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairEmpty() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn().setBody("<?xml version=\"1.0\"?><!-- bla bla --><persons></persons>   ");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(0, names.size());
    }

    @Test
    public void testTokenizeXMLPairNoData() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn().setBody("");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(0, names.size());
    }

    @Test
    public void testTokenizeXMLPairNullData() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn().setBody(null);

        List<?> names = exp.evaluate(exchange, List.class);
        assertNull(names);
    }

    @Test
    public void testTokenizeXMLPairWithSimilarChildNames() {
        Expression exp = tokenizeXML("Trip", "Trips");
        exp.init(context);

        exchange.getIn()
                .setBody("<?xml version='1.0' encoding='UTF-8'?>\n<Trips>\n<Trip>\n<TripType>\n</TripType>\n</Trip>\n</Trips>");
        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(1, names.size());
    }

    @Test
    public void testTokenizeXMLPairWithDefaultNamespace() {
        Expression exp = tokenizeXML("<person>", "<persons>");
        exp.init(context);

        exchange.getIn().setBody(
                "<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person>James</person>\n<person>Claus</person>\n"
                                 + "<person>Jonathan</person>\n<person>Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithDefaultNamespaceNotInherit() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn().setBody(
                "<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person>James</person>\n<person>Claus</person>\n"
                                 + "<person>Jonathan</person>\n<person>Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person>James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person>Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithDefaultAndFooNamespace() {
        Expression exp = tokenizeXML("<person>", "<persons>");
        exp.init(context);

        exchange.getIn().setBody(
                "<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">\n<person>James</person>\n<person>Claus</person>\n"
                                 + "<person>Jonathan</person>\n<person>Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns=\"http:acme.com/persons\" xmlns:foo=\"http:foo.com\">Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithLocalNamespace() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn()
                .setBody(
                        "<?xml version=\"1.0\"?><persons>\n<person xmlns=\"http:acme.com/persons\">James</person>\n<person xmlns=\"http:acme.com/persons\">Claus</person>\n"
                         + "<person xmlns=\"http:acme.com/persons\">Jonathan</person>\n<person xmlns=\"http:acme.com/persons\">Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithLocalAndInheritedNamespace() {
        Expression exp = tokenizeXML("<person>", "<persons>");
        exp.init(context);

        exchange.getIn().setBody(
                "<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person xmlns:foo=\"http:foo.com\">James</person>\n<person>Claus</person>\n"
                                 + "<person>Jonathan</person>\n<person xmlns:bar=\"http:bar.com\">Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns:foo=\"http:foo.com\" xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person xmlns:bar=\"http:bar.com\" xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithLocalAndNotInheritedNamespace() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn().setBody(
                "<?xml version=\"1.0\"?><persons xmlns=\"http:acme.com/persons\">\n<person xmlns:foo=\"http:foo.com\">James</person>\n"
                                 + "<person>Claus</person>\n<person>Jonathan</person>\n<person xmlns:bar=\"http:bar.com\">Hadrian</person>\n</persons>\n");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person xmlns:foo=\"http:foo.com\">James</person>", names.get(0));
        assertEquals("<person>Claus</person>", names.get(1));
        assertEquals("<person>Jonathan</person>", names.get(2));
        assertEquals("<person xmlns:bar=\"http:bar.com\">Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithAttributes() {
        Expression exp = tokenizeXML("<person>", null);
        exp.init(context);

        exchange.getIn()
                .setBody(
                        "<persons><person id=\"1\">James</person><person id=\"2\">Claus</person><person id=\"3\">Jonathan</person>"
                         + "<person id=\"4\">Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person id=\"1\">James</person>", names.get(0));
        assertEquals("<person id=\"2\">Claus</person>", names.get(1));
        assertEquals("<person id=\"3\">Jonathan</person>", names.get(2));
        assertEquals("<person id=\"4\">Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithAttributesInheritNamespace() {
        Expression exp = tokenizeXML("<person>", "<persons>");
        exp.init(context);

        exchange.getIn().setBody(
                "<persons xmlns=\"http:acme.com/persons\"><person id=\"1\">James</person><person id=\"2\">Claus</person>"
                                 + "<person id=\"3\">Jonathan</person><person id=\"4\">Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person id=\"1\" xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person id=\"2\" xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person id=\"3\" xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person id=\"4\" xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

    @Test
    public void testTokenizeXMLPairWithAttributes2InheritNamespace() {
        Expression exp = tokenizeXML("<person>", "<persons>");
        exp.init(context);

        exchange.getIn().setBody(
                "<persons riders=\"true\" xmlns=\"http:acme.com/persons\"><person id=\"1\">James</person><person id=\"2\">Claus</person>"
                                 + "<person id=\"3\">Jonathan</person><person id=\"4\">Hadrian</person></persons>");

        List<?> names = exp.evaluate(exchange, List.class);
        assertEquals(4, names.size());

        assertEquals("<person id=\"1\" xmlns=\"http:acme.com/persons\">James</person>", names.get(0));
        assertEquals("<person id=\"2\" xmlns=\"http:acme.com/persons\">Claus</person>", names.get(1));
        assertEquals("<person id=\"3\" xmlns=\"http:acme.com/persons\">Jonathan</person>", names.get(2));
        assertEquals("<person id=\"4\" xmlns=\"http:acme.com/persons\">Hadrian</person>", names.get(3));
    }

}
