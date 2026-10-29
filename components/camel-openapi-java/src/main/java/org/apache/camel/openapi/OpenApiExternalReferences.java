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
package org.apache.camel.openapi;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.parser.models.RefFormat;
import io.swagger.v3.parser.processors.ExternalRefProcessor;
import io.swagger.v3.parser.reference.Reference;
import io.swagger.v3.parser.reference.ReferenceUtils;
import io.swagger.v3.parser.reference.ReferenceVisitor;
import io.swagger.v3.parser.util.ClasspathHelper;
import io.swagger.v3.parser.util.DeserializationUtils;
import io.swagger.v3.parser.util.RefUtils;

/**
 * Checks the references ({@code $ref}) of an OpenAPI specification against the {@code externalReferences} option before
 * the parser resolves them. The parser loads every external reference it meets: from the working directory and the
 * classpath, or over http(s) when the reference is a URL or the specification itself comes from a URL.
 * <p>
 * The check works on the tree the parser resolves (YAML aliases expanded), resolves each reference the way the parser
 * does, that is against the document that contains it and, for OpenAPI 3.1, against the enclosing {@code $id}s, which
 * the parser also carries into the documents it reaches through a reference, and reads the local documents it allows to
 * check their references in turn. For OpenAPI 3.1 it checks, like the parser, only the part of a document a reference
 * selects (its JSON pointer or {@code $anchor}), follows the references inside that part, and ends a path where the
 * parser ends it: at a reference equal to one it is already resolving. A document it cannot read, a fragment it cannot
 * find, and a reference the parser could resolve in another document than the one the check took fail the check.
 * <p>
 * This is a copy of the class of the same name in camel-rest-openapi, which neither module can depend on the other for;
 * keep the two in sync.
 */
final class OpenApiExternalReferences {

    static final String ALL = "all";
    static final String LOCAL = "local";
    static final String NONE = "none";

    /**
     * The URI the parser gives an OpenAPI 3.1 specification that has no location (OpenAPIDereferencer31).
     */
    private static final String LOCAL_URI = "local";

    private OpenApiExternalReferences() {
    }

    /**
     * The value of the option to apply: {@link #ALL} when none is set.
     *
     * @throws IllegalArgumentException for a value other than all, local or none
     */
    static String policy(String externalReferences) {
        if (externalReferences == null || externalReferences.isBlank()) {
            return ALL;
        }
        String policy = externalReferences.trim().toLowerCase(Locale.ROOT);
        if (ALL.equals(policy) || LOCAL.equals(policy) || NONE.equals(policy)) {
            return policy;
        }
        throw new IllegalArgumentException(
                "Unknown externalReferences value: " + externalReferences + ", expected all, local or none");
    }

    /**
     * Checks every {@code $ref} of the specification, and of the local documents it references, against the option.
     *
     * @param  externalReferences       the option: all, local or none
     * @param  specification            the specification, as JSON or YAML text
     * @param  specificationUri         where the specification comes from, for the error message
     * @param  remote                   whether the specification comes from a URL: the parser then resolves its
     *                                  external references against that URL
     * @throws IllegalArgumentException for the first reference the option does not allow, for a referenced document or
     *                                  fragment that cannot be read or found, and for a reference the parser could
     *                                  resolve in another document than the one the check took
     */
    static void check(String externalReferences, String specification, String specificationUri, boolean remote) {
        String policy = policy(externalReferences);
        if (ALL.equals(policy)) {
            return;
        }
        new Check(policy, specificationUri, remote).root(specification);
    }

    private static boolean isHttp(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:");
    }

    /**
     * The OpenAPI 3.1 {@code $id}s that decide what a list of them resolves against: from the last absolute one on,
     * since an absolute {@code $id} gives the same base whatever is above it.
     */
    private static List<String> significant(List<String> ids) {
        for (int i = ids.size() - 1; i >= 0; i--) {
            try {
                if (URI.create(ReferenceUtils.toBaseURI(ids.get(i))).isAbsolute()) {
                    return ids.subList(i, ids.size());
                }
            } catch (Exception e) {
                // not a URI: kept, the check then tells it apart
            }
        }
        return ids;
    }

