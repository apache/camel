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
package org.apache.camel.language.xtokenizer;

import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An xtokenize expression with its namespaces inline in one string, the namespaceMappings option.
 */
class XMLTokenizeNamespaceMappingsTest extends CamelTestSupport {

    private static final String MAPPINGS = "c=http://acme.com/cheese,w=http://acme.com/wine";

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("otherNamespaces", new Namespaces("c", "http://acme.com/other").add("w", "http://acme.com/other"));
    }

    @Test
    void usesTheMappingsOfTheString() {
        Iterator<?> cheese = tokenize(definition("//c:order", MAPPINGS),
                "<orders xmlns:c=\"http://acme.com/cheese\"><c:order>one</c:order></orders>");
        Iterator<?> wine = tokenize(definition("//w:order", MAPPINGS),
                "<orders xmlns:w=\"http://acme.com/wine\"><w:order>two</w:order></orders>");

        assertTrue(cheese.hasNext());
        assertTrue(String.valueOf(cheese.next()).contains("one"));
        assertTrue(wine.hasNext());
        assertTrue(String.valueOf(wine.next()).contains("two"));

        // matching local name, wrong namespace URI
        Iterator<?> water = tokenize(definition("//c:order", MAPPINGS),
                "<orders xmlns:c=\"http://acme.com/water\"><c:order>three</c:order></orders>");
        assertFalse(water.hasNext());
    }

    @Test
    void resolvesAPropertyPlaceholder() {
        context.getPropertiesComponent().addOverrideProperty("namespaces", MAPPINGS);

        Iterator<?> wine = tokenize(definition("//w:order", "{{namespaces}}"),
                "<orders xmlns:w=\"http://acme.com/wine\"><w:order>two</w:order></orders>");

        assertTrue(wine.hasNext());
        assertTrue(String.valueOf(wine.next()).contains("two"));
    }

    @Test
    void winsOverNamespacesRef() {
        XMLTokenizerExpression definition = definition("//w:order", MAPPINGS);
        definition.setNamespacesRef("otherNamespaces");

        Iterator<?> wine = tokenize(definition,
                "<orders xmlns:w=\"http://acme.com/wine\"><w:order>two</w:order></orders>");

        assertTrue(wine.hasNext());
        assertTrue(String.valueOf(wine.next()).contains("two"));
    }

    private static XMLTokenizerExpression definition(String expression, String namespaceMappings) {
        XMLTokenizerExpression definition = new XMLTokenizerExpression(expression);
        definition.setNamespaceMappings(namespaceMappings);
        return definition;
    }

    private Iterator<?> tokenize(XMLTokenizerExpression definition, String body) {
        Expression exp = definition.createExpression(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);

        return assertInstanceOf(Iterator.class, exp.evaluate(exchange, Object.class));
    }
}
