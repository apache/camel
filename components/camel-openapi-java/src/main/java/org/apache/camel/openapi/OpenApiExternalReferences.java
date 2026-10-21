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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.parser.models.RefFormat;
import io.swagger.v3.parser.processors.ExternalRefProcessor;
import io.swagger.v3.parser.reference.ReferenceUtils;
import io.swagger.v3.parser.util.ClasspathHelper;
import io.swagger.v3.parser.util.DeserializationUtils;
import io.swagger.v3.parser.util.RefUtils;

/**
 * Checks the references ({@code $ref}) of an OpenAPI specification against the {@code externalReferences} option before
 * the parser resolves them. The parser loads every external reference it meets: from the working directory and the
 * classpath, or over http(s) when the reference is a URL or the specification itself comes from a URL.
 * <p>
 * The check works on the tree the parser resolves (YAML aliases expanded), resolves each reference the way the parser
 * does, that is against the document that contains it and, for OpenAPI 3.1, against the enclosing {@code $id}s, and
 * reads the local documents it allows to check their references in turn. A document it cannot read fails the check.
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
     * @throws IllegalArgumentException for the first reference the option does not allow, and for a referenced document
     *                                  that cannot be read
     */
    static void check(String externalReferences, String specification, String specificationUri, boolean remote) {
        String policy = policy(externalReferences);
        if (ALL.equals(policy)) {
            return;
        }
        new Check(policy, specificationUri, remote).document(specification, null, null);
    }

    private static boolean isHttp(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:");
    }

    /**
     * The check of one specification: the root document and the documents reached from it.
     */
    private static final class Check {

        private final String policy;
        private final String specificationUri;
        private final boolean remote;
        private final Set<String> checked = new HashSet<>();
        private boolean openApi31;

        Check(String policy, String specificationUri, boolean remote) {
            this.policy = policy;
            this.specificationUri = specificationUri;
            this.remote = remote;
        }

        /**
         * Checks one document.
         *
         * @param content  the document
         * @param document the resolved location of the document, as the parser uses it, or null for the root
         * @param origin   where the root references this document, for the error message, or null for the root
         */
        void document(String content, String document, String origin) {
            JsonNode tree;
            try {
                // the tree the parser resolves: the same YAML reader, with the aliases expanded and its limits applied
                tree = DeserializationUtils.deserializeIntoTree(content, document != null ? document : specificationUri);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        (document != null ? where(document, origin) : "The OpenAPI specification " + specificationUri)
                                                   + " cannot be read to check its references: " + e.getMessage(),
                        e);
            }
            if (document == null) {
                // the parser resolves an OpenAPI 3.1 document with its own dereferencer, which has other rules
                openApi31 = tree.path("openapi").asText("").startsWith("3.1");
            }
            // the external references of this document, with where they are, in the order they are met
            Map<String, String> targets = new LinkedHashMap<>();
            String base = openApi31 && document == null ? LOCAL_URI : document;
            scan(tree, "", document, base, targets);
            for (Map.Entry<String, String> target : targets.entrySet()) {
                if (checked.add(target.getKey())) {
                    // a referenced local document can reference further documents, which the parser loads as well
                    document(read(target.getKey(), target.getValue()), target.getKey(), target.getValue());
                }
            }
        }

        private void scan(JsonNode node, String path, String document, String base, Map<String, String> targets) {
            if (node.isObject()) {
                String nodeBase = base;
                if (openApi31) {
                    JsonNode id = node.get("$id");
                    if (id != null && id.isTextual() && !id.asText().isBlank()) {
                        nodeBase = resolveBase(id.asText(), base, path, document);
                    }
                }
                for (Map.Entry<String, JsonNode> field : node.properties()) {
                    String fieldPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                    if ("$ref".equals(field.getKey()) && field.getValue().isTextual()) {
                        reference(field.getValue().asText(), fieldPath, document, nodeBase, targets);
                    } else {
                        scan(field.getValue(), fieldPath, document, nodeBase, targets);
                    }
                }
            } else if (node.isArray()) {
                int i = 0;
                for (JsonNode item : node) {
                    scan(item, path + "[" + i++ + "]", document, base, targets);
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

        private void reference(String ref, String path, String document, String base, Map<String, String> targets) {
            if (ref.isEmpty() || ref.startsWith("#")) {
                // inside the document: the parser evaluates the fragment in the document, or in the schema whose $id
                // is the base, without loading anything
                return;
            }
            String target;
            try {
                if (openApi31) {
                    // ReferenceVisitor.resolveSchemaRef: the reference resolved against the enclosing $ids and the
                    // document, without its fragment
                    target = ReferenceUtils.toBaseURI(ReferenceUtils.resolve(ref, base));
                } else {
                    // ExternalRefProcessor: a reference met in a referenced document is joined with the path of that
                    // document; the parser then reads the part before the fragment
                    String joined = document == null ? ref : ExternalRefProcessor.join(document, ref);
                    int fragment = joined.indexOf("#/");
                    target = fragment < 0 ? joined : joined.substring(0, fragment);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        where(ref, path, document) + " cannot be resolved: " + e.getMessage(), e);
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
            if (reason != null) {
                throw new IllegalArgumentException(where(ref, path, document) + ", but " + reason);
            }
            // a local document: file: and classpath: URIs, and paths the parser reads from the working directory or
            // the classpath
            targets.putIfAbsent(target, where(ref, path, document));
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
