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

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.NonValidationKeyword;
import com.networknt.schema.path.NodePath;
import com.networknt.schema.path.PathType;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.EipModel;

/**
 * YAML DSL validator that tooling can use to validate Camel source files if they can be parsed and are valid according
 * to the Camel YAML DSL spec.
 */
public class YamlValidator {

    private static final String LOCATION = "/schema/camelYamlDsl.json";
    private static final String LOCATION_CANONICAL = "/schema/camelYamlDsl-canonical.json";

    /**
     * A handful of "pick exactly one" EIP option groups (see {@link EipModel.EipOptionModel#getOneOfs()}) flatten their
     * alternatives directly onto a specific host node instead of appearing under a wrapper key named after the option
     * itself (that's how "expression" works). The canonical schema cannot express this "exactly one of" cardinality (it
     * has no oneOf/anyOf constructs), so {@link #checkOneOfCardinality} re-checks it here, driven by the same catalog
     * metadata the classic schema is generated from.
     */
    private static final Map<String, Set<String>> FLATTENED_HOSTS = Map.of(
            "dataFormatType", Set.of("marshal", "unmarshal"),
            "errorHandlerType", Set.of("errorHandler"),
            "tokenizerImplementation", Set.of("tokenizer"),
            "resequencerConfig", Set.of("resequence"),
            "loadBalancerType", Set.of("loadBalance"));

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final boolean canonical;
    private Schema schema;
    private Map<String, OneOfGroup> oneOfGroups;

    private record OneOfGroup(Set<String> alternatives, boolean required) {
    }

    public YamlValidator() {
        this(false);
    }

    public YamlValidator(boolean canonical) {
        this.canonical = canonical;
    }

    public boolean isCanonical() {
        return canonical;
    }

    public List<Error> validate(File file) throws Exception {
        if (schema == null) {
            init();
        }
        try {
            var target = mapper.readTree(file);
            return validate(target);
        } catch (Exception e) {
            return List.of(parseError(e));
        }
    }

    public List<Error> validate(String content) throws Exception {
        if (schema == null) {
            init();
        }
        try {
            var target = mapper.readTree(content);
            return validate(target);
        } catch (Exception e) {
            return List.of(parseError(e));
        }
    }

    private List<Error> validate(JsonNode target) {
        var errors = filterOneOfNoise(new ArrayList<>(schema.validate(target)));
        if (canonical) {
            checkOneOfCardinality(target, new NodePath(PathType.JSON_POINTER), errors);
        }
        return errors;
    }

