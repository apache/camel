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

import java.util.List;
import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Predicate;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.builder.Namespaces;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An xpath expression with its namespaces inline in one string, the namespaceMappings option.
 */
class XPathNamespaceMappingsTest extends ContextTestSupport {

    private static final String ORDER
            = "<order xmlns=\"http://acme.com/cheese\"><wine xmlns=\"http://acme.com/wine\">Rioja</wine></order>";

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties properties = new Properties();
        properties.setProperty("namespaces", "c=http://acme.com/cheese,w=http://acme.com/wine");
        return properties;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("otherNamespaces", new Namespaces("c", "http://acme.com/other").add("w", "http://acme.com/other"));
    }

    @Test
    void usesTheMappingsOfTheString() {
        XPathExpression definition = definition("/c:order/w:wine = 'Rioja'");
        definition.setNamespaceMappings("c=http://acme.com/cheese,w=http://acme.com/wine");
        Predicate predicate = definition.createPredicate(context);

        assertThat(predicate.matches(createExchangeWithBody(ORDER))).isTrue();
        // the wine element in the cheese namespace
        assertThat(
                predicate.matches(createExchangeWithBody("<order xmlns=\"http://acme.com/cheese\"><wine>Rioja</wine></order>")))
                .isFalse();
    }

    @Test
    void resolvesAPropertyPlaceholder() {
        XPathExpression definition = definition("/c:order/w:wine = 'Rioja'");
        definition.setNamespaceMappings("{{namespaces}}");

        assertThat(definition.createPredicate(context).matches(createExchangeWithBody(ORDER))).isTrue();
    }

    @Test
    void aNamespaceEntryWinsForTheSamePrefix() {
        XPathExpression definition = definition("/c:order/w:wine = 'Rioja'");
        definition.setNamespaceMappings("c=http://acme.com/cheese,w=http://acme.com/other");
        definition.setNamespace(List.of(new PropertyDefinition("w", "http://acme.com/wine")));

        assertThat(definition.createPredicate(context).matches(createExchangeWithBody(ORDER))).isTrue();
    }

    @Test
    void winsOverNamespacesRef() {
        XPathExpression definition = definition("/c:order/w:wine = 'Rioja'");
        definition.setNamespaceMappings("c=http://acme.com/cheese,w=http://acme.com/wine");
        definition.setNamespacesRef("otherNamespaces");

        assertThat(definition.createPredicate(context).matches(createExchangeWithBody(ORDER))).isTrue();
    }

    @Test
    void anEmptyStringFallsBackToNamespacesRef() {
        XPathExpression definition = definition("/c:order/w:wine = 'Rioja'");
        definition.setNamespaceMappings("");
        definition.setNamespacesRef("otherNamespaces");

        // the namespaces of the bean do not match the message
        assertThat(definition.createPredicate(context).matches(createExchangeWithBody(ORDER))).isFalse();
    }

    @Test
    void failsOnAnInvalidMapping() {
        XPathExpression definition = definition("/c:order");
        definition.setNamespaceMappings("http://acme.com/cheese");

        assertThatThrownBy(() -> definition.createPredicate(context))
                .hasStackTraceContaining("Invalid namespace mapping: http://acme.com/cheese");
    }

    private static XPathExpression definition(String expression) {
        XPathExpression definition = new XPathExpression(expression);
        definition.setResultQName("BOOLEAN");
        return definition;
    }
}
