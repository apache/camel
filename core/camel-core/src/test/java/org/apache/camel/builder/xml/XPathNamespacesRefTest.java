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
package org.apache.camel.builder.xml;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Predicate;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.builder.Namespaces;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that an xpath expression can reference a {@link Namespaces} bean from the registry via namespacesRef, instead
 * of declaring the namespaces inline.
 */
class XPathNamespacesRefTest extends ContextTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myNamespaces", new Namespaces("c", "http://acme.com/cheese").add("w", "http://acme.com/wine"));
    }

    @Test
    void testXPathWithNamespacesRef() {
        Predicate cheese = predicate("/c:number = 55");
        Predicate wine = predicate("/w:number = 77");

        // 1 positive example per namespace
        assertThat(cheese.matches(createExchangeWithBody("<number xmlns=\"http://acme.com/cheese\">55</number>"))).isTrue();
        assertThat(wine.matches(createExchangeWithBody("<number xmlns=\"http://acme.com/wine\">77</number>"))).isTrue();

        // negative, data-wise: correct namespace, wrong value
        assertThat(cheese.matches(createExchangeWithBody("<number xmlns=\"http://acme.com/cheese\">99</number>"))).isFalse();

        // negative, namespace-wise: correct value, wrong namespace URI
        assertThat(wine.matches(createExchangeWithBody("<number xmlns=\"http://acme.com/water\">77</number>"))).isFalse();
    }

    private Predicate predicate(String expression) {
        XPathExpression definition = new XPathExpression(expression);
        definition.setNamespacesRef("myNamespaces");
        definition.setResultQName("BOOLEAN");
        return definition.createPredicate(context);
    }
}
