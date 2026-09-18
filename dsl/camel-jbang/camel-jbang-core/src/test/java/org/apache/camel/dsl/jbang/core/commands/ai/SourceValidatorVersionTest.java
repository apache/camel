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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.DefaultRuntimeProvider;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The validation of a source for another Camel version or runtime than the CLI's own (CAMEL-24711): the schema of that
 * version, and a component the runtime has no extension for.
 */
class SourceValidatorVersionTest {

    private static final String ROUTE = """
            - from:
                uri: timer:tick
                steps:
                  - to: kafka:orders
                  - to: log:done
            """;

    /** A catalog that knows timer and log only, the way the camel-quarkus-catalog knows the extensions. */
    static final class FakeQuarkusProvider extends DefaultRuntimeProvider {
        @Override
        public String getProviderName() {
            return "quarkus";
        }

        @Override
        public String getComponentJSonSchemaDirectory() {
            return "org/apache/camel/dsl/jbang/core/commands/ai/fakequarkus/components";
        }
    }

    @Test
    void aComponentWithoutAnExtensionIsAnErrorOnTheRuntimeCatalog() {
        CamelCatalog quarkus = new DefaultCamelCatalog();
        quarkus.setRuntimeProvider(new FakeQuarkusProvider());

        List<String> msgs = SourceValidator.validateYamlEndpoints(ROUTE, quarkus);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("kafka: Camel Quarkus has no extension").contains("camel-quarkus-kafka");

        // the plain catalog says nothing about a scheme it does not know: a project can register a component of its own
        assertThat(SourceValidator.validateYamlEndpoints(ROUTE.replace("kafka:orders", "mine:orders"),
                new DefaultCamelCatalog())).isEmpty();
        // and neither does the runtime catalog, only Camel components without an extension are reported
        assertThat(SourceValidator.validateYamlEndpoints(ROUTE.replace("kafka:orders", "mine:orders"), quarkus)).isEmpty();
    }

    @Test
    void theBuiltInCatalogUsesTheBuiltInSchema() throws Exception {
        YamlValidator v = SourceValidator.yamlValidator(new DefaultCamelCatalog());
        assertThat(v).isSameAs(SourceValidator.yamlValidator());
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires the camel-yaml-dsl jar of another version")
    void aCatalogOfAnotherVersionGetsTheSchemaOfThatVersion() throws Exception {
        CamelCatalog older = CatalogLoader.loadCatalog(null, "4.18.0", true);
        assertThat(older.getCatalogVersion()).isEqualTo("4.18.0");

        YamlValidator v = SourceValidator.yamlValidator(older);
        assertThat(v).isNotSameAs(SourceValidator.yamlValidator());
        assertThat(SourceValidator.yamlValidator(older)).as("one validator per version").isSameAs(v);

        // a route both versions accept validates; the schema read from the 4.18.0 jar is the one that answers
        assertThat(SourceValidator.validateCamelYaml(ROUTE, older)).isEmpty();
        assertThat(CatalogLoader.loadYamlDslSchema(null, "4.18.0", false, true)).contains("\"$schema\"");
        // the CLI's own version has its schema on the classpath: nothing to read
        assertThat(CatalogLoader.loadYamlDslSchema(null, new DefaultCamelCatalog().getCatalogVersion(), false, true))
                .isNull();
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                              disabledReason = "Runs only local — requires the camel-yaml-dsl jar of another version")
    void theCanonicalSchemaExistsFromCamel422() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CatalogLoader.loadYamlDslSchema(null, "4.18.0", true, true))
                .hasMessageContaining("no canonical YAML DSL schema")
                .hasMessageContaining("4.22");
    }
}
