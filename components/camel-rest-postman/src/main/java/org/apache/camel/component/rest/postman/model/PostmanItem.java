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
package org.apache.camel.component.rest.postman.model;

import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;

/**
 * A leaf item of a Postman collection, that is one request, together with everything it inherited from the folders
 * enclosing it.
 * <p>
 * Inheritance is resolved eagerly while the item tree is flattened, so an item carries its own effective auth block and
 * its own variable scope. That keeps lookups at routing time to a field read.
 */
public final class PostmanItem {

    private final JsonObject json;
    private final String id;
    private final String name;
    private final List<String> folderPath;
    private final String slug;
    private final String qualifiedSlug;
    private final String canonicalId;
    private final PostmanRequest request;
    private final PostmanAuth effectiveAuth;
    private final Map<String, String> scopeVariables;

    public PostmanItem(JsonObject json, String id, String name, List<String> folderPath, String slug,
                       String qualifiedSlug, String canonicalId, PostmanRequest request, PostmanAuth effectiveAuth,
                       Map<String, String> scopeVariables) {
        this.json = json;
        this.id = id;
        this.name = name;
        this.folderPath = List.copyOf(folderPath);
        this.slug = slug;
        this.qualifiedSlug = qualifiedSlug;
        this.canonicalId = canonicalId;
        this.request = request;
        this.effectiveAuth = effectiveAuth;
        this.scopeVariables = Map.copyOf(scopeVariables);
    }

    /**
     * The {@code item.id} recorded in the collection.
     * <p>
     * This is optional in the v2.1 schema, and Postman's exporter strips auto-generated ids, so it is normally present
     * only on collections fetched from the Postman cloud.
     *
     * @return the id, or {@code null} when the collection does not record one
     */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * The names of the enclosing folders, outermost first.
     */
    public List<String> getFolderPath() {
        return folderPath;
    }

    /**
     * The slugified item name, for example {@code getUserById}.
     */
    public String getSlug() {
        return slug;
    }

    /**
     * The slug prefixed with the slugified folder path, for example {@code users/getUserById}. Used to disambiguate
     * items whose names collide.
     */
    public String getQualifiedSlug() {
        return qualifiedSlug;
    }

    /**
     * The identifier a route author uses for this request: the plain slug when it is unique across the collection,
     * otherwise the folder qualified slug. This is both the URI fragment that selects the request and the
     * {@code direct:} name the contract-first consumer dispatches to, so that there is only ever one spelling.
     */
    public String getCanonicalId() {
        return canonicalId;
    }

    public PostmanRequest getRequest() {
        return request;
    }

    /**
     * The auth block that applies to this request: its own, or failing that the nearest enclosing folder's, or failing
     * that the collection's.
     *
     * @return the auth block, or {@code null} when none applies
     */
    public PostmanAuth getEffectiveAuth() {
        return effectiveAuth;
    }

    /**
     * The variables visible to this request, with the innermost scope winning.
     */
    public Map<String, String> getScopeVariables() {
        return scopeVariables;
    }

    /**
     * The saved example responses recorded against this item, used to serve mock responses.
     */
    public List<PostmanResponse> getSavedResponses() {
        return PostmanResponse.listFrom(json.get("response"));
    }

    public JsonObject getJson() {
        return json;
    }

    /**
     * A human readable description of this item for use in error messages, naming the folder path when there is one.
     */
    public String describe() {
        if (folderPath.isEmpty()) {
            return "'" + name + "'";
        }
        return "'" + String.join(" / ", folderPath) + " / " + name + "'";
    }

    @Override
    public String toString() {
        return "PostmanItem[" + canonicalId + "]";
    }
}
