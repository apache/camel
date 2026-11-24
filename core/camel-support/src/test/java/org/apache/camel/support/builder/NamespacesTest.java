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
package org.apache.camel.support.builder;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespacesTest {

    @Test
    void createsTheMappingsOfAString() {
        assertThat(new Namespaces("c=http://acme.com/cheese,w=http://acme.com/wine").getNamespaces())
                .containsExactlyInAnyOrderEntriesOf(Map.of("c", "http://acme.com/cheese", "w", "http://acme.com/wine"));
    }

    @Test
    void ignoresWhitespaceAndAcceptsNewLines() {
        assertThat(new Namespaces(" c = http://acme.com/cheese ,\n w=urn:acme:wine\n").getNamespaces())
                .containsExactlyInAnyOrderEntriesOf(Map.of("c", "http://acme.com/cheese", "w", "urn:acme:wine"));
    }

    @Test
    void keepsAnEqualsSignInTheUri() {
        assertThat(new Namespaces("q=http://acme.com/ns?version=2").getNamespaces())
                .containsExactlyEntriesOf(Map.of("q", "http://acme.com/ns?version=2"));
    }

    @Test
    void anEmptyStringHasNoMappings() {
        assertThat(new Namespaces("").getNamespaces()).isEmpty();
        assertThat(new Namespaces(" , ").getNamespaces()).isEmpty();
        assertThat(new Namespaces((String) null).getNamespaces()).isEmpty();
    }

    @Test
    void failsOnAMappingWithoutPrefixOrUri() {
        assertThatThrownBy(() -> new Namespaces("http://acme.com/cheese"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid namespace mapping: http://acme.com/cheese");
        assertThatThrownBy(() -> new Namespaces("=http://acme.com/cheese"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Namespaces("c="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
