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
package org.apache.camel.language.xquery;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.XQueryExpression;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that an xquery expression can reference a {@link Namespaces} bean from the registry via namespacesRef, instead
 * of declaring the namespaces inline.
 */
class XQueryNamespacesRefTest extends CamelTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myNamespaces", new Namespaces("c", "http://acme.com/cheese").add("w", "http://acme.com/wine"));
    }

    @Test
    void testXQueryWithNamespacesRef() {
        Predicate cheese = predicate("/c:person[@name='James']");
        Predicate wine = predicate("/w:person[@name='James']");

        // 1 positive example per namespace
        assertTrue(cheese.matches(exchange("<person xmlns=\"http://acme.com/cheese\" name=\"James\"/>")));
        assertTrue(wine.matches(exchange("<person xmlns=\"http://acme.com/wine\" name=\"James\"/>")));

        // negative, data-wise: correct namespace, wrong value
        assertFalse(cheese.matches(exchange("<person xmlns=\"http://acme.com/cheese\" name=\"Claus\"/>")));

        // negative, namespace-wise: correct value, wrong namespace URI
        assertFalse(wine.matches(exchange("<person xmlns=\"http://acme.com/water\" name=\"James\"/>")));
    }

    private Predicate predicate(String expression) {
        XQueryExpression definition = new XQueryExpression(expression);
        definition.setNamespacesRef("myNamespaces");
        return definition.createPredicate(context);
    }

    private Exchange exchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }
}