    /**
     * Filters noise from {@code oneOf} validation. When a {@code oneOf} has N branches and none match, the validator
     * reports errors from ALL branches — producing dozens of "required property 'X' not found" messages for branches
     * the user never intended. This method keeps only the errors from the branch that matched the user's YAML most
     * closely (deepest structural match) and drops the rest.
     */
    static List<Error> filterOneOfNoise(List<Error> errors) {
        if (errors.size() <= 1) {
            return errors;
        }

        List<Error> oneOfMetas = errors.stream()
                .filter(e -> "oneOf".equals(e.getKeyword()))
                .sorted(Comparator.comparingInt(
                        (Error e) -> e.getEvaluationPath().toString().length()).reversed())
                .toList();

        if (oneOfMetas.isEmpty()) {
            return errors;
        }

        Set<Error> toRemove = new LinkedHashSet<>();

        for (Error meta : oneOfMetas) {
            if (toRemove.contains(meta)) {
                continue;
            }

            String prefix = meta.getEvaluationPath().toString();

            Map<String, List<Error>> branches = new LinkedHashMap<>();
            for (Error e : errors) {
                if (toRemove.contains(e) || e == meta) {
                    continue;
                }
                String path = e.getEvaluationPath().toString();
                if (path.startsWith(prefix + "/")) {
                    String rest = path.substring(prefix.length() + 1);
                    String branchIndex = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                    branches.computeIfAbsent(branchIndex, k -> new ArrayList<>()).add(e);
                }
            }

            if (branches.isEmpty()) {
                continue;
            }

            // find the best-matching branch using a three-tier priority:
            //   1. property-level errors (additionalProperties, enum, pattern, etc.) — "right branch, wrong value/property"
            //   2. type errors only — "wrong branch entirely" (less informative)
            //   3. structural errors only (required, oneOf, not) — wrong-branch noise
            // within the same tier, prefer the deepest instance location
            String bestBranch = null;
            int bestDepth = -1;
            int bestTier = 0;

            for (Map.Entry<String, List<Error>> entry : branches.entrySet()) {
                int tier = branchTier(entry.getValue());
                int maxDepth = entry.getValue().stream()
                        .mapToInt(e -> e.getInstanceLocation().toString().length())
                        .max().orElse(0);

                if (tier > bestTier) {
                    bestBranch = entry.getKey();
                    bestDepth = maxDepth;
                    bestTier = tier;
                } else if (tier == bestTier && maxDepth > bestDepth) {
                    bestBranch = entry.getKey();
                    bestDepth = maxDepth;
                }
            }

            for (Map.Entry<String, List<Error>> entry : branches.entrySet()) {
                if (!entry.getKey().equals(bestBranch)) {
                    toRemove.addAll(entry.getValue());
                }
            }

            if (bestTier > 0) {
                toRemove.add(meta);
            }
        }

        if (toRemove.isEmpty()) {
            return errors;
        }

        List<Error> result = new ArrayList<>(errors.size() - toRemove.size());
        for (Error e : errors) {
            if (!toRemove.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }

    private static int branchTier(List<Error> branchErrors) {
        boolean hasPropertyLevel = false;
        boolean hasType = false;
        for (Error e : branchErrors) {
            String kw = e.getKeyword();
            if (isPropertyLevelError(kw)) {
                hasPropertyLevel = true;
            } else if ("type".equals(kw)) {
                hasType = true;
            }
        }
        if (hasPropertyLevel) {
            return 2;
        }
        if (hasType) {
            return 1;
        }
        return 0;
    }

    private static boolean isPropertyLevelError(String keyword) {
        return keyword != null
                && ("additionalProperties".equals(keyword)
                        || "enum".equals(keyword)
                        || "pattern".equals(keyword)
                        || "minimum".equals(keyword)
                        || "maximum".equals(keyword)
                        || "minLength".equals(keyword)
                        || "maxLength".equals(keyword)
                        || "format".equals(keyword)
                        || "const".equals(keyword)
                        || "minItems".equals(keyword)
                        || "maxItems".equals(keyword));
    }

    /**
     * Recursively walks the parsed YAML tree looking for the host nodes/wrapper keys of a "pick exactly one" option
     * group, and reports a synthetic error when zero (for a required group) or more than one alternative is present.
     */
    private void checkOneOfCardinality(JsonNode node, NodePath path, List<Error> errors) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                NodePath childPath = path.append(key);
                if (value.isObject()) {
                    for (Map.Entry<String, Set<String>> hostEntry : FLATTENED_HOSTS.entrySet()) {
                        if (hostEntry.getValue().contains(key)) {
                            reportIfInvalidCardinality(value, oneOfGroups.get(hostEntry.getKey()), childPath, errors);
                        }
                    }
                    if (!FLATTENED_HOSTS.containsKey(key)) {
                        reportIfInvalidCardinality(value, oneOfGroups.get(key), childPath, errors);
                    }
                }
                checkOneOfCardinality(value, childPath, errors);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                checkOneOfCardinality(node.get(i), path.append(i), errors);
            }
        }
    }

    private static void reportIfInvalidCardinality(JsonNode container, OneOfGroup group, NodePath path, List<Error> errors) {
        if (group == null) {
            return;
        }
        List<String> found = new ArrayList<>();
        Iterator<String> names = container.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (group.alternatives().contains(name)) {
                found.add(name);
            }
        }
        if (found.isEmpty() && group.required()) {
            errors.add(Error.builder()
                    .keyword("oneOf")
                    .instanceLocation(path)
                    .message("must have exactly one of " + group.alternatives() + " but found none")
                    .build());
        } else if (found.size() > 1) {
            errors.add(Error.builder()
                    .keyword("oneOf")
                    .instanceLocation(path)
                    .message("must have exactly one of " + group.alternatives() + " but found: " + found)
                    .build());
        }
    }

    /**
     * Builds the "pick exactly one" option groups from the Camel catalog's EIP model metadata - the same metadata the
     * classic (non-canonical) schema's oneOf groups are generated from. Only object-typed options are considered;
     * array-typed options (e.g. "outputs") use "oneOf" to mean "each element is one of these types", not "exactly one
     * of these sibling keys must be present".
     */
    private static Map<String, OneOfGroup> loadOneOfGroups() {
        Map<String, OneOfGroup> groups = new HashMap<>();
        CamelCatalog catalog = new DefaultCamelCatalog();
        for (String name : catalog.findModelNames()) {
            EipModel model = catalog.eipModel(name);
            if (model == null) {
                continue;
            }
            for (EipModel.EipOptionModel option : model.getOptions()) {
                List<String> oneOfs = option.getOneOfs();
                if (oneOfs != null && !oneOfs.isEmpty() && "object".equals(option.getType())) {
                    groups.putIfAbsent(option.getName(), new OneOfGroup(new LinkedHashSet<>(oneOfs), option.isRequired()));
                }
            }
        }
        return groups;
    }

    private static Error parseError(Exception e) {
        String msg = e.getClass().getName() + ": " + e.getMessage();
        return Error.builder()
                .messageKey("parser")
                .format(new MessageFormat("{0}"))
                .arguments(msg)
                .build();
    }

    public void init() throws Exception {
        String location = canonical ? LOCATION_CANONICAL : LOCATION;
        var model = mapper.readTree(YamlValidator.class.getResourceAsStream(location));
        var version = getSpecificationVersion(model).orElse(SpecificationVersion.DRAFT_4);
        var config = SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();

        // Register "deprecated" as a known non-validation keyword to suppress warnings
        Dialect base = getBaseDialect(version);
        Dialect dialect = Dialect.builder(base)
                .keyword(new NonValidationKeyword("deprecated"))
                .build();

        var schemaRegistry = SchemaRegistry.withDefaultDialect(dialect,
                builder -> builder.schemaRegistryConfig(config));

        // Use a proper URI for the schema location to ensure $ref resolution works
        var schemaLocation = SchemaLocation.of(location);
        schema = schemaRegistry.getSchema(schemaLocation, model);

        if (canonical) {
            oneOfGroups = loadOneOfGroups();
        }
    }

    private static Dialect getBaseDialect(SpecificationVersion version) {
        return switch (version) {
            case DRAFT_4 -> Dialects.getDraft4();
            case DRAFT_6 -> Dialects.getDraft6();
            case DRAFT_7 -> Dialects.getDraft7();
            case DRAFT_2019_09 -> Dialects.getDraft201909();
            case DRAFT_2020_12 -> Dialects.getDraft202012();
        };
    }

    private static Optional<SpecificationVersion> getSpecificationVersion(JsonNode schemaNode) {
        var schemaField = schemaNode.get("$schema");
        if (schemaField != null && schemaField.isTextual()) {
            return SpecificationVersion.fromDialectId(schemaField.asText());
        }
        return Optional.empty();
    }

}