    /**
     * The schema with the given {@code $anchor}, searched as ReferenceVisitor.findAnchor does.
     */
    private static JsonNode findAnchor(JsonNode node, String anchor) {
        if (node.isObject()) {
            JsonNode anchorNode = node.get("$anchor");
            if (anchorNode != null && anchorNode.isValueNode() && anchor.equals(anchorNode.asText())) {
                return node;
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                JsonNode found = findAnchor(child, anchor);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * A part of a document a reference leads to: the document, for OpenAPI 3.1 the fragment that selects the part,
     * where the reference is, the OpenAPI 3.1 {@code $id}s the parser carries into the part, which decide what the
     * references inside it resolve to, and the object with the {@code $ref}.
     */
    private static final class Target {

        private final String location;
        private final String fragment;
        private final String ref;
        private final String origin;
        private final List<String> inheritedIds;
        private final JsonNode holder;

        Target(String location, String fragment, String ref, String origin, List<String> inheritedIds, JsonNode holder) {
            this.location = location;
            this.fragment = fragment;
            this.ref = ref;
            this.origin = origin;
            this.inheritedIds = inheritedIds;
            this.holder = holder;
        }
    }

    /**
     * OpenAPI 3.1: a URI the parser looks up to find the document a reference leads to, with the document the check
     * took for it (null for the specification) and the URI of that document.
     */
    private static final class Lookup {

        private final String uri;
        private final String document;
        private final String documentUri;
        private final String origin;

        Lookup(String uri, String document, String documentUri, String origin) {
            this.uri = uri;
            this.document = document;
            this.documentUri = documentUri;
            this.origin = origin;
        }
    }

    /**
     * The check of one specification: the root document and the documents reached from it.
     */
    private static final class Check {

        private final String policy;
        private final String specificationUri;
        private final boolean remote;
        // the documents read, by location: the parser reads each of them once too
        private final Map<String, JsonNode> documents = new HashMap<>();
        // the parts checked, with what decides how their references resolve
        private final Set<String> checked = new HashSet<>();
        // OpenAPI 3.1: the objects with the $ref that lead to the part being checked, from the root
        private final Deque<JsonNode> resolving = new ArrayDeque<>();
        // OpenAPI 3.1: what the parser can find under a URI, by document: the document read from there, and the
        // documents with a schema whose $id resolves to it
        private final Map<String, Map<String, String>> registered = new HashMap<>();
        // OpenAPI 3.1: the URIs the parser looks up
        private final List<Lookup> lookups = new ArrayList<>();
        private boolean openApi31;

        Check(String policy, String specificationUri, boolean remote) {
            this.policy = policy;
            this.specificationUri = specificationUri;
            this.remote = remote;
        }

        void root(String specification) {
            JsonNode tree = tree(specification, null, null);
            // the parser resolves an OpenAPI 3.1 document with its own dereferencer, which has other rules
            openApi31 = tree.path("openapi").asText("").startsWith("3.1");
            if (openApi31) {
                // a reference inside the specification leads to a part of this tree, like one inside a referenced document
                documents.put(LOCAL_URI, tree);
            }
            follow(tree, null, null, openApi31 ? LOCAL_URI : null, List.of());
            if (openApi31) {
                lookUp();
            }
        }

        /**
         * Checks the references of one part of a document and then the parts they lead to.
         *
         * @param node         the part: the whole document, or for OpenAPI 3.1 the node a reference selects
         * @param document     the resolved location of the document, as the parser uses it, or null for the root
         * @param name         the document and the fragment of the part, for the error message, or null for the root
         * @param base         what the references of the part resolve against
         * @param inheritedIds the {@code $id}s of the schemas enclosing the reference that led here (OpenAPI 3.1)
         */
        private void follow(JsonNode node, String document, String name, String base, List<String> inheritedIds) {
            // the references of this part, with where they are, in the order they are met
            List<Target> targets = new ArrayList<>();
            scan(node, "", document, name, base, inheritedIds, targets);
            for (Target target : targets) {
                // OpenAPI31Traverser compares the schemas it visits by value, so it stops at a reference equal to one it
                // is already resolving, and loads nothing further on this path, whatever base that reference has now
                if (openApi31 && isResolving(target.holder)) {
                    continue;
                }
                if (!checked.add(key(target))) {
                    continue;
                }
                JsonNode part = select(load(target), target);
                String targetBase = target.location;
                if (openApi31) {
                    // OpenAPI31Traverser.traverseSchema: the inherited $ids resolved, in turn, against the document
                    for (String id : target.inheritedIds) {
                        targetBase = resolveBase(id, targetBase, "", target.location);
                    }
                }
                resolving.push(target.holder);
                try {
                    // the specification is the document null, as in the scan of the whole of it
                    follow(part, isRoot(target) ? null : target.location, name(target), targetBase, target.inheritedIds);
                } finally {
                    resolving.pop();
                }
            }
        }

        private boolean isRoot(Target target) {
            return openApi31 && LOCAL_URI.equals(target.location);
        }

        private boolean isResolving(JsonNode holder) {
            for (JsonNode reference : resolving) {
                if (reference.equals(holder)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * What decides the references of a part: the document, and for OpenAPI 3.1 the fragment and the inherited
         * {@code $id}s from the last absolute one on.
         */
        private String key(Target target) {
            if (!openApi31) {
                // the whole document, which has no $id
                return target.location;
            }
            return target.location + "#" + Objects.toString(target.fragment, "") + " under "
                   + significant(target.inheritedIds);
        }

        private String name(Target target) {
            return openApi31 && target.fragment != null && !target.fragment.isBlank()
                    ? target.location + "#" + target.fragment : target.location;
        }

        /**
         * A referenced document, read once. OpenAPI 3.1 dereferencing reads a document into its own tree
         * (ReferenceVisitor.deserializeIntoTree), and keeps it under its location.
         */
        private JsonNode load(Target target) {
            JsonNode tree = documents.get(target.location);
            if (tree == null) {
                String content = read(target.location, target.origin);
                if (openApi31) {
                    try {
                        tree = new ReferenceVisitor(new Reference(), null, null, null).deserializeIntoTree(content);
                    } catch (Exception e) {
                        throw new IllegalArgumentException(
                                where(target.location, target.origin) + " cannot be read to check its references: "
                                                           + e.getMessage(),
                                e);
                    }
                    register(target.location, target.location, "the location of a referenced document");
                } else {
                    tree = tree(content, target.location, target.origin);
                }
                documents.put(target.location, tree);
            }
            return tree;
        }

        /**
         * The part of a document a reference selects: for OpenAPI 3.1 the node of the JSON pointer of the fragment, or
         * else the schema whose {@code $anchor} the fragment is (ReferenceVisitor.resolveSchemaRef), and for OpenAPI
         * 3.0 the whole document, whose references the parser resolves relative to it.
         */
        private JsonNode select(JsonNode tree, Target target) {
            if (!openApi31 || target.fragment == null || target.fragment.isBlank()) {
                return tree;
            }
            try {
                return ReferenceUtils.jsonPointerEvaluate(target.fragment, tree, target.ref);
            } catch (RuntimeException e) {
                JsonNode anchor = findAnchor(tree, target.fragment);
                if (anchor == null) {
                    throw new IllegalArgumentException(
                            target.origin + ", but #" + target.fragment + " cannot be found in " + target.location
                                                       + " to check its references",
                            e);
                }
                return anchor;
            }
        }

        private JsonNode tree(String content, String document, String origin) {
            try {
                // the tree the parser resolves: the same YAML reader, with the aliases expanded and its limits applied
                return DeserializationUtils.deserializeIntoTree(content, document != null ? document : specificationUri);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        (document != null ? where(document, origin) : "The OpenAPI specification " + specificationUri)
                                                   + " cannot be read to check its references: " + e.getMessage(),
                        e);
            }
        }

        private void scan(
                JsonNode node, String path, String document, String name, String base, List<String> ids,
                List<Target> targets) {
            if (node.isObject()) {
                String nodeBase = base;
                List<String> nodeIds = ids;
                if (openApi31) {
                    JsonNode id = node.get("$id");
                    if (id != null && id.isTextual() && !id.asText().isBlank()) {
                        nodeBase = resolveBase(id.asText(), base, path, name);
                        nodeIds = new ArrayList<>(ids);
                        nodeIds.add(id.asText());
                        // OpenAPI31Traverser.traverseSchema keeps the document under the base of the $id too
                        register(nodeBase, document,
                                "the $id of a schema in " + (name != null ? name : "the specification"));
                    }
                }
                for (Map.Entry<String, JsonNode> field : node.properties()) {
                    String fieldPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                    if ("$ref".equals(field.getKey()) && field.getValue().isTextual()) {
                        reference(field.getValue().asText(), node, fieldPath, document, name, nodeBase, nodeIds, targets);
                    } else {
                        scan(field.getValue(), fieldPath, document, name, nodeBase, nodeIds, targets);
                    }
                }
            } else if (node.isArray()) {
                int i = 0;
                for (JsonNode item : node) {
                    scan(item, path + "[" + i++ + "]", document, name, base, ids, targets);
                }
            }
        }

        /**
         * The base the references below an OpenAPI 3.1 {@code $id} resolve against: the id resolved against the
         * enclosing base, as OpenAPI31Traverser.traverseSchema does.
         */
        private String resolveBase(String id, String base, String path, String document) {
            try {
                return ReferenceUtils.toBaseURI(ReferenceUtils.resolve(ReferenceUtils.toBaseURI(id), base));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        where(id, path, document) + " cannot be resolved as $id: " + e.getMessage(), e);
            }
        }

        private void register(String uri, String document, String description) {
            registered.computeIfAbsent(uri, k -> new LinkedHashMap<>()).putIfAbsent(document, description);
        }

        private void reference(
                String ref, JsonNode holder, String path, String document, String name, String base, List<String> ids,
                List<Target> targets) {
            if (ref.isEmpty()) {
                return;
            }
            if (ref.startsWith("#")) {
                if (openApi31) {
                    inside(ref, holder, path, document, name, base, ids, targets);
                }
                // OpenAPI 3.0: the parser evaluates the fragment in the document, which the check reads as a whole
                return;
            }
            String target;
            String fragment = null;
            try {
                if (openApi31) {
                    // ReferenceVisitor.resolveSchemaRef: the reference resolved against the enclosing $ids and the
                    // document, without its fragment, which then selects the part of that document
                    target = ReferenceUtils.toBaseURI(ReferenceUtils.resolve(ref, base));
                    fragment = ReferenceUtils.getFragment(ref);
                } else {
                    // ExternalRefProcessor: a reference met in a referenced document is joined with the path of that
                    // document; the parser then reads the part before the fragment
                    String joined = document == null ? ref : ExternalRefProcessor.join(document, ref);
                    int hash = joined.indexOf("#/");
                    target = hash < 0 ? joined : joined.substring(0, hash);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        where(ref, path, name) + " cannot be resolved: " + e.getMessage(), e);
            }
            String reason = null;
            if (NONE.equals(policy)) {
                reason = "externalReferences=none allows only references inside the document";
            } else if (remote) {
                reason = "externalReferences=local allows no external reference in a specification loaded from a URL,"
                         + " since the parser resolves it against that URL";
            } else if (isHttp(target)) {
                reason = "externalReferences=local allows no http(s) reference"
                         + (target.equals(ref) ? "" : " (" + ref + " resolves to " + target + ")");
            }
            String origin = where(ref, path, name);
            if (reason != null) {
                throw new IllegalArgumentException(origin + ", but " + reason);
            }
            if (openApi31) {
                lookups.add(new Lookup(target, target, target, origin));
            }
            // a local document: file: and classpath: URIs, and paths the parser reads from the working directory or
            // the classpath; the parser carries the enclosing $ids into it (ReferenceVisitor.resolveSchemaRef)
            targets.add(new Target(target, fragment, ref, origin, ids, holder));
        }

        /**
         * An OpenAPI 3.1 reference inside the document. The parser resolves it against the {@code $id}s in effect and
         * looks the result up: the document itself when no {@code $id} moves the base, else the document of the schema
         * whose {@code $id} it is, which loads nothing. The check reads the specification as a whole, but the parser
         * deserializes only the part the reference selects, under the {@code $id}s enclosing the reference, so the
         * check follows the reference like any other, in the specification or in a referenced document.
         */
        private void inside(
                String ref, JsonNode holder, String path, String document, String name, String base, List<String> ids,
                List<Target> targets) {
            String uri;
            String fragment;
            try {
                uri = ReferenceUtils.toBaseURI(ReferenceUtils.resolve(ref, base));
                fragment = ReferenceUtils.getFragment(ref);
            } catch (Exception e) {
                // the parser cannot resolve it either, so it loads nothing for it
                return;
            }
            String origin = where(ref, path, name);
            lookups.add(new Lookup(uri, document, document != null ? document : LOCAL_URI, origin));
            // always add the target, also for an in-spec #-reference (document is null, so LOCAL_URI stands for the
            // specification root); a document != null guard here used to drop those references unchecked
            // the parser takes only the part the reference selects, under the $ids of the schemas enclosing the reference
            targets.add(new Target(document != null ? document : LOCAL_URI, fragment, ref, origin, ids, holder));
        }

        /**
         * OpenAPI 3.1: the parser takes the document it keeps first under a URI it looks up, the document it read from
         * there or the document of a schema whose {@code $id} resolves to it, whichever it met first. A reference
         * passes when that can be no other document than the one the check took.
         */
        private void lookUp() {
            for (Lookup lookup : lookups) {
                Map<String, String> found = registered.get(lookup.uri);
                if (found != null) {
                    for (Map.Entry<String, String> entry : found.entrySet()) {
                        if (!Objects.equals(entry.getKey(), lookup.document)) {
                            throw new IllegalArgumentException(
                                    lookup.origin + ", but " + lookup.uri + " is also " + entry.getValue()
                                                               + ", where the parser can resolve the reference instead");
                        }
                    }
                }
                if (!lookup.uri.equals(lookup.documentUri) && (found == null || !found.containsKey(lookup.document))) {
                    throw new IllegalArgumentException(
                            lookup.origin + ", but the $ids in effect resolve it against " + lookup.uri
                                                       + ", which is not the $id of a schema in the same document");
                }
            }
        }

        /**
         * Reads a referenced local document the way the parser does: a {@code file:} or {@code classpath:} URI by its
         * path (OpenAPI 3.1, Visitor.readURI), anything else from the working directory and then the classpath
         * (RefUtils.readExternalRef).
         */
        private String read(String target, String origin) {
            try {
                String scheme = null;
                String schemePath = null;
                try {
                    URI uri = new URI(target);
                    scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
                    schemePath = uri.getPath();
                } catch (URISyntaxException e) {
                    // not a URI, so a path
                }
                if ("file".equals(scheme)) {
                    return Files.readString(Paths.get(schemePath), StandardCharsets.UTF_8);
                } else if ("classpath".equals(scheme)) {
                    return ClasspathHelper.loadFileFromClasspath(schemePath);
                } else {
                    return RefUtils.readExternalRef(target, RefFormat.RELATIVE, null, new File(".").toPath(), null);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        where(target, origin) + " cannot be read to check its references: " + e.getMessage(), e);
            }
        }

        private String where(String ref, String path, String document) {
            return "The OpenAPI specification " + specificationUri + " references " + ref + " at " + path
                   + (document != null ? " in " + document : "");
        }

        private String where(String document, String origin) {
            return origin != null
                    ? origin + ", which" : "The OpenAPI specification " + specificationUri + " references "
                                           + document + ", which";
        }
    }
}
