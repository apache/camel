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
package org.apache.camel.dsl.yaml.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.yaml.common.DataFormatKeyHints;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24847: the alias table the runtime deserializer uses without a catalog must agree with the catalog: every data
 * format whose name differs from its model name is in it, with the model name as the key and a value of the option that
 * selects the library or type.
 */
public class DataFormatKeyHintsCatalogTest {

    private final CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    public void testEveryAliasedDataFormatOfTheCatalogIsInTheTable() {
        List<String> aliased = new ArrayList<>();
        for (String name : catalog.findDataFormatNames()) {
            DataFormatModel df = catalog.dataFormatModel(name);
            if (df.getModelName().equals(name)) {
                assertThat(DataFormatKeyHints.alias(name)).as(name + " is its own key").isNull();
                continue;
            }
            aliased.add(name);
            DataFormatKeyHints.Alias alias = DataFormatKeyHints.alias(name);
            assertThat(alias).as(name + " maps to " + df.getModelName()).isNotNull();
            assertThat(alias.key()).as(name).isEqualTo(df.getModelName());
            EipModel model = catalog.eipModel(df.getModelName());
            EipModel.EipOptionModel selector = model.getOptions().stream()
                    .filter(o -> o.getEnums() != null && o.getEnums().size() > 1
                            && (o.getName().equals("library") || o.getName().equals("type")))
                    .findFirst().orElse(null);
            if (selector == null) {
                assertThat(alias.option()).as(name + " has no library or type to select").isNull();
            } else {
                assertThat(alias.option()).as(name).isEqualTo(selector.getName());
                assertThat(selector.getEnums()).as(name + " " + selector.getName()).contains(alias.value());
            }
        }
        // and nothing in the table that the catalog does not have: a misspelled key would keep the sizes equal
        assertThat(DataFormatKeyHints.ALIASES.keySet()).as("every alias is the normalized name of a catalog data format")
                .isSubsetOf(aliased.stream().map(DataFormatKeyHints::normalize).collect(Collectors.toSet()));
        assertThat(DataFormatKeyHints.ALIASES).as("aliases without a catalog data format: " + aliased)
                .hasSize(aliased.size());
    }

    @Test
    public void testSpellingsOfAnAlias() {
        assertThat(DataFormatKeyHints.alias("jackson")).isEqualTo(DataFormatKeyHints.ALIASES.get("jackson"));
        assertThat(DataFormatKeyHints.alias("Jackson")).isEqualTo(DataFormatKeyHints.ALIASES.get("jackson"));
        assertThat(DataFormatKeyHints.alias("json-jackson")).isEqualTo(DataFormatKeyHints.ALIASES.get("jackson"));
        assertThat(DataFormatKeyHints.alias("jackson-json")).isEqualTo(DataFormatKeyHints.ALIASES.get("jackson"));
        assertThat(DataFormatKeyHints.alias("jackson-avro")).isEqualTo(DataFormatKeyHints.ALIASES.get("avrojackson"));
        assertThat(DataFormatKeyHints.alias("bindy_csv")).isEqualTo(DataFormatKeyHints.ALIASES.get("bindycsv"));
        assertThat(DataFormatKeyHints.alias("snakeYaml")).isEqualTo(DataFormatKeyHints.ALIASES.get("snakeyaml"));
        // a library of another model is not an alias of this one
        assertThat(DataFormatKeyHints.alias("yaml-jackson")).isNull();
        assertThat(DataFormatKeyHints.alias("json")).isNull();
        assertThat(DataFormatKeyHints.alias("jacksonXml")).isNull();
        assertThat(DataFormatKeyHints.form("gson")).isEqualTo("json: {library: Gson}");
        assertThat(DataFormatKeyHints.form("jacksonXml")).isEqualTo("jacksonXml");
    }
}
