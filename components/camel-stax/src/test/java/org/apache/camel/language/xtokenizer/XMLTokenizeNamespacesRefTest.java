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
 * Tests that an xtokenize expression can reference a {@link Namespaces} bean from the registry via namespacesRef,
 * instead of declaring the namespaces inline.
 */
class XMLTokenizeNamespacesRefTest extends CamelTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myNamespaces", new Namespaces("c", "http://acme.com/cheese").add("w", "http://acme.com/wine"));
    }

    @Test
    void testXTokenizeWithNamespacesRef() {
        Iterator<?> cheese
                = tokenize("//c:order", "<orders xmlns:c=\"http://acme.com/cheese\"><c:order>one</c:order></orders>");
        Iterator<?> wine = tokenize("//w:order", "<orders xmlns:w=\"http://acme.com/wine\"><w:order>two</w:order></orders>");

        // 1 positive example per namespace
        assertTrue(cheese.hasNext());
        assertTrue(String.valueOf(cheese.next()).contains("one"));
        assertTrue(wine.hasNext());
        assertTrue(String.valueOf(wine.next()).contains("two"));

        // negative, namespace-wise: matching local name, wrong namespace URI
        Iterator<?> water
                = tokenize("//c:order", "<orders xmlns:c=\"http://acme.com/water\"><c:order>three</c:order></orders>");
        assertFalse(water.hasNext());
    }

    private Iterator<?> tokenize(String expression, String body) {
        XMLTokenizerExpression definition = new XMLTokenizerExpression(expression);
        definition.setNamespacesRef("myNamespaces");
        Expression exp = definition.createExpression(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);

        return assertInstanceOf(Iterator.class, exp.evaluate(exchange, Object.class));
    }
}
