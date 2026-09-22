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
package org.apache.camel.dsl.yaml.common;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The hint for a {@code marshal}/{@code unmarshal} key that names a data format the way its artifact or the catalog
 * does (jackson, json-jackson, jackson-xml, snake-yaml) instead of by its YAML key (json with library Jackson,
 * jacksonXml, yaml).
 * <p>
 * The YAML key is the data format's model name. Ten data formats in the catalog have a name of their own that differs
 * from it, because one model serves several libraries or types: {@link #ALIASES} lists them with the option that
 * selects the library or type. The table mirrors the catalog ({@code name} and {@code modelName} of each data format,
 * and the enum of the {@code library} or {@code type} option of the model); the validator's tests check the two agree.
 * The deserializer cannot read the catalog at runtime, which is why the table is here rather than derived.
 */
public final class DataFormatKeyHints {

    /** The YAML key of an aliased data format and the option that selects it: json with library Jackson. */
    public record Alias(String key, String option, String value) {

        /** The key with the option, as it is written in YAML: json: {library: Jackson}, or yaml: {...}. */
        public String form() {
            return option != null ? key + ": {" + option + ": " + value + "}" : key + ": {...}";
        }
    }

    /**
     * The catalog data formats whose name is not their YAML key, by name in lower case with no separators, so that
     * jackson, Jackson, snake-yaml and snakeYaml all match.
     */
    public static final Map<String, Alias> ALIASES = Map.ofEntries(
            Map.entry("jackson", new Alias("json", "library", "Jackson")),
            Map.entry("gson", new Alias("json", "library", "Gson")),
            Map.entry("jsonb", new Alias("json", "library", "Jsonb")),
            Map.entry("fastjson", new Alias("json", "library", "Fastjson")),
            Map.entry("avrojackson", new Alias("avro", "library", "Jackson")),
            Map.entry("protobufjackson", new Alias("protobuf", "library", "Jackson")),
            Map.entry("snakeyaml", new Alias("yaml", null, null)),
            Map.entry("bindycsv", new Alias("bindy", "type", "Csv")),
            Map.entry("bindyfixed", new Alias("bindy", "type", "Fixed")),
            Map.entry("bindykvp", new Alias("bindy", "type", "KeyValue")));

    /** The models an alias belongs to, which people prefix or suffix the library with: json-jackson, jackson-json. */
    private static final List<String> MODELS = List.of("json", "avro", "protobuf", "yaml", "bindy");

    private DataFormatKeyHints() {
    }

    /**
     * The hint for a key that is not a data format key, or null when the key is neither a spelling of one of the known
     * keys nor an alias.
     *
     * @param  key       the key as written: jackson, json-jackson, jackson-xml, JSON
     * @param  knownKeys the data format keys of marshal/unmarshal: json, jacksonXml, yaml...
     * @return           the hint: did you mean 'jacksonXml'? for a spelling of a key, the data format is json, Jackson
     *                   is its library: write json: {library: Jackson} for an alias, or null
     */
    public static String hint(String key, Collection<String> knownKeys) {
        String normalized = normalize(key);
        // jackson-xml, JSON, base-64: the key itself, spelled differently
        for (String known : knownKeys) {
            if (!known.equals(key) && normalized.equals(normalize(known))) {
                return "did you mean '" + known + "'?";
            }
        }
        Alias alias = alias(key);
        return alias != null ? hint(alias) : null;
    }

    /**
     * The alias a spelling of a data format name refers to, or null: jackson and json-jackson give json with library
     * Jackson, bindy-csv gives bindy with type Csv.
     */
    public static Alias alias(String name) {
        String normalized = normalize(name);
        Alias alias = ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }
        // json-jackson, jackson-json: the model around the library; jackson-avro: the library of another model
        for (String model : MODELS) {
            String rest = null;
            if (normalized.startsWith(model) && normalized.length() > model.length()) {
                rest = normalized.substring(model.length());
            } else if (normalized.endsWith(model) && normalized.length() > model.length()) {
                rest = normalized.substring(0, normalized.length() - model.length());
            }
            if (rest == null) {
                continue;
            }
            alias = ALIASES.get(model + rest);
            if (alias == null) {
                alias = ALIASES.get(rest);
                if (alias != null && !alias.key().equals(model)) {
                    alias = null;
                }
            }
            if (alias != null) {
                return alias;
            }
        }
        return null;
    }

    /**
     * The key with the option that selects a data format, as it is written in YAML: json: {library: Jackson} for
     * jackson, jacksonXml for jacksonXml.
     */
    public static String form(String name) {
        Alias alias = alias(name);
        return alias != null ? alias.form() : name;
    }

    private static String hint(Alias alias) {
        if (alias.option() == null) {
            return "the data format is " + alias.key() + ": write " + alias.form();
        }
        return "the data format is " + alias.key() + ", " + alias.value() + " is its " + alias.option() + ": write "
               + alias.form();
    }

    /** Lower case with no separators: json-jackson, json_jackson, jsonJackson and JSON-Jackson are one name. */
    public static String normalize(String name) {
        return name.replaceAll("[-_ .]", "").toLowerCase(Locale.ROOT);
    }
}
