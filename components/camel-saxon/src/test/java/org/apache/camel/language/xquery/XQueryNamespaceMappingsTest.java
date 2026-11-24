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
 * An xquery expression with its namespaces inline in one string, the namespaceMappings option.
 */
class XQueryNamespaceMappingsTest extends CamelTestSupport {

    private static final String MAPPINGS = "c=http://acme.com/cheese,w=http://acme.com/wine";

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("otherNamespaces", new Namespaces("c", "http://acme.com/other").add("w", "http://acme.com/other"));
    }

    @Test
    void usesTheMappingsOfTheString() {
        Predicate cheese = predicate("/c:person[@name='James']", MAPPINGS);
        Predicate wine = predicate("/w:person[@name='James']", MAPPINGS);

        assertTrue(cheese.matches(exchange("<person xmlns=\"http://acme.com/cheese\" name=\"James\"/>")));
        assertTrue(wine.matches(exchange("<person xmlns=\"http://acme.com/wine\" name=\"James\"/>")));

        // correct value, wrong namespace URI
        assertFalse(wine.matches(exchange("<person xmlns=\"http://acme.com/water\" name=\"James\"/>")));
    }

    @Test
    void resolvesAPropertyPlaceholder() {
        context.getPropertiesComponent().addOverrideProperty("namespaces", MAPPINGS);

        Predicate wine = predicate("/w:person[@name='James']", "{{namespaces}}");

        assertTrue(wine.matches(exchange("<person xmlns=\"http://acme.com/wine\" name=\"James\"/>")));
    }

    @Test
    void winsOverNamespacesRef() {
        XQueryExpression definition = new XQueryExpression("/w:person[@name='James']");
        definition.setNamespaceMappings(MAPPINGS);
        definition.setNamespacesRef("otherNamespaces");
        Predicate wine = definition.createPredicate(context);

        assertTrue(wine.matches(exchange("<person xmlns=\"http://acme.com/wine\" name=\"James\"/>")));
    }

    private Predicate predicate(String expression, String namespaceMappings) {
        XQueryExpression definition = new XQueryExpression(expression);
        definition.setNamespaceMappings(namespaceMappings);
        return definition.createPredicate(context);
    }

    private Exchange exchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }
}
