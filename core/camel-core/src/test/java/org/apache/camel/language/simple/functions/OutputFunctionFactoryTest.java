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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutputFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new OutputFunctionFactory();
    }

    // --- pretty( ---

    @Test
    public void testPretty() {
        assertEquals("Hello", evaluate("pretty('Hello')", String.class));

        // XmlPrettyPrinter only emits text content when indent > 1 (i.e., inside a nested element).
        // Text directly inside the root element is silently dropped, so "world!" does not appear.
        assertEquals("<hello id=\"m123\">\n</hello>", evaluate("pretty(${body})", String.class));

        exchange.getMessage().setBody("{\"name\": \"Jack\", \"id\": 123}");
        assertEquals("{\n\t\"name\": \"Jack\",\n\t\"id\": 123\n}\n", evaluate("pretty(${body})", String.class));
    }

    // --- toJson( / toPrettyJson( ---

    @Test
    public void testToJson() {
        // string body is returned as-is
        exchange.getMessage().setBody("Hello");
        assertEquals("Hello", evaluate("toJson(${body})", String.class));

        // map body is serialized to JSON
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Jack");
        map.put("id", 123);
        exchange.getMessage().setBody(map);
        assertEquals("{\"name\":\"Jack\",\"id\":123}", evaluate("toJson(${body})", String.class));
        // pretty mode
        String pretty = Jsoner.prettyPrint("{\"name\":\"Jack\",\"id\":123}");
        assertEquals(pretty, evaluate("toPrettyJson(${body})", String.class));

        // list body is serialized to JSON array
        exchange.getMessage().setBody(List.of("a", "b", "c"));
        assertEquals("[\"a\",\"b\",\"c\"]", evaluate("toJson(${body})", String.class));
        // pretty mode
        pretty = Jsoner.prettyPrint("[\"a\",\"b\",\"c\"]");
        assertEquals(pretty, evaluate("toPrettyJson(${body})", String.class));

        // null body
        exchange.getMessage().setBody(null);
        assertNull(evaluate("toJson(${body})", Object.class));
        // pretty mode
        assertNull(evaluate("toPrettyJson(${body})", Object.class));

        // toJson with a header expression
        exchange.getMessage().setHeader("myNum", 42);
        assertEquals("42", evaluate("toJson(${header.myNum})", String.class));
        // pretty mode
        assertEquals("42", evaluate("toPrettyJson(${header.myNum})", String.class));
    }

    // --- compact( ---

    @Test
    public void testCompact() {
        exchange.getMessage().setBody("<?xml version=\"1.0\"?>\n<order>\n  <id>1</id>\n  <name> Camel </name>\n</order>\n");
        assertEquals("<?xml version=\"1.0\"?><order><id>1</id><name> Camel </name></order>",
                evaluate("compact()", String.class));

        exchange.getMessage().setBody("{\n  \"name\" : \"Camel  in Action\",\n  \"ids\" : [ 1, 2 ]\n}");
        assertEquals("{\"name\":\"Camel  in Action\",\"ids\":[1,2]}", evaluate("compact(${body})", String.class));

        // the inverse of pretty
        exchange.getMessage().setBody("{\"name\": \"Jack\", \"id\": 123}");
        assertEquals("{\"name\":\"Jack\",\"id\":123}", evaluate("compact(${pretty(${body})})", String.class));

        // not XML or JSON, or not well-formed: as-is
        assertEquals("Hello  World", evaluate("compact('Hello  World')", String.class));
        exchange.getMessage().setBody("{\"id\": 1,}");
        assertEquals("{\"id\": 1,}", evaluate("compact()", String.class));
        exchange.getMessage().setBody("<a> <b> </a>");
        assertEquals("<a> <b> </a>", evaluate("compact()", String.class));
        exchange.getMessage().setBody(null);
        assertNull(evaluate("compact()", String.class));
    }

    // --- stripXmlDeclaration( / stripXmlComments( / stripXmlNamespaces( ---

    @Test
    public void testStripXmlDeclaration() {
        exchange.getMessage().setBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<order>\n  <id>1</id>\n</order>");
        assertEquals("<order>\n  <id>1</id>\n</order>", evaluate("stripXmlDeclaration()", String.class));
        exchange.getMessage().setHeader("xml", "<?xml version='1.0'?><a/>");
        assertEquals("<a/>", evaluate("stripXmlDeclaration(${header.xml})", String.class));
        assertEquals("<a/>", evaluate("stripXmlDeclaration('<a/>')", String.class));
    }

    @Test
    public void testStripXmlComments() {
        exchange.getMessage().setBody("<order><!-- internal --><id>1</id><note>a<!-- x -->b</note></order>");
        assertEquals("<order><id>1</id><note>ab</note></order>", evaluate("stripXmlComments()", String.class));
        // not well-formed: as-is
        assertEquals("<a><!-- x --><b></a>", evaluate("stripXmlComments('<a><!-- x --><b></a>')", String.class));
    }

    @Test
    public void testStripXmlNamespaces() {
        exchange.getMessage()
                .setBody("<ns1:Order xmlns:ns1=\"http://example.com\" ns1:id=\"1\"><ns1:Item>Camel</ns1:Item></ns1:Order>");
        assertEquals("<Order id=\"1\"><Item>Camel</Item></Order>", evaluate("stripXmlNamespaces()", String.class));
        assertEquals("<a/>", evaluate("stripXmlNamespaces('<x:a xmlns:x=\"urn:x\"/>')", String.class));
    }

    @Test
    public void testStripXmlRejectsDoctype() {
        // a DOCTYPE is never processed: the value is returned as-is, no entity is expanded
        String xml = "<!DOCTYPE a [<!ENTITY e \"expanded\">]><a><!-- c -->&e;</a>";
        exchange.getMessage().setBody(xml);
        assertEquals(xml, evaluate("stripXmlComments()", String.class));
        assertEquals(xml, evaluate("stripXmlNamespaces()", String.class));
        assertEquals(xml, evaluate("compact()", String.class));
    }

    // --- canonicalXml( ---

    @Test
    public void testCanonicalXml() {
        exchange.getMessage().setBody("<?xml version=\"1.0\"?><order type='a' id='1' xmlns:x=\"urn:unused\"><item/></order>");
        assertEquals("<order id=\"1\" type=\"a\"><item></item></order>", evaluate("canonicalXml()", String.class));
        assertEquals("<order id=\"1\" type=\"a\"><item></item></order>", evaluate("canonicalXml(${body})", String.class));
        assertEquals("<order id=\"1\" type=\"a\"><item></item></order>",
                evaluate("canonicalXml(${body},exclusive)", String.class));
        assertEquals("<order xmlns:x=\"urn:unused\" id=\"1\" type=\"a\"><item></item></order>",
                evaluate("canonicalXml(inclusive)", String.class));
        assertEquals("<order xmlns:x=\"urn:unused\" id=\"1\" type=\"a\"><item></item></order>",
                evaluate("canonicalXml(${body}, inclusive)", String.class));
    }

    @Test
    public void testCanonicalXmlCompare() {
        exchange.getMessage().setBody("<order id=\"1\" type=\"a\">\n<!-- c --><item></item></order>");
        exchange.setVariable("expected", "<order type='a' id='1'>\n<item/></order>");
        assertEquals(Boolean.TRUE,
                evaluate("canonicalXml() == ${canonicalXml(${variable.expected})}", Boolean.class));
    }

    @Test
    public void testCanonicalXmlInvalid() {
        exchange.getMessage().setBody("<order><item></order>");
        ExpressionEvaluationException e
                = assertThrows(ExpressionEvaluationException.class, () -> evaluate("canonicalXml()", String.class));
        assertTrue(e.getMessage().startsWith("Cannot canonicalize the XML"), e.getMessage());

        exchange.getMessage().setBody("<!DOCTYPE a [<!ENTITY e \"expanded\">]><a>&e;</a>");
        assertThrows(ExpressionEvaluationException.class, () -> evaluate("canonicalXml()", String.class));
    }

    @Test
    public void testCanonicalXmlUnknownMode() {
        SimpleParserException e = assertThrows(SimpleParserException.class,
                () -> createFactory().createFunction(context, "canonicalXml(${body},c14n11)", 0));
        assertTrue(e.getMessage().contains("Unknown canonicalXml mode: c14n11"), e.getMessage());
    }
}
