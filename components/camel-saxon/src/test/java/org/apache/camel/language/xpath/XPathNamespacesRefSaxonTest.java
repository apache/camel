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
package org.apache.camel.language.xpath;

import net.sf.saxon.xpath.XPathFactoryImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.builder.Namespaces;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests namespacesRef with Saxon, which resolves namespace prefixes when the expression is compiled rather than when it
 * is evaluated. The referenced {@link Namespaces} bean must therefore be resolved before the expression is built, not
 * afterwards, or compilation fails with "Namespace prefix has not been declared".
 */
class XPathNamespacesRefSaxonTest extends CamelTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myNamespaces", new Namespaces("c", "http://acme.com/cheese").add("w", "http://acme.com/wine"));
        registry.bind("saxonFactory", new XPathFactoryImpl());
    }

    @Test
    void testXPathWithNamespacesRef() {
        Predicate cheese = predicate("/c:number = 55");
        Predicate wine = predicate("/w:number = 77");

        // 1 positive example per namespace
        assertTrue(cheese.matches(exchange("<number xmlns=\"http://acme.com/cheese\">55</number>")));
        assertTrue(wine.matches(exchange("<number xmlns=\"http://acme.com/wine\">77</number>")));

        // negative, data-wise: correct namespace, wrong value
        assertFalse(cheese.matches(exchange("<number xmlns=\"http://acme.com/cheese\">99</number>")));

        // negative, namespace-wise: correct value, wrong namespace URI
        assertFalse(wine.matches(exchange("<number xmlns=\"http://acme.com/water\">77</number>")));
    }

    @Test
    void testXPathWithNamespacesRefAndResultType() {
        // a resultType makes the language return a converting wrapper rather than the NamespaceAware builder,
        // so the referenced namespaces must already be applied when the expression is built
        XPathExpression definition = new XPathExpression("/c:number");
        definition.setNamespacesRef("myNamespaces");
        definition.setResultType(String.class);
        definition.setFactoryRef("saxonFactory");
        Expression expression = definition.createExpression(context);

        assertEquals("55", expression.evaluate(exchange("<number xmlns=\"http://acme.com/cheese\">55</number>"), String.class));
    }

    private Predicate predicate(String expression) {
        XPathExpression definition = new XPathExpression(expression);
        definition.setNamespacesRef("myNamespaces");
        definition.setResultQName("BOOLEAN");
        definition.setFactoryRef("saxonFactory");
        return definition.createPredicate(context);
    }

    private Exchange exchange(String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }
}
