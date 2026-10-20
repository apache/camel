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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.parser.models.RefFormat;
import io.swagger.v3.parser.util.RefUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks the references ({@code $ref}) of an OpenAPI specification against the {@code externalReferences} option before
 * the parser resolves them. The parser loads every external reference it meets: from the working directory and the
 * classpath, or over http(s) when the reference is a URL or the specification itself comes from a URL.
 * <p>
 * This is a copy of the class of the same name in camel-rest-openapi, which neither module can depend on the other for;
 * keep the two in sync.
 */
final class OpenApiExternalReferences {

    static final String ALL = "all";
    static final String LOCAL = "local";
    static final String NONE = "none";

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiExternalReferences.class);

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
     * @throws IllegalArgumentException for the first reference the option does not allow
     */
    static void check(String externalReferences, String specification, String specificationUri, boolean remote) {
        String policy = policy(externalReferences);
        if (ALL.equals(policy)) {
            return;
        }
        checkDocument(policy, specification, specificationUri, null, remote, new HashSet<>());
    }

    private static void checkDocument(
            String policy, String content, String specificationUri, String document, boolean remote,
            Set<String> checked) {
        DocumentCheck check = new DocumentCheck(policy, specificationUri, document, remote);
        check.scan(readTree(content, specificationUri, document), "");
        // a local document can reference further documents, which the parser loads as well
        for (String file : check.localDocuments) {
            if (checked.add(file)) {
                String text = readLocalDocument(file);
                if (text != null) {
                    checkDocument(policy, text, specificationUri, file, false, checked);
                }
            }
        }
    }

    private static JsonNode readTree(String content, String specificationUri, String document) {
        try {
            return (content.trim().startsWith("{") ? Json.mapper() : Yaml.mapper()).readTree(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Cannot read the OpenAPI specification " + specificationUri
                                               + (document != null ? " (" + document + ")" : "")
                                               + " to check its references: " + e.getMessage(),
                    e);
        }
    }

    private static String readLocalDocument(String file) {
        try {
            // how the parser reads a relative reference of a specification that has no location: from the working
            // directory, then from the classpath
            return RefUtils.readExternalRef(file, RefFormat.RELATIVE, null, new File(".").toPath());
        } catch (RuntimeException e) {
            LOG.debug("Cannot read {} to check its references, the parser reports it when it resolves the specification: {}",
                    file, e.getMessage());
            return null;
        }
    }

    private static boolean isHttp(String file) {
        String lower = file.toLowerCase(Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:");
    }

    /**
     * The check of one document.
     */
    private static final class DocumentCheck {

        private final String policy;
        private final String specificationUri;
        private final String document;
        private final boolean remote;
        private final List<String> localDocuments = new ArrayList<>();

        DocumentCheck(String policy, String specificationUri, String document, boolean remote) {
            this.policy = policy;
            this.specificationUri = specificationUri;
            this.document = document;
            this.remote = remote;
        }

        void scan(JsonNode node, String path) {
            if (node.isObject()) {
                for (Map.Entry<String, JsonNode> field : node.properties()) {
                    String fieldPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                    if ("$ref".equals(field.getKey()) && field.getValue().isTextual()) {
                        reference(field.getValue().asText(), fieldPath);
                    } else {
                        scan(field.getValue(), fieldPath);
                    }
                }
            } else if (node.isArray()) {
                int i = 0;
                for (JsonNode item : node) {
                    scan(item, path + "[" + i++ + "]");
                }
            }
        }

        private void reference(String ref, String path) {
            if (ref.isEmpty() || ref.startsWith("#")) {
                // inside the document
                return;
            }
            int fragment = ref.indexOf('#');
            String file = fragment < 0 ? ref : ref.substring(0, fragment);
            String reason = null;
            if (NONE.equals(policy)) {
                reason = "externalReferences=none allows only references inside the document";
            } else if (remote) {
                reason = "externalReferences=local allows no external reference in a specification loaded from a URL,"
                         + " since the parser resolves it against that URL";
            } else if (isHttp(file)) {
                reason = "externalReferences=local allows no http(s) reference";
            }
            if (reason != null) {
                throw new IllegalArgumentException(
                        "The OpenAPI specification " + specificationUri + " references " + ref + " at " + path
                                                   + (document != null ? " in " + document : "") + ", but " + reason);
            }
            localDocuments.add(file);
        }
    }
}
